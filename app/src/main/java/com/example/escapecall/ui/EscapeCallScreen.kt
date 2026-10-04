package com.example.escapecall.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import com.example.escapecall.data.AlertDelivery
import com.example.escapecall.data.CallerPersona
import com.example.escapecall.data.EmergencyContact
import com.example.escapecall.data.EscapeSettings
import java.text.DateFormat
import java.util.Date

@Composable
fun EscapeCallApp(
    uiState: EscapeCallUiState,
    permissionSnapshot: PermissionSnapshot,
    onRequestPermission: (PermissionKind) -> Unit,
    onDraftChange: ((EscapeSettings) -> EscapeSettings) -> Unit,
    onSaveSettings: () -> Unit,
    onSetDemoMode: (Boolean) -> Unit,
    onTriggerNotification: () -> Unit,
    onRunSmokeTest: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSetup: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onClearHistory: () -> Unit,
) {
    when (uiState.screen) {
        EscapeCallScreen.Setup -> SetupScreen(
            uiState = uiState,
            permissionSnapshot = permissionSnapshot,
            onRequestPermission = onRequestPermission,
            onDraftChange = onDraftChange,
            onSaveSettings = onSaveSettings,
            onSetDemoMode = onSetDemoMode,
            onTriggerNotification = onTriggerNotification,
            onRunSmokeTest = onRunSmokeTest,
            onShowHistory = onShowHistory,
        )
        EscapeCallScreen.IncomingCall -> IncomingCallScreen(
            persona = uiState.settings.persona,
            onAccept = onAccept,
            onDecline = onDecline,
        )
        EscapeCallScreen.ActiveCall -> ActiveCallScreen(
            uiState = uiState,
            onEnd = onEnd,
        )
        EscapeCallScreen.History -> HistoryScreen(
            uiState = uiState,
            onBack = onShowSetup,
            onClearHistory = onClearHistory,
        )
    }
}

@Composable
private fun SetupScreen(
    uiState: EscapeCallUiState,
    permissionSnapshot: PermissionSnapshot,
    onRequestPermission: (PermissionKind) -> Unit,
    onDraftChange: ((EscapeSettings) -> EscapeSettings) -> Unit,
    onSaveSettings: () -> Unit,
    onSetDemoMode: (Boolean) -> Unit,
    onTriggerNotification: () -> Unit,
    onRunSmokeTest: () -> Unit,
    onShowHistory: () -> Unit,
) {
    var pendingPermission by remember { mutableStateOf<PermissionKind?>(null) }

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "EscapeCall",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Local setup",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onShowHistory) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                }
            }

            item {
                PersonaForm(uiState.draftSettings, onDraftChange)
            }

            item {
                EmergencyForm(uiState.draftSettings, onDraftChange)
            }

            item {
                BackendForm(uiState.draftSettings, onDraftChange)
            }

            item {
                PermissionChecklist(
                    permissionSnapshot = permissionSnapshot,
                    onRequest = { pendingPermission = it },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Demo mode", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Shorter connection pacing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.draftSettings.demoMode,
                        onCheckedChange = { enabled ->
                            onDraftChange { it.copy(demoMode = enabled) }
                            onSetDemoMode(enabled)
                        },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onSaveSettings,
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                    ElevatedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onTriggerNotification,
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ring")
                    }
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isJoining,
                    onClick = onRunSmokeTest,
                ) {
                    if (uiState.isJoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("RTC smoke test")
                }
            }

            uiState.statusMessage?.let { message ->
                item {
                    StatusCard(
                        message = message,
                        isError = uiState.backendHealthy == false,
                    )
                }
            }
        }
    }

    pendingPermission?.let { kind ->
        PermissionRationaleDialog(
            kind = kind,
            onDismiss = { pendingPermission = null },
            onContinue = {
                pendingPermission = null
                onRequestPermission(kind)
            },
        )
    }
}

@Composable
private fun PersonaForm(
    draft: EscapeSettings,
    onDraftChange: ((EscapeSettings) -> EscapeSettings) -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Caller persona", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.persona.name,
                onValueChange = { value ->
                    onDraftChange { it.copy(persona = it.persona.copy(name = value)) }
                },
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.persona.relationship,
                onValueChange = { value ->
                    onDraftChange { it.copy(persona = it.persona.copy(relationship = value)) }
                },
                label = { Text("Relationship") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.codeword,
                onValueChange = { value ->
                    onDraftChange { it.copy(codeword = value) }
                },
                label = { Text("Safety phrase") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun EmergencyForm(
    draft: EscapeSettings,
    onDraftChange: ((EscapeSettings) -> EscapeSettings) -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Emergency contact", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.emergencyContact.name,
                onValueChange = { value ->
                    onDraftChange {
                        it.copy(
                            emergencyContact = it.emergencyContact.copy(name = value),
                        )
                    }
                },
                label = { Text("Name") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.emergencyContact.phoneNumber,
                onValueChange = { value ->
                    onDraftChange {
                        it.copy(
                            emergencyContact = it.emergencyContact.copy(phoneNumber = value),
                        )
                    }
                },
                label = { Text("Phone number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                leadingIcon = { Icon(Icons.Filled.Sms, contentDescription = null) },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun BackendForm(
    draft: EscapeSettings,
    onDraftChange: ((EscapeSettings) -> EscapeSettings) -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backend", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.backendUrl,
                onValueChange = { value ->
                    onDraftChange { it.copy(backendUrl = value) }
                },
                label = { Text("HTTPS URL") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun PermissionChecklist(
    permissionSnapshot: PermissionSnapshot,
    onRequest: (PermissionKind) -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Permissions", fontWeight = FontWeight.SemiBold)
            PermissionRow(
                icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                title = "Microphone",
                granted = permissionSnapshot.microphoneGranted,
                onRequest = { onRequest(PermissionKind.Microphone) },
            )
            PermissionRow(
                icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                title = "Location",
                granted = permissionSnapshot.locationGranted,
                onRequest = { onRequest(PermissionKind.Location) },
            )
            PermissionRow(
                icon = { Icon(Icons.Filled.Sms, contentDescription = null) },
                title = "SMS",
                granted = permissionSnapshot.smsGranted,
                onRequest = { onRequest(PermissionKind.Sms) },
            )
            PermissionRow(
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                title = "Notifications",
                granted = permissionSnapshot.notificationsGranted,
                onRequest = { onRequest(PermissionKind.Notifications) },
            )
            PermissionRow(
                icon = { Icon(Icons.Filled.Call, contentDescription = null) },
                title = "Full-screen call",
                granted = permissionSnapshot.fullScreenIntentGranted,
                onRequest = { onRequest(PermissionKind.FullScreenIntent) },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: @Composable () -> Unit,
    title: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(modifier = Modifier.weight(1f), text = title)
        if (granted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = Color(0xFF168246),
            )
        } else {
            OutlinedButton(onClick = onRequest) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun PermissionRationaleDialog(
    kind: PermissionKind,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val title = when (kind) {
        PermissionKind.Microphone -> "Microphone"
        PermissionKind.Location -> "Location"
        PermissionKind.Sms -> "SMS"
        PermissionKind.Notifications -> "Notifications"
        PermissionKind.FullScreenIntent -> "Full-screen call"
    }
    val body = when (kind) {
        PermissionKind.Microphone -> "The call needs live audio so the agent can respond naturally."
        PermissionKind.Location -> "The alert message can include your current location."
        PermissionKind.Sms -> "The alert is sent directly from this device to your saved contact."
        PermissionKind.Notifications -> "The incoming-call screen is launched from a high-priority call notification."
        PermissionKind.FullScreenIntent -> "Android may require enabling full-screen call alerts in system settings."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun IncomingCallScreen(
    persona: CallerPersona,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111315))
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = "Incoming call",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(64.dp))
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(7.dp),
                contentAlignment = Alignment.Center,
            ) {
                AvatarInitial(persona.displayName, size = 120)
            }
            Spacer(Modifier.height(44.dp))
            Text(
                text = persona.displayName,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = persona.relationship,
                color = Color.White.copy(alpha = 0.66f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Mobile",
                color = Color.White.copy(alpha = 0.42f),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallActionButton(
                    color = Color(0xFFE53935),
                    icon = { Icon(Icons.Filled.CallEnd, contentDescription = "Decline") },
                    label = "Decline",
                    onClick = onDecline,
                )
                CallActionButton(
                    color = Color(0xFF16A34A),
                    icon = { Icon(Icons.Filled.Call, contentDescription = "Accept") },
                    label = "Accept",
                    onClick = onAccept,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActiveCallScreen(
    uiState: EscapeCallUiState,
    onEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050506))
            .padding(horizontal = 28.dp, vertical = 44.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            AvatarInitial(uiState.settings.persona.displayName, size = 104)
            Spacer(Modifier.height(20.dp))
            Text(
                text = uiState.settings.persona.displayName,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = formatDuration(uiState.callDurationSeconds),
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = when {
                            uiState.isJoining -> "Connecting"
                            uiState.isAgentSpeaking -> "Connected"
                            else -> uiState.statusMessage ?: "Connected"
                        },
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            FilledIconButton(
                modifier = Modifier.size(76.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White,
                ),
                onClick = onEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "End call",
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    uiState: EscapeCallUiState,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Call history",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onClearHistory) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear history")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (uiState.history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No calls yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.history, key = { it.id }) { entry ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(entry.personaName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            entry.relationship,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.SHORT,
                                            DateFormat.SHORT,
                                        ).format(Date(entry.startedAtMillis)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Divider()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(formatDuration(entry.durationSeconds)) },
                                    )
                                    AssistChip(
                                        onClick = {},
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (entry.codewordTriggered) {
                                                    Icons.Filled.Warning
                                                } else {
                                                    Icons.Filled.CheckCircle
                                                },
                                                contentDescription = null,
                                            )
                                        },
                                        label = {
                                            Text(
                                                if (entry.codewordTriggered) {
                                                    entry.alertDelivery.label()
                                                } else {
                                                    "No alert"
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean) {
    val colors = if (isError) {
        CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5))
    } else {
        CardDefaults.cardColors(containerColor = Color(0xFFEAF7EE))
    }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = colors,
    ) {
        Text(
            modifier = Modifier.padding(14.dp),
            text = message,
            color = if (isError) Color(0xFF8A1F1F) else Color(0xFF0D5A2B),
        )
    }
}

@Composable
private fun AvatarInitial(name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFF2D333B)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            color = Color.White,
            fontSize = (size / 2.4).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CallActionButton(
    color: Color,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            modifier = Modifier.size(76.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = color,
                contentColor = Color.White,
            ),
            onClick = onClick,
        ) {
            icon()
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun AlertDelivery.label(): String {
    return when (this) {
        AlertDelivery.NotTriggered -> "No alert"
        AlertDelivery.SmsSent -> "SMS sent"
        AlertDelivery.NotificationFallback -> "Fallback"
        AlertDelivery.Failed -> "Failed"
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
