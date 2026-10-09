package com.example.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NetworkUtils {

    fun getLocalIpAddress(context: Context): String {
        try {
            // Check NetworkInterfaces for non-loopback IPv4 address (Wi-Fi, hotspot, wlan0, ap0, etc.)
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val candidateIps = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        // Prioritize wlan0, ap0, softap, eth
                        val name = networkInterface.name.lowercase(Locale.ROOT)
                        if (name.contains("wlan") || name.contains("ap") || name.contains("softap")) {
                            return hostAddress
                        }
                        candidateIps.add(hostAddress)
                    }
                }
            }

            if (candidateIps.isNotEmpty()) {
                return candidateIps.first()
            }

            // Fallback via WifiManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    fun isConnectedToWifiOrHotspot(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun getDefaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups.coerceIn(0, units.size - 1)]}"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0.0 MB/s"
        val mbPerSec = bytesPerSec.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB/s", mbPerSec)
    }

    fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return if (diff < 60 * 1000) {
            "Just now"
        } else if (diff < 24 * 3600 * 1000) {
            val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            "Today, " + timeSdf.format(Date(timestamp))
        } else if (diff < 48 * 3600 * 1000) {
            val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            "Yesterday, " + timeSdf.format(Date(timestamp))
        } else {
            sdf.format(Date(timestamp))
        }
    }
}
