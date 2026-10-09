package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NearbyDevice
import com.example.transfer.NetworkUtils
import com.example.transfer.QrCodeGenerator
import com.example.ui.MainViewModel
import com.example.ui.components.FastShareTopAppBar
import com.example.ui.theme.FastShareBlue

@Composable
fun ScanQrCodeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onConnectedToReceiver: () -> Unit
) {
    val context = LocalContext.current
    val deviceName by viewModel.deviceName.collectAsState()
    val localIp = remember { NetworkUtils.getLocalIpAddress(context) }
    var qrSeed by remember { mutableStateOf(System.currentTimeMillis()) }

    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf(localIp) }

    // Start receiver mode so this device listens for incoming files when displaying QR
    DisposableEffect(Unit) {
        viewModel.startReceiverMode()
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    val serverState by viewModel.serverSessionState.collectAsState()
    if (serverState.isTransferring) {
        onConnectedToReceiver()
    }

    val qrContent = "fastshare://pair?ip=$localIp&port=8888&name=$deviceName&seed=$qrSeed"
    val qrBitmap: Bitmap? = remember(qrContent) {
        QrCodeGenerator.generateQrCodeBitmap(qrContent, width = 500, height = 500)
    }

    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            title = { Text("Connect by IP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter receiver IP address and port:")
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        label = { Text("IP Address (e.g. 192.168.1.5)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualIpDialog = false
                        val cleanIp = manualIpInput.trim()
                        if (cleanIp.isNotEmpty()) {
                            val manualDevice = NearbyDevice(
                                id = "$cleanIp:8888",
                                name = "Direct IP Device",
                                ipAddress = cleanIp,
                                port = 8888,
                                isReceiver = true
                            )
                            viewModel.connectAndSend(manualDevice)
                            onConnectedToReceiver()
                        }
                    }
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            FastShareTopAppBar(
                title = "Scan QR Code",
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Flashlight",
                            tint = FastShareBlue
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .testTag("scan_qr_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // QR Viewfinder Bracket Frame (Storyboard Screen 10)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(
                        width = 3.dp,
                        color = FastShareBlue,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "FastShare Pairing QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Ask the sender to scan this QR code to send files to this device.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$deviceName ($localIp:8888)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FastShareBlue
                )
            }

            // Bottom Actions (Storyboard Screen 10)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Connect to my FastShare device: $qrContent")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Connection Link"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("share_qr_button"),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FastShareBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share QR Code",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { qrSeed = System.currentTimeMillis() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("new_qr_button"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New QR", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showManualIpDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("manual_ip_button"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Direct IP", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
