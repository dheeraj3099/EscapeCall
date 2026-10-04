package com.example.escapecall.data

data class CallerPersona(
    val name: String = "Rohan",
    val relationship: String = "Older Brother",
) {
    val displayName: String
        get() = name.ifBlank { "Mom" }
}

data class EmergencyContact(
    val name: String = "",
    val phoneNumber: String = "",
)

data class EscapeSettings(
    val persona: CallerPersona = CallerPersona(),
    val codeword: String = "blue umbrella",
    val emergencyContact: EmergencyContact = EmergencyContact(),
    val demoMode: Boolean = false,
    val backendUrl: String = "",
) {
    val hasEmergencyContact: Boolean
        get() = emergencyContact.phoneNumber.isNotBlank()

    val hasCodeword: Boolean
        get() = codeword.isNotBlank()
}

enum class AlertDelivery {
    NotTriggered,
    SmsSent,
    NotificationFallback,
    Failed,
}

data class CallLogEntry(
    val id: String,
    val personaName: String,
    val relationship: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSeconds: Long,
    val codewordTriggered: Boolean,
    val alertDelivery: AlertDelivery,
)

data class TranscriptLine(
    val speaker: TranscriptSpeaker,
    val text: String,
    val isFinal: Boolean,
    val timestampMillis: Long = System.currentTimeMillis(),
)

enum class TranscriptSpeaker {
    User,
    Agent,
}

enum class AgentCallState {
    Idle,
    Joining,
    Listening,
    Thinking,
    Speaking,
    Silent,
    Ended,
    Error,
}

data class AgoraSessionConfig(
    val appId: String,
    val channelName: String,
    val rtcToken: String,
    val rtmToken: String,
    val requesterRtcUid: Int,
    val requesterRtmUserId: String,
    val agentRtcUid: Int,
    val expiresAtUnix: Long,
)

data class AgentStartResult(
    val agentId: String,
    val status: String,
    val createdAtUnix: Long,
)

data class EmergencyResult(
    val delivery: AlertDelivery,
    val message: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
