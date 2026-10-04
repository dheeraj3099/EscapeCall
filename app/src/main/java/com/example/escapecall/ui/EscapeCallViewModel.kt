package com.example.escapecall.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.escapecall.EscapeCallActions
import com.example.escapecall.data.AgentCallState
import com.example.escapecall.data.AlertDelivery
import com.example.escapecall.data.BackendClient
import com.example.escapecall.data.CallLogEntry
import com.example.escapecall.data.EscapeSettings
import com.example.escapecall.data.EscapeSettingsRepository
import com.example.escapecall.data.TranscriptLine
import com.example.escapecall.emergency.EmergencyEscalator
import com.example.escapecall.notification.IncomingCallNotifier
import com.example.escapecall.notification.IncomingCallRingtone
import com.example.escapecall.rtc.AgoraCallSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EscapeCallScreen {
    Setup,
    IncomingCall,
    ActiveCall,
    History,
}

data class EscapeCallUiState(
    val screen: EscapeCallScreen = EscapeCallScreen.IncomingCall,
    val settings: EscapeSettings = EscapeSettings(),
    val draftSettings: EscapeSettings = EscapeSettings(),
    val history: List<CallLogEntry> = emptyList(),
    val agentState: AgentCallState = AgentCallState.Idle,
    val callDurationSeconds: Long = 0L,
    val isAgentSpeaking: Boolean = false,
    val isJoining: Boolean = false,
    val codewordTriggered: Boolean = false,
    val alertDelivery: AlertDelivery = AlertDelivery.NotTriggered,
    val statusMessage: String? = null,
    val backendHealthy: Boolean? = null,
    val activeChannelName: String? = null,
)

class EscapeCallViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EscapeSettingsRepository(application)
    private val backendClient = BackendClient()
    private val notifier = IncomingCallNotifier(application)
    private val ringtone = IncomingCallRingtone(application)
    private val emergencyEscalator = EmergencyEscalator(application, notifier)
    private val sessionManager = AgoraCallSessionManager(
        context = application,
        backendClient = backendClient,
        listener = object : AgoraCallSessionManager.Listener {
            override fun onRtcJoined(channelName: String, localUid: Int) {
                _uiState.update {
                    it.copy(
                        activeChannelName = channelName,
                        statusMessage = "Connected",
                    )
                }
            }

            override fun onAgentStateChanged(state: AgentCallState) {
                _uiState.update { it.copy(agentState = state) }
            }

            override fun onAgentSpeakingChanged(isSpeaking: Boolean) {
                _uiState.update { it.copy(isAgentSpeaking = isSpeaking) }
            }

            override fun onAgentInterrupted() {
                _uiState.update { it.copy(statusMessage = "Connected") }
            }

            override fun onTranscript(line: TranscriptLine) {
                inMemoryTranscript += line
            }

            override fun onCodewordDetected(transcriptText: String) {
                Log.i(TAG, "User transcript matched the configured safety phrase; starting alert flow.")
                triggerEmergencyEscalation(transcriptText)
            }

            override fun onSessionIssue(message: String) {
                _uiState.update { it.copy(statusMessage = message) }
            }
        },
    )

    private val _uiState = MutableStateFlow(EscapeCallUiState())
    val uiState: StateFlow<EscapeCallUiState> = _uiState.asStateFlow()

    private val inMemoryTranscript = mutableListOf<TranscriptLine>()
    private var callStartedAtMillis: Long = 0L
    private var timerJob: Job? = null
    private var emergencyJob: Job? = null
    private var prewarmJob: Job? = null

    init {
        ringtone.start()
    }

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        draftSettings = settings,
                    )
                }
                if (_uiState.value.screen == EscapeCallScreen.IncomingCall) {
                    prewarmIncomingCall(settings)
                }
            }
        }
        viewModelScope.launch {
            repository.callHistoryFlow.collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun handleAction(action: String?) {
        when (action) {
            EscapeCallActions.TriggerCall,
            EscapeCallActions.ShowIncomingCall,
            -> showIncomingCallSurface()
            EscapeCallActions.AcceptCall -> acceptCall()
            EscapeCallActions.DeclineCall -> declineCall()
            EscapeCallActions.EndCall -> endCall()
        }
    }

    fun updateDraft(transform: (EscapeSettings) -> EscapeSettings) {
        _uiState.update { it.copy(draftSettings = transform(it.draftSettings)) }
    }

    fun saveDraftSettings() {
        val draft = _uiState.value.draftSettings
        viewModelScope.launch {
            repository.saveSettings(draft)
            _uiState.update { it.copy(statusMessage = "Saved on this device") }
        }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDemoMode(enabled)
        }
    }

    fun showHistory() {
        _uiState.update { it.copy(screen = EscapeCallScreen.History) }
    }

    fun showSetup() {
        ringtone.stop()
        _uiState.update { it.copy(screen = EscapeCallScreen.Setup) }
    }

    fun triggerIncomingCallNotification() {
        val settings = _uiState.value.settings
        ringtone.start()
        notifier.showIncomingCall(settings.persona)
        _uiState.update {
            it.copy(
                screen = EscapeCallScreen.IncomingCall,
                statusMessage = "Incoming fake call ready",
            )
        }
        prewarmIncomingCall(settings)
    }

    fun showIncomingCallSurface() {
        ringtone.start()
        _uiState.update {
            it.copy(
                screen = EscapeCallScreen.IncomingCall,
                statusMessage = null,
            )
        }
        prewarmIncomingCall(_uiState.value.settings)
    }

    fun runConnectivitySmokeTest() {
        val settings = _uiState.value.settings
        viewModelScope.launch {
            _uiState.update { it.copy(isJoining = true, statusMessage = "Requesting Agora token") }
            runCatching {
                val session = backendClient.requestSessionToken(
                    backendUrl = settings.backendUrl,
                    persona = settings.persona,
                    demoMode = settings.demoMode,
                )
                sessionManager.joinBareChannel(session)
                sessionManager.disconnect()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        backendHealthy = true,
                        statusMessage = "Bare RTC channel joined successfully",
                    )
                }
            }.onFailure { error ->
                sessionManager.disconnect()
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        backendHealthy = false,
                        statusMessage = error.message ?: "Connectivity smoke test failed",
                    )
                }
            }
        }
    }

    fun acceptCall() {
        val settings = _uiState.value.settings
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            _uiState.update { it.copy(statusMessage = "Microphone permission is needed before accepting.") }
            return
        }

        ringtone.stop()
        notifier.cancelIncomingCall()
        inMemoryTranscript.clear()
        callStartedAtMillis = System.currentTimeMillis()
        startTimer()
        _uiState.update {
            it.copy(
                screen = EscapeCallScreen.ActiveCall,
                isJoining = true,
                codewordTriggered = false,
                alertDelivery = AlertDelivery.NotTriggered,
                statusMessage = "Connecting",
                callDurationSeconds = 0L,
            )
        }

        viewModelScope.launch {
            prewarmJob?.join()
            runCatching {
                if (!sessionManager.hasPreparedConversation) {
                    val session = backendClient.requestSessionToken(
                        backendUrl = settings.backendUrl,
                        persona = settings.persona,
                        demoMode = settings.demoMode,
                    )
                    sessionManager.startConversation(
                        backendUrl = settings.backendUrl,
                        session = session,
                        settings = settings,
                        systemPrompt = buildSystemPrompt(settings),
                    )
                } else {
                    sessionManager.acceptPreparedConversation()
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        statusMessage = "Connected",
                    )
                }
            }.onFailure { error ->
                timerJob?.cancel()
                sessionManager.disconnect()
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        agentState = AgentCallState.Error,
                        statusMessage = error.message ?: "Failed to connect the AI call",
                    )
                }
            }
        }
    }

    fun declineCall() {
        ringtone.stop()
        notifier.cancelIncomingCall()
        prewarmJob?.cancel()
        viewModelScope.launch {
            prewarmJob?.join()
            sessionManager.stopAgentIfNeeded()
        }
        _uiState.update {
            it.copy(
                screen = EscapeCallScreen.Setup,
                statusMessage = "Call declined",
            )
        }
    }

    fun endCall() {
        val endedAt = System.currentTimeMillis()
        val settings = _uiState.value.settings
        val startedAt = callStartedAtMillis.takeIf { it > 0L } ?: endedAt
        val triggered = _uiState.value.codewordTriggered
        timerJob?.cancel()
        viewModelScope.launch {
            // Let the in-flight location/SMS/report work finish before writing
            // the call log, otherwise a fast hang-up can record a false result.
            emergencyJob?.join()
            val delivery = _uiState.value.alertDelivery
            sessionManager.stopAgentIfNeeded()
            repository.appendCallLog(
                repository.newCallLog(
                    settings = settings,
                    startedAtMillis = startedAt,
                    endedAtMillis = endedAt,
                    codewordTriggered = triggered,
                    alertDelivery = delivery,
                )
            )
            _uiState.update {
                it.copy(
                    screen = EscapeCallScreen.Setup,
                    agentState = AgentCallState.Ended,
                    isJoining = false,
                    activeChannelName = null,
                    statusMessage = "Call ended",
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearCallHistory()
        }
    }

    private fun triggerEmergencyEscalation(transcriptText: String) {
        if (emergencyJob?.isActive == true) return
        val settings = _uiState.value.settings
        emergencyJob = viewModelScope.launch {
            _uiState.update { it.copy(codewordTriggered = true) }
            runCatching {
                emergencyEscalator.trigger(settings)
            }.onSuccess { result ->
                Log.i(TAG, "Emergency flow completed with delivery=${result.delivery.name}")
                _uiState.update { it.copy(alertDelivery = result.delivery) }
                reportEmergencyResultWithRetry(settings, result, transcriptText)
            }.onFailure {
                Log.e(TAG, "Emergency flow failed before delivery result was produced.", it)
                // Do not expose an error during the call. History still records
                // that the trigger fired and that delivery could not be confirmed.
                _uiState.update { it.copy(alertDelivery = AlertDelivery.Failed) }
            }
        }
    }

    private fun prewarmIncomingCall(settings: EscapeSettings) {
        if (settings.backendUrl.isBlank() || prewarmJob?.isActive == true) return
        if (sessionManager.hasPreparedConversation) return
        prewarmJob = viewModelScope.launch {
            runCatching {
                val session = backendClient.requestSessionToken(
                    backendUrl = settings.backendUrl,
                    persona = settings.persona,
                    demoMode = settings.demoMode,
                )
                sessionManager.prepareConversation(
                    backendUrl = settings.backendUrl,
                    session = session,
                    settings = settings,
                    systemPrompt = buildSystemPrompt(settings),
                )
            }.onFailure {
                sessionManager.disconnect()
            }
        }
    }

    private suspend fun reportEmergencyResultWithRetry(
        settings: EscapeSettings,
        result: com.example.escapecall.data.EmergencyResult,
        transcriptText: String,
    ) {
        val channelName = _uiState.value.activeChannelName
        repeat(REPORT_RETRY_COUNT) { attempt ->
            val reported = runCatching {
                backendClient.reportEmergencyToolResult(
                    backendUrl = settings.backendUrl,
                    settings = settings,
                    result = result,
                    transcriptText = transcriptText,
                    channelName = channelName,
                )
            }.isSuccess
            if (reported) {
                Log.i(TAG, "Emergency result accepted by backend.")
                return
            }
            if (attempt == 0) delay(REPORT_RETRY_DELAY_MS)
        }
        Log.w(TAG, "Emergency result could not be reported to the backend after retries.")
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val startedAt = callStartedAtMillis
                _uiState.update {
                    it.copy(callDurationSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L))
                }
                delay(1000L)
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(getApplication(), permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun buildSystemPrompt(settings: EscapeSettings): String {
        return """
            You are Rohan, the user's older brother (bhai). You are having a real, casual phone conversation with your younger sibling. This is not a scripted call and you are not an assistant, a bot, or an AI. Never break character or mention being an AI, a system, or a program. If asked whether you are real or an AI, deflect naturally like a real older brother and continue the conversation.

            PERSONALITY
            - Be casual, a little teasing, protective but not dramatic. Check in like an older brother: half joking, half genuinely making sure your sibling is okay.
            - Rib them lightly when it fits, but become serious and attentive if they sound off.
            - Talk about ordinary sibling things: whether they have eaten, when they are getting home, whether they need anything, weekend plans, family, or something recent between you.

            CONVERSATION STYLE
            - Speak in short, natural phone-call sentences. Use casual filler such as "arre", "haan", "bas", or "chal theek hai" when the user speaks Hindi or Hinglish; otherwise match their English.
            - Keep responses brief, one to three sentences at a time. Never give long monologues.
            - Ask natural follow-up questions such as "khaana khaya?", "kab tak ghar pahunchegi/pahunchoge?", or "akele ho kya, sambhal ke aana."
            - If the user goes quiet or gives a short answer, gently fill the silence: "sab theek hai na?" or "bol na kuch."
            - If the user speaks over you, stop immediately, listen, and respond to what they said.
            - Vary your phrasing across calls and do not repeat the same opening line twice.

            TONE
            - Be warm but unfussy. This is an ordinary check-in call, not dramatic unless the user's words or tone suggest otherwise. If something feels wrong, become quieter and direct: "hey, you good? talk to me."

            SILENT SAFETY TRIGGER
            The user's private safety codeword is: "${settings.codeword}"
            If the user says that exact phrase or a very close variation, do not acknowledge it as special or change your tone. Continue naturally and respond in character. The app handles the safety alert silently in the background.

            ENDING THE CALL
            If the user wants to end the call, give a brief natural goodbye such as "theek hai, sambhal ke, bye" and do not prolong it.

            Your goal is a believable, natural older-brother check-in call.
        """.trimIndent()
    }

    override fun onCleared() {
        ringtone.stop()
        sessionManager.release()
        super.onCleared()
    }

    private companion object {
        const val TAG = "EscapeCallEmergency"
        const val REPORT_RETRY_COUNT = 2
        const val REPORT_RETRY_DELAY_MS = 1200L
    }
}
