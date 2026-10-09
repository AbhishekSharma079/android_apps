package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.model.TransferStatus
import com.example.transfer.NetworkUtils
import com.example.ui.MainViewModel
import com.example.ui.components.FastShareTopAppBar
import com.example.ui.components.FileTypeIconBadge
import com.example.ui.theme.FastShareBlue
import com.example.ui.theme.FastShareGreen
import com.example.ui.theme.FastShareRed

@Composable
fun SendingFilesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onTransferComplete: () -> Unit
) {
    val clientState by viewModel.clientSessionState.collectAsState()
    val serverState by viewModel.serverSessionState.collectAsState()

    // Determine active session (client or server)
    val activeState = if (clientState.isTransferring || clientState.isCompleted) clientState else serverState

    val title = if (activeState.direction == TransferDirection.SENT) "Sending Files" else "Receiving Files"
    val progress = activeState.overallProgress
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    val percentInt = (progress * 100).toInt()

    LaunchedEffect(activeState.isCompleted) {
        if (activeState.isCompleted) {
            onTransferComplete()
        }
    }

    Scaffold(
        topBar = {
            FastShareTopAppBar(
                title = title,
                onBackClick = onBack
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelActiveTransfer()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("cancel_transfer_button"),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .testTag("sending_files_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Big Circular Progress Indicator (Storyboard Screen 6)
            item {
                Box(
                    modifier = Modifier
                        .size(170.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Background track
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = FastShareBlue.copy(alpha = 0.15f),
                        strokeWidth = 12.dp
                    )
                    // Active progress arc
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = FastShareBlue,
                        strokeWidth = 12.dp
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$percentInt%",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (activeState.speedBytesPerSec > 0) {
                            Text(
                                text = NetworkUtils.formatSpeed(activeState.speedBytesPerSec),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FastShareBlue
                            )
                        }
                    }
                }
            }

            // Status Subtitle
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (activeState.direction == TransferDirection.SENT) "Sending..." else "Receiving...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeState.totalFiles} files  •  ${NetworkUtils.formatFileSize(activeState.totalBytes)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // File items with individual progress
            items(activeState.items) { item ->
                TransferProgressRow(item = item)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TransferProgressRow(item: TransferItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transferring_file_${item.fileName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeIconBadge(
                mimeType = item.mimeType,
                fileName = item.fileName
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${NetworkUtils.formatFileSize(item.bytesTransferred)} / ${NetworkUtils.formatFileSize(item.fileSize)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (item.status == TransferStatus.TRANSFERRING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = FastShareBlue,
                        trackColor = FastShareBlue.copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            when (item.status) {
                TransferStatus.COMPLETED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = FastShareGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                TransferStatus.TRANSFERRING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = FastShareBlue
                    )
                }
                TransferStatus.FAILED -> {
                    Text("Failed", color = FastShareRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TransferStatus.CANCELLED -> {
                    Text("Cancelled", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                TransferStatus.PENDING -> {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }
    }
}
