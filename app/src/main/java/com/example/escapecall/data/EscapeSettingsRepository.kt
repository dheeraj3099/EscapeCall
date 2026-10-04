package com.example.escapecall.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.escapecall.BuildConfig
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.escapeCallDataStore by preferencesDataStore(name = "escape_call")

class EscapeSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.escapeCallDataStore

    val settingsFlow: Flow<EscapeSettings> = dataStore.data.map { preferences ->
        EscapeSettings(
            persona = CallerPersona(
                name = preferences[Keys.PersonaName].orEmpty().ifBlank { "Rohan" },
                relationship = preferences[Keys.PersonaRelationship].orEmpty().ifBlank { "Older Brother" },
            ),
            codeword = preferences[Keys.Codeword].orEmpty().ifBlank { "blue umbrella" },
            emergencyContact = EmergencyContact(
                name = preferences[Keys.ContactName].orEmpty(),
                phoneNumber = preferences[Keys.ContactPhone].orEmpty(),
            ),
            demoMode = preferences[Keys.DemoMode] ?: false,
            backendUrl = preferences[Keys.BackendUrl].orEmpty().ifBlank {
                BuildConfig.ESCAPE_BACKEND_URL
            },
        )
    }

    val callHistoryFlow: Flow<List<CallLogEntry>> = dataStore.data.map { preferences ->
        decodeCallLogs(preferences[Keys.CallLogs].orEmpty())
    }

    suspend fun saveSettings(settings: EscapeSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.PersonaName] = settings.persona.name.trim()
            preferences[Keys.PersonaRelationship] = settings.persona.relationship.trim()
            preferences[Keys.Codeword] = settings.codeword.trim()
            preferences[Keys.ContactName] = settings.emergencyContact.name.trim()
            preferences[Keys.ContactPhone] = settings.emergencyContact.phoneNumber.trim()
            preferences[Keys.DemoMode] = settings.demoMode
            preferences[Keys.BackendUrl] = settings.backendUrl.trim().ifBlank {
                BuildConfig.ESCAPE_BACKEND_URL
            }
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DemoMode] = enabled
        }
    }

    suspend fun appendCallLog(entry: CallLogEntry) {
        dataStore.edit { preferences ->
            val existing = decodeCallLogs(preferences[Keys.CallLogs].orEmpty())
            preferences[Keys.CallLogs] = encodeCallLogs((listOf(entry) + existing).take(MAX_LOGS))
        }
    }

    suspend fun clearCallHistory() {
        dataStore.edit { preferences ->
            preferences[Keys.CallLogs] = "[]"
        }
    }

    fun newCallLog(
        settings: EscapeSettings,
        startedAtMillis: Long,
        endedAtMillis: Long,
        codewordTriggered: Boolean,
        alertDelivery: AlertDelivery,
    ): CallLogEntry {
        val durationSeconds = ((endedAtMillis - startedAtMillis).coerceAtLeast(0L) / 1000L)
        return CallLogEntry(
            id = UUID.randomUUID().toString(),
            personaName = settings.persona.displayName,
            relationship = settings.persona.relationship,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            durationSeconds = durationSeconds,
            codewordTriggered = codewordTriggered,
            alertDelivery = alertDelivery,
        )
    }

    private object Keys {
        val PersonaName = stringPreferencesKey("persona_name")
        val PersonaRelationship = stringPreferencesKey("persona_relationship")
        val Codeword = stringPreferencesKey("codeword")
        val ContactName = stringPreferencesKey("contact_name")
        val ContactPhone = stringPreferencesKey("contact_phone")
        val DemoMode = booleanPreferencesKey("demo_mode")
        val BackendUrl = stringPreferencesKey("backend_url")
        val CallLogs = stringPreferencesKey("call_logs")
    }

    private companion object {
        const val MAX_LOGS = 50
    }
}

private fun encodeCallLogs(logs: List<CallLogEntry>): String {
    return JSONArray().apply {
        logs.forEach { log ->
            put(
                JSONObject()
                    .put("id", log.id)
                    .put("personaName", log.personaName)
                    .put("relationship", log.relationship)
                    .put("startedAtMillis", log.startedAtMillis)
                    .put("endedAtMillis", log.endedAtMillis)
                    .put("durationSeconds", log.durationSeconds)
                    .put("codewordTriggered", log.codewordTriggered)
                    .put("alertDelivery", log.alertDelivery.name)
            )
        }
    }.toString()
}

private fun decodeCallLogs(raw: String): List<CallLogEntry> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    CallLogEntry(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        personaName = item.optString("personaName", "Mom"),
                        relationship = item.optString("relationship", "Family"),
                        startedAtMillis = item.optLong("startedAtMillis"),
                        endedAtMillis = item.optLong("endedAtMillis"),
                        durationSeconds = item.optLong("durationSeconds"),
                        codewordTriggered = item.optBoolean("codewordTriggered"),
                        alertDelivery = runCatching {
                            AlertDelivery.valueOf(item.optString("alertDelivery"))
                        }.getOrDefault(AlertDelivery.NotTriggered),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
