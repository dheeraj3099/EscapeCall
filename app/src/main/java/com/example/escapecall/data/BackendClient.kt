package com.example.escapecall.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BackendClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun health(backendUrl: String): Boolean {
        return runCatching {
            val body = getJson(backendUrl, "health")
            body.optString("status").equals("ok", ignoreCase = true)
        }.getOrDefault(false)
    }

    suspend fun requestSessionToken(
        backendUrl: String,
        persona: CallerPersona,
        demoMode: Boolean,
    ): AgoraSessionConfig {
        val body = postJson(
            backendUrl = backendUrl,
            path = "v1/agora/token",
            payload = JSONObject()
                .put("persona_name", persona.displayName)
                .put("relationship", persona.relationship)
                .put("demo_mode", demoMode),
        )
        return AgoraSessionConfig(
            appId = body.requireString("app_id"),
            channelName = body.requireString("channel_name"),
            rtcToken = body.requireString("rtc_token"),
            rtmToken = body.requireString("rtm_token"),
            requesterRtcUid = body.requireInt("requester_rtc_uid"),
            requesterRtmUserId = body.requireString("requester_rtm_user_id"),
            agentRtcUid = body.requireInt("agent_rtc_uid"),
            expiresAtUnix = body.optLong("expires_at_unix"),
        )
    }

    suspend fun startAgent(
        backendUrl: String,
        session: AgoraSessionConfig,
        settings: EscapeSettings,
        systemPrompt: String,
    ): AgentStartResult {
        val body = postJson(
            backendUrl = backendUrl,
            path = "v1/agora/agent/start",
            payload = JSONObject()
                .put("channel_name", session.channelName)
                .put("requester_rtc_uid", session.requesterRtcUid)
                .put("requester_rtm_user_id", session.requesterRtmUserId)
                .put("agent_rtc_uid", session.agentRtcUid)
                .put("persona_name", settings.persona.displayName)
                .put("relationship", settings.persona.relationship)
                .put("system_prompt", systemPrompt)
                .put("demo_mode", settings.demoMode),
        )
        return AgentStartResult(
            agentId = body.requireString("agent_id"),
            status = body.optString("status", "started"),
            createdAtUnix = body.optLong("created_at_unix", body.optLong("create_ts")),
        )
    }

    suspend fun stopAgent(
        backendUrl: String,
        agentId: String,
        channelName: String,
    ) {
        postJson(
            backendUrl = backendUrl,
            path = "v1/agora/agent/stop",
            payload = JSONObject()
                .put("agent_id", agentId)
                .put("channel_name", channelName),
        )
    }

    suspend fun interruptAgent(
        backendUrl: String,
        agentId: String,
        channelName: String,
    ) {
        postJson(
            backendUrl = backendUrl,
            path = "v1/agora/agent/interrupt",
            payload = JSONObject()
                .put("agent_id", agentId)
                .put("channel_name", channelName),
        )
    }

    suspend fun reportEmergencyToolResult(
        backendUrl: String,
        settings: EscapeSettings,
        result: EmergencyResult,
        transcriptText: String,
        channelName: String?,
    ) {
        postJson(
            backendUrl = backendUrl,
            path = "v1/emergency/alert-emergency-contact",
            payload = JSONObject()
                .put("source", "android-client")
                .put("persona_name", settings.persona.displayName)
                .put("contact_name", settings.emergencyContact.name)
                .put("codeword", settings.codeword)
                .put("transcript_text", transcriptText)
                .put("delivery", result.delivery.name)
                .put("message", result.message)
                .put("latitude", result.latitude)
                .put("longitude", result.longitude)
                .put("channel_name", channelName),
        )
    }

    private suspend fun getJson(backendUrl: String, path: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(endpoint(backendUrl, path))
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.requireJson()
            }
        }
    }

    private suspend fun postJson(
        backendUrl: String,
        path: String,
        payload: JSONObject,
    ): JSONObject {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(endpoint(backendUrl, path))
                .post(payload.toString().toRequestBody(JSON))
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.requireJson()
            }
        }
    }

    private fun endpoint(backendUrl: String, path: String): String {
        val base = backendUrl.trim().trimEnd('/')
        return "$base/${path.trimStart('/')}"
    }

    private fun okhttp3.Response.requireJson(): JSONObject {
        val raw = body?.string().orEmpty()
        if (!isSuccessful) {
            val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull()
            throw IOException(detail?.takeIf { it.isNotBlank() } ?: "Backend request failed with HTTP $code.")
        }
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun JSONObject.requireString(key: String): String {
        return optString(key).takeIf { it.isNotBlank() }
            ?: throw IOException("Backend response is missing '$key'.")
    }

    private fun JSONObject.requireInt(key: String): Int {
        if (!has(key)) throw IOException("Backend response is missing '$key'.")
        return optInt(key)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val NETWORK_TIMEOUT_SECONDS = 20L
    }
}
