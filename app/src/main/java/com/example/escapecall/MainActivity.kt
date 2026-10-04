package com.example.escapecall

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.escapecall.ui.EscapeCallApp
import com.example.escapecall.ui.EscapeCallViewModel
import com.example.escapecall.ui.PermissionKind
import com.example.escapecall.ui.PermissionSnapshot
import com.example.escapecall.ui.theme.EscapeCallTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EscapeCallViewModel by viewModels()
    private var permissionSnapshot by mutableStateOf(emptyPermissionSnapshot())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionSnapshot = currentPermissionSnapshot()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionSnapshot = currentPermissionSnapshot()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        permissionSnapshot = currentPermissionSnapshot()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            EscapeCallTheme {
                EscapeCallApp(
                    uiState = uiState,
                    permissionSnapshot = permissionSnapshot,
                    onRequestPermission = ::requestPermission,
                    onDraftChange = viewModel::updateDraft,
                    onSaveSettings = viewModel::saveDraftSettings,
                    onSetDemoMode = viewModel::setDemoMode,
                    onTriggerNotification = viewModel::triggerIncomingCallNotification,
                    onRunSmokeTest = viewModel::runConnectivitySmokeTest,
                    onShowHistory = viewModel::showHistory,
                    onShowSetup = viewModel::showSetup,
                    onAccept = viewModel::acceptCall,
                    onDecline = viewModel::declineCall,
                    onEnd = viewModel::endCall,
                    onClearHistory = viewModel::clearHistory,
                )
            }
        }
        viewModel.handleAction(intent?.action)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleAction(intent.action)
    }

    override fun onResume() {
        super.onResume()
        permissionSnapshot = currentPermissionSnapshot()
    }

    private fun requestPermission(kind: PermissionKind) {
        when (kind) {
            PermissionKind.Microphone -> permissionLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO),
            )
            PermissionKind.Location -> permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            )
            PermissionKind.Sms -> permissionLauncher.launch(
                arrayOf(Manifest.permission.SEND_SMS),
            )
            PermissionKind.Notifications -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    permissionSnapshot = currentPermissionSnapshot()
                }
            }
            PermissionKind.FullScreenIntent -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    settingsLauncher.launch(intent)
                } else {
                    permissionSnapshot = currentPermissionSnapshot()
                }
            }
        }
    }

    private fun currentPermissionSnapshot(): PermissionSnapshot {
        return PermissionSnapshot(
            microphoneGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
            locationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
            smsGranted = hasPermission(Manifest.permission.SEND_SMS),
            notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            },
            fullScreenIntentGranted = canUseFullScreenIntent(),
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun canUseFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }
    }
}

private fun emptyPermissionSnapshot(): PermissionSnapshot {
    return PermissionSnapshot(
        microphoneGranted = false,
        locationGranted = false,
        smsGranted = false,
        notificationsGranted = false,
        fullScreenIntentGranted = false,
    )
}
