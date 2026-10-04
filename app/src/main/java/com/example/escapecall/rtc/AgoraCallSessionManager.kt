package com.example.escapecall.rtc

import android.content.Context
import android.util.Log
import com.example.escapecall.data.AgentCallState
import com.example.escapecall.data.AgoraSessionConfig
import com.example.escapecall.data.BackendClient
import com.example.escapecall.data.EscapeSettings
import com.example.escapecall.data.TranscriptLine
import com.example.escapecall.data.TranscriptSpeaker
import com.example.escapecall.domain.CodewordMatcher
import io.agora.conversational.api.AgentManualEosEvent
import io.agora.conversational.api.AgentState
import io.agora.conversational.api.ConversationalAIAPIConfig
import io.agora.conversational.api.ConversationalAIAPIError
import io.agora.conversational.api.ConversationalAIAPIImpl
import io.agora.conversational.api.IConversationalAIAPI
import io.agora.conversational.api.IConversationalAIAPIEventHandler
import io.agora.conversational.api.InterruptEvent
import io.agora.conversational.api.MessageError
import io.agora.conversational.api.MessageReceipt
import io.agora.conversational.api.Metric
import io.agora.conversational.api.ModuleError
import io.agora.conversational.api.StateChangeEvent
import io.agora.conversational.api.Transcript
import io.agora.conversational.api.TranscriptRenderMode
import io.agora.conversational.api.Turn
import io.agora.conversational.api.UserManualEosEvent
import io.agora.conversational.api.UserManualSosEvent
import io.agora.conversational.api.VoiceprintStateChangeEvent
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtm.ErrorInfo
import io.agora.rtm.ResultCallback
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmConfig
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owns RTC/RTM and delegates transcript parsing to Agora's official client toolkit. */
class AgoraCallSessionManager(
    context: Context,
    private val backendClient: BackendClient,
    private val listener: Listener,
) : IConversationalAIAPIEventHandler {
    interface Listener {
        fun onRtcJoined(channelName: String, localUid: Int)
        fun onAgentStateChanged(state: AgentCallState)
        fun onAgentSpeakingChanged(isSpeaking: Boolean)
        fun onAgentInterrupted()
        fun onTranscript(line: TranscriptLine)
        fun onCodewordDetected(transcriptText: String)
        fun onSessionIssue(message: String)
    }

    private val appContext = context.applicationContext
    private var rtcEngine: RtcEngine? = null
    private var rtmClient: RtmClient? = null
    private var convoApi: IConversationalAIAPI? = null
    private var joinDeferred: CompletableDeferred<Int>? = null
    private var activeSession: AgoraSessionConfig? = null
    private var activeSettings: EscapeSettings? = null
    private var activeBackendUrl = ""
    private var activeAgentId: String? = null
    private var preparedForIncomingCall = false
    private var interruptInFlight = false

    val hasPreparedConversation: Boolean
        get() = preparedForIncomingCall && activeAgentId != null

    suspend fun joinBareChannel(session: AgoraSessionConfig) {
        disconnect()
        activeSession = session
        ensureRtcEngine(session.appId)
        joinRtcChannel(session, publishMicrophone = true)
    }

    suspend fun startConversation(
        backendUrl: String,
        session: AgoraSessionConfig,
        settings: EscapeSettings,
        systemPrompt: String,
    ): String {
        disconnect()
        activeSession = session
        activeSettings = settings
        activeBackendUrl = backendUrl
        updateState(AgentCallState.Joining)
        ensureRtcEngine(session.appId)
        createToolkit(session)
        joinRtcChannel(session, publishMicrophone = true)
        subscribeToolkit(session.channelName)

        // The official quickstart subscribes before asking the backend to start the agent.
        val agent = backendClient.startAgent(backendUrl, session, settings, systemPrompt)
        activeAgentId = agent.agentId
        return agent.agentId
    }

    suspend fun prepareConversation(
        backendUrl: String,
        session: AgoraSessionConfig,
        settings: EscapeSettings,
        systemPrompt: String,
    ): String {
        disconnect()
        activeSession = session
        activeSettings = settings
        activeBackendUrl = backendUrl
        updateState(AgentCallState.Joining)
        ensureRtcEngine(session.appId)
        createToolkit(session)
        joinRtcChannel(session, publishMicrophone = false)
        subscribeToolkit(session.channelName)
        withContext(Dispatchers.Main.immediate) {
            rtcEngine?.muteAllRemoteAudioStreams(true)
        }
        val agent = backendClient.startAgent(backendUrl, session, settings, systemPrompt)
        activeAgentId = agent.agentId
        preparedForIncomingCall = true
        return agent.agentId
    }

    suspend fun acceptPreparedConversation(): Boolean {
        if (!hasPreparedConversation) return false
        val engine = rtcEngine ?: return false
        withContext(Dispatchers.Main.immediate) {
            val result = engine.updateChannelMediaOptions(
                ChannelMediaOptions().apply {
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    publishMicrophoneTrack = true
                    autoSubscribeAudio = true
                },
            )
            if (result != Constants.ERR_OK) {
                throw IOException("Agora microphone publish failed (${RtcEngine.getErrorDescription(result)}).")
            }
            engine.muteAllRemoteAudioStreams(false)
        }
        preparedForIncomingCall = false
        return true
    }

    suspend fun stopAgentIfNeeded() {
        val session = activeSession
        val agentId = activeAgentId
        if (session != null && agentId != null && activeBackendUrl.isNotBlank()) {
            runCatching { backendClient.stopAgent(activeBackendUrl, agentId, session.channelName) }
                .onFailure { listener.onSessionIssue(it.message ?: "Failed to stop Agora agent.") }
        }
        disconnect()
    }

    fun disconnect() {
        joinDeferred?.cancel()
        joinDeferred = null
        val channel = activeSession?.channelName
        convoApi?.let { api ->
            if (!channel.isNullOrBlank()) api.unsubscribeMessage(channel) { }
            api.removeHandler(this)
            api.destroy()
        }
        convoApi = null
        rtmClient?.logout(noopRtmCallback())
        rtmClient = null
        rtcEngine?.leaveChannel()
        rtcEngine = null
        runCatching { RtcEngine.destroy() }
        activeSession = null
        activeSettings = null
        activeBackendUrl = ""
        activeAgentId = null
        preparedForIncomingCall = false
        interruptInFlight = false
        updateState(AgentCallState.Idle)
    }

    fun release() = disconnect()

    private suspend fun ensureRtcEngine(appId: String) = withContext(Dispatchers.Main.immediate) {
        if (rtcEngine != null) return@withContext
        val engine = RtcEngine.create(
            RtcEngineConfig().apply {
                mContext = appContext
                mAppId = appId
                mEventHandler = rtcEventHandler
                mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                mAudioScenario = Constants.AUDIO_SCENARIO_AI_CLIENT
            },
        ) ?: error("Agora RTC engine failed to initialize.")
        checkRtcResult("enableAudio", engine.enableAudio())
        rtcEngine = engine
    }

    private suspend fun createToolkit(session: AgoraSessionConfig) {
        val client = RtmClient.create(
            RtmConfig.Builder(session.appId, session.requesterRtmUserId)
                .useStringUserId(true)
                .build(),
        )
        val api = ConversationalAIAPIImpl(
            ConversationalAIAPIConfig(
                rtcEngine = rtcEngine ?: error("RTC engine is not initialized."),
                rtmClient = client,
                renderMode = TranscriptRenderMode.Text,
            ),
        )
        convoApi = api
        rtmClient = client
        api.addHandler(this)
        try {
            awaitRtmVoid { callback -> client.login(session.rtmToken, callback) }
            api.loadAudioSettings(Constants.AUDIO_SCENARIO_AI_CLIENT)
        } catch (error: Throwable) {
            api.removeHandler(this)
            api.destroy()
            client.logout(noopRtmCallback())
            convoApi = null
            rtmClient = null
            throw error
        }
    }

    private suspend fun subscribeToolkit(channelName: String) = suspendCancellableCoroutine<Unit> { continuation ->
        val api = convoApi ?: return@suspendCancellableCoroutine continuation.resumeWithException(
            IllegalStateException("Agora Conversational AI toolkit is not initialized."),
        )
        api.subscribeMessage(channelName) { error: ConversationalAIAPIError? ->
            if (error == null) continuation.resume(Unit) {}
            else continuation.resumeWithException(IOException("Agora transcript subscription failed: ${error.errorMessage}"))
        }
    }

    private suspend fun joinRtcChannel(session: AgoraSessionConfig, publishMicrophone: Boolean) {
        val engine = rtcEngine ?: error("RTC engine is not initialized.")
        val deferred = CompletableDeferred<Int>()
        joinDeferred = deferred
        val result = withContext(Dispatchers.Main.immediate) {
            engine.joinChannel(
                session.rtcToken,
                session.channelName,
                session.requesterRtcUid,
                ChannelMediaOptions().apply {
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    publishMicrophoneTrack = publishMicrophone
                    autoSubscribeAudio = true
                },
            )
        }
        if (result != Constants.ERR_OK) throw IOException("RTC join failed (${RtcEngine.getErrorDescription(result)}).")
        withTimeout(15_000) { deferred.await() }
        joinDeferred = null
    }

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            joinDeferred?.complete(uid)
            listener.onRtcJoined(channel.orEmpty(), uid)
        }

        override fun onError(err: Int) {
            listener.onSessionIssue("RTC error ${RtcEngine.getErrorDescription(err)}")
        }
    }

    override fun onAgentStateChanged(agentUserId: String, event: StateChangeEvent) {
        updateState(
            when (event.state) {
                AgentState.IDLE -> AgentCallState.Idle
                AgentState.SILENT -> AgentCallState.Silent
                AgentState.LISTENING -> AgentCallState.Listening
                AgentState.THINKING -> AgentCallState.Thinking
                AgentState.SPEAKING -> AgentCallState.Speaking
                AgentState.UNKNOWN -> AgentCallState.Error
            },
        )
    }

    override fun onAgentListeningChanged(agentUserId: String, isListening: Boolean) = Unit
    override fun onAgentThinkingChanged(agentUserId: String, isThinking: Boolean) {
        if (isThinking) updateState(AgentCallState.Thinking)
    }
    override fun onAgentSpeakingChanged(agentUserId: String, isSpeaking: Boolean) {
        listener.onAgentSpeakingChanged(isSpeaking)
        if (isSpeaking) updateState(AgentCallState.Speaking)
    }
    override fun onAgentInterrupted(agentUserId: String, event: InterruptEvent) {
        interruptInFlight = false
        listener.onAgentInterrupted()
    }

    override fun onTranscriptUpdated(agentUserId: String, transcript: Transcript) {
        val isUser = transcript.type.name.equals("USER", ignoreCase = true)
        listener.onTranscript(
            TranscriptLine(
                speaker = if (isUser) TranscriptSpeaker.User else TranscriptSpeaker.Agent,
                text = transcript.text,
                isFinal = transcript.status.name.equals("END", ignoreCase = true),
            ),
        )
        if (isUser && CodewordMatcher.matches(transcript.text, activeSettings?.codeword.orEmpty())) {
            listener.onCodewordDetected(transcript.text)
        }
        if (isUser && transcript.text.isNotBlank()) interruptAgentIfNeeded()
    }

    private fun interruptAgentIfNeeded() {
        if (interruptInFlight) return
        val channel = activeSession?.channelName ?: return
        interruptInFlight = true
        convoApi?.interrupt(channel) { error ->
            interruptInFlight = false
            if (error != null) listener.onSessionIssue("Agent interruption failed: ${error.errorMessage}")
        }
    }

    private fun updateState(state: AgentCallState) = listener.onAgentStateChanged(state)

    override fun onAgentMetrics(agentUserId: String, metric: Metric) = Unit
    override fun onTurnFinished(agentUserId: String, turn: Turn) = Unit
    override fun onAgentError(agentUserId: String, error: ModuleError) = listener.onSessionIssue(error.message)
    override fun onMessageError(agentUserId: String, error: MessageError) = listener.onSessionIssue(error.message)
    override fun onMessageReceiptUpdated(agentUserId: String, receipt: MessageReceipt) = Unit
    override fun onAgentVoiceprintStateChanged(agentUserId: String, event: VoiceprintStateChangeEvent) = Unit
    override fun onUserManualSosEvent(agentUserId: String, event: UserManualSosEvent) = Unit
    override fun onUserManualEosEvent(agentUserId: String, event: UserManualEosEvent) = Unit
    override fun onAgentManualEosEvent(agentUserId: String, event: AgentManualEosEvent) = Unit
    override fun onDebugLog(log: String) {
        Log.d("EscapeCallAgora", log)
    }

    private suspend fun awaitRtmVoid(register: (ResultCallback<Void>) -> Unit) =
        suspendCancellableCoroutine<Unit> { continuation ->
            register(object : ResultCallback<Void> {
                override fun onSuccess(responseInfo: Void?) = continuation.resume(Unit) {}
                override fun onFailure(errorInfo: ErrorInfo?) = continuation.resumeWithException(
                    IOException(errorInfo?.errorReason ?: "Agora RTM request failed."),
                )
            })
        }

    private fun noopRtmCallback() = object : ResultCallback<Void> {
        override fun onSuccess(responseInfo: Void?) = Unit
        override fun onFailure(errorInfo: ErrorInfo?) = Unit
    }

    private fun checkRtcResult(action: String, result: Int) {
        if (result != Constants.ERR_OK) throw IOException("Agora $action failed (${RtcEngine.getErrorDescription(result)}).")
    }
}
