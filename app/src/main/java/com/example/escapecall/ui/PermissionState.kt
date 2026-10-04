package com.example.escapecall.ui

enum class PermissionKind {
    Microphone,
    Location,
    Sms,
    Notifications,
    FullScreenIntent,
}

data class PermissionSnapshot(
    val microphoneGranted: Boolean,
    val locationGranted: Boolean,
    val smsGranted: Boolean,
    val notificationsGranted: Boolean,
    val fullScreenIntentGranted: Boolean,
) {
    fun isGranted(kind: PermissionKind): Boolean {
        return when (kind) {
            PermissionKind.Microphone -> microphoneGranted
            PermissionKind.Location -> locationGranted
            PermissionKind.Sms -> smsGranted
            PermissionKind.Notifications -> notificationsGranted
            PermissionKind.FullScreenIntent -> fullScreenIntentGranted
        }
    }
}
