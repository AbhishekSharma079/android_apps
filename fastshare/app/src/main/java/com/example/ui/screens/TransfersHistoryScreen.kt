package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.components.FastShareBottomBar
import com.example.ui.components.FastShareTopAppBar
import com.example.ui.components.FileTypeIconBadge
import com.example.ui.theme.FastShareBlue
import com.example.ui.theme.FastShareGreen
import com.example.ui.theme.FastShareRed

@Composable
fun TransfersHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateReceived: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val context = LocalContext.current
    val allTransfers by viewModel.allTransfers.collectAsState()
    var filterDirection by remember { mutableStateOf<TransferDirection?>(null) }

    val filteredList = allTransfers.filter {
        filterDirection == null || it.direction == filterDirection
    }

    Scaffold(
        topBar = {
            FastShareTopAppBar(
                title = "Transfer History",
                onBackClick = onBack,
                actions = {
                    if (allTransfers.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearAllTransfers() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            FastShareBottomBar(
                currentRoute = "transfers",
                onNavigate = { route ->
                    when (route) {
                        "home" -> onNavigateHome()
                        "received" -> onNavigateReceived()
                        "settings" -> onNavigateSettings()
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
                .testTag("transfers_history_screen")
        ) {
            // Filter Pills: All, Sent, Received
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterDirection == null,
                    onClick = { filterDirection = null },
                    label = { Text("All (${allTransfers.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FastShareBlue,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                FilterChip(
                    selected = filterDirection == TransferDirection.SENT,
                    onClick = { filterDirection = TransferDirection.SENT },
                    label = { Text("Sent") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FastShareBlue,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                FilterChip(
                    selected = filterDirection == TransferDirection.RECEIVED,
                    onClick = { filterDirection = TransferDirection.RECEIVED },
                    label = { Text("Received") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FastShareBlue,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = FastShareBlue.copy(alpha = 0.5f),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No transfer history found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredList) { item ->
                        TransferHistoryRow(
                            item = item,
                            onClick = { viewModel.openFile(context, item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransferHistoryRow(
    item: TransferItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("history_item_${item.id}"),
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                val dir = if (item.direction == TransferDirection.SENT) "Sent to" else "Received from"
                Text(
                    text = "${NetworkUtils.formatFileSize(item.fileSize)} • $dir ${item.peerDeviceName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = NetworkUtils.formatTimestamp(item.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            if (item.status == TransferStatus.COMPLETED) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = FastShareGreen,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = FastShareRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
