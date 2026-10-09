package com.example.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.NearbyDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class DeviceDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DeviceDiscovery"
        private const val BROADCAST_PORT = 58742
        private const val NSD_SERVICE_TYPE = "_fastshare._tcp."
    }

    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    private var udpSocket: DatagramSocket? = null

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("FastShareMulticastLock")?.apply {
                setReferenceCounted(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create multicast lock", e)
        }
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    fun startDiscovery(
        isReceiver: Boolean,
        deviceName: String,
        serverPort: Int = 8888
    ) {
        _isSearching.value = true
        _discoveredDevices.value = emptyList()

        try {
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }

        // 1. Start UDP Listener
        startUdpListener(isReceiver)

        // 2. Start UDP Broadcaster
        startUdpBroadcaster(isReceiver, deviceName, serverPort)

        // 3. Start NSD Discovery & Registration
        startNsd(isReceiver, deviceName, serverPort)
    }

    fun stopDiscovery() {
        _isSearching.value = false
        broadcastJob?.cancel()
        broadcastJob = null
        listenJob?.cancel()
        listenJob = null

        try {
            udpSocket?.close()
            udpSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing UDP socket", e)
        }

        stopNsd()

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing multicast lock", e)
        }
    }

    private fun startUdpListener(isReceiver: Boolean) {
        listenJob?.cancel()
        listenJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(BROADCAST_PORT))
                }
                udpSocket = socket
                val buffer = ByteArray(2048)

                while (isActive && !socket.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        parseBeaconMessage(message, packet.address.hostAddress ?: "", isReceiver)
                    } catch (e: Exception) {
                        if (!isActive) break
                        Log.d(TAG, "Socket receive interrupted: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener error", e)
            }
        }
    }

    private fun startUdpBroadcaster(
        isReceiver: Boolean,
        deviceName: String,
        serverPort: Int
    ) {
        broadcastJob?.cancel()
        broadcastJob = scope.launch(Dispatchers.IO) {
            val myIp = NetworkUtils.getLocalIpAddress(context)
            val json = JSONObject().apply {
                put("app", "FASTSHARE")
                put("name", deviceName)
                put("role", if (isReceiver) "RECEIVER" else "SENDER")
                put("ip", myIp)
                put("port", serverPort)
            }.toString()

            val bytes = json.toByteArray()

            var broadcastSocket: DatagramSocket? = null
            try {
                broadcastSocket = DatagramSocket().apply {
                    broadcast = true
                }

                while (isActive) {
                    try {
                        // Broadcast to standard LAN broadcast address
                        val broadcastAddress = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(bytes, bytes.size, broadcastAddress, BROADCAST_PORT)
                        broadcastSocket.send(packet)
                    } catch (e: Exception) {
                        Log.d(TAG, "Broadcast send failed: ${e.message}")
                    }
                    delay(1200)
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP broadcaster setup error", e)
            } finally {
                broadcastSocket?.close()
            }
        }
    }

    private fun parseBeaconMessage(message: String, senderIp: String, currentDeviceIsReceiver: Boolean) {
        try {
            val json = JSONObject(message)
            if (json.optString("app") != "FASTSHARE") return

            val remoteRole = json.optString("role")
            val remoteName = json.optString("name", "Nearby Device")
            val remotePort = json.optInt("port", 8888)
            val reportedIp = json.optString("ip", senderIp)
            val ipToUse = if (reportedIp.isNotBlank() && reportedIp != "127.0.0.1") reportedIp else senderIp

            // Sender looks for Receivers, Receiver looks for Senders (or both)
            val shouldAdd = if (currentDeviceIsReceiver) {
                remoteRole == "SENDER" || remoteRole == "RECEIVER"
            } else {
                remoteRole == "RECEIVER" || remoteRole == "SENDER"
            }

            val myIp = NetworkUtils.getLocalIpAddress(context)
            // Skip itself if exactly matching own IP and port
            if (ipToUse == myIp && remoteRole == (if (currentDeviceIsReceiver) "RECEIVER" else "SENDER")) {
                return
            }

            if (shouldAdd) {
                addDiscoveredDevice(
                    NearbyDevice(
                        id = "$ipToUse:$remotePort",
                        name = remoteName,
                        ipAddress = ipToUse,
                        port = remotePort,
                        isReceiver = remoteRole == "RECEIVER"
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse beacon: $message")
        }
    }

    fun addDiscoveredDevice(device: NearbyDevice) {
        val currentList = _discoveredDevices.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == device.id || (it.ipAddress == device.ipAddress && it.port == device.port) }
        if (index >= 0) {
            currentList[index] = device.copy(discoveredAt = System.currentTimeMillis())
        } else {
            currentList.add(device)
        }
        _discoveredDevices.value = currentList
    }

    private fun startNsd(isReceiver: Boolean, deviceName: String, serverPort: Int) {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "FastShare-$deviceName"
                serviceType = NSD_SERVICE_TYPE
                port = serverPort
            }

            nsdRegistrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {
                    Log.d(TAG, "NSD service registered: ${info?.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "NSD registration failed: $errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo?) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)

            nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String?) {}
                override fun onDiscoveryStopped(serviceType: String?) {}

                override fun onServiceFound(info: NsdServiceInfo?) {
                    if (info?.serviceType?.contains("fastshare") == true) {
                        try {
                            nsdManager?.resolveService(info, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                                override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                    resolvedInfo?.let { res ->
                                        val host = res.host?.hostAddress ?: return@let
                                        val port = res.port
                                        val cleanName = res.serviceName.removePrefix("FastShare-")
                                        addDiscoveredDevice(
                                            NearbyDevice(
                                                id = "$host:$port",
                                                name = cleanName,
                                                ipAddress = host,
                                                port = port,
                                                isReceiver = isReceiver
                                            )
                                        )
                                    }
                                }
                            })
                        } catch (e: Exception) {
                            Log.e(TAG, "Resolve failed", e)
                        }
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            }

            nsdManager?.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD initialization error", e)
        }
    }

    private fun stopNsd() {
        try {
            nsdRegistrationListener?.let { nsdManager?.unregisterService(it) }
            nsdRegistrationListener = null
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering NSD service", e)
        }
        try {
            nsdDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            nsdDiscoveryListener = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping NSD discovery", e)
        }
    }
}
