package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.model.FileCategory
import com.example.ui.screens.ConnectingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceivedFilesScreen
import com.example.ui.screens.ScanQrCodeScreen
import com.example.ui.screens.SelectFilesScreen
import com.example.ui.screens.SendCategoriesScreen
import com.example.ui.screens.SendingFilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransferCompletedScreen
import com.example.ui.screens.TransfersHistoryScreen

@Composable
fun FastShareApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("splash") }

    // If server starts receiving while on another screen, jump to progress screen
    val serverState by viewModel.serverSessionState.collectAsState()
    if (serverState.isTransferring && currentScreen != "sending" && currentScreen != "transfer_completed") {
        currentScreen = "sending"
    }

    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
        when (screen) {
            "splash" -> {
                SplashScreen(
                    onContinue = { currentScreen = "home" }
                )
            }

            "home" -> {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToSend = { currentScreen = "send_categories" },
                    onNavigateToReceive = { currentScreen = "scan_qr" },
                    onNavigateToSettings = { currentScreen = "settings" },
                    onNavigateToReceived = { currentScreen = "received" },
                    onNavigateToTransfers = { currentScreen = "transfers" },
                    onFileClick = { item -> viewModel.openFile(context, item) }
                )
            }

            "send_categories" -> {
                BackHandler { currentScreen = "home" }
                SendCategoriesScreen(
                    onBack = { currentScreen = "home" },
                    onCategorySelected = { category ->
                        viewModel.setCategory(category)
                        currentScreen = "select_files"
                    },
                    onSelectAllFiles = {
                        viewModel.setCategory(FileCategory.PHOTOS)
                        currentScreen = "select_files"
                    }
                )
            }

            "select_files" -> {
                BackHandler { currentScreen = "send_categories" }
                SelectFilesScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = "send_categories" },
                    onProceedToSend = {
                        currentScreen = "connecting"
                    }
                )
            }

            "connecting" -> {
                BackHandler {
                    viewModel.stopDiscovery()
                    currentScreen = "select_files"
                }
                ConnectingScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.stopDiscovery()
                        currentScreen = "select_files"
                    },
                    onScanQrCode = { currentScreen = "scan_qr" },
                    onConnectedAndSending = { currentScreen = "sending" }
                )
            }

            "sending" -> {
                BackHandler {
                    viewModel.cancelActiveTransfer()
                    currentScreen = "home"
                }
                SendingFilesScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.cancelActiveTransfer()
                        currentScreen = "home"
                    },
                    onTransferComplete = {
                        currentScreen = "transfer_completed"
                    }
                )
            }

            "transfer_completed" -> {
                BackHandler {
                    viewModel.resetTransferState()
                    currentScreen = "home"
                }
                TransferCompletedScreen(
                    viewModel = viewModel,
                    onDone = {
                        viewModel.resetTransferState()
                        currentScreen = "home"
                    }
                )
            }

            "received" -> {
                BackHandler { currentScreen = "home" }
                ReceivedFilesScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = "home" },
                    onNavigateHome = { currentScreen = "home" },
                    onNavigateTransfers = { currentScreen = "transfers" },
                    onNavigateSettings = { currentScreen = "settings" }
                )
            }

            "transfers" -> {
                BackHandler { currentScreen = "home" }
                TransfersHistoryScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = "home" },
                    onNavigateHome = { currentScreen = "home" },
                    onNavigateReceived = { currentScreen = "received" },
                    onNavigateSettings = { currentScreen = "settings" }
                )
            }

            "settings" -> {
                BackHandler { currentScreen = "home" }
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = "home" },
                    onNavigateHome = { currentScreen = "home" },
                    onNavigateReceived = { currentScreen = "received" },
                    onNavigateTransfers = { currentScreen = "transfers" }
                )
            }

            "scan_qr" -> {
                BackHandler { currentScreen = "home" }
                ScanQrCodeScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = "home" },
                    onConnectedToReceiver = { currentScreen = "sending" }
                )
            }
        }
    }
}
