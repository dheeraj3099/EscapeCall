package com.example.escapecall.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.escapecall.data.AlertDelivery
import com.example.escapecall.data.EmergencyResult
import com.example.escapecall.data.EscapeSettings
import com.example.escapecall.notification.IncomingCallNotifier
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class EmergencyEscalator(
    context: Context,
    private val notifier: IncomingCallNotifier,
) {
    private val appContext = context.applicationContext
    private val locationClient = LocationServices.getFusedLocationProviderClient(appContext)

    suspend fun trigger(settings: EscapeSettings): EmergencyResult {
        Log.i(
            TAG,
            "Alert triggered; contact=${settings.emergencyContact.name.ifBlank { "unnamed" }}, " +
                "phone=${maskPhone(settings.emergencyContact.phoneNumber)}",
        )
        val location = fetchCurrentLocation()
        if (location == null) {
            Log.w(TAG, "Current location unavailable; SMS will contain a location-unavailable message.")
        } else {
            Log.i(TAG, "Location resolved: lat=${location.latitude}, lon=${location.longitude}")
        }
        val message = buildMessage(settings, location)
        val smsResult = sendSmsIfAllowed(settings.emergencyContact.phoneNumber, message)
        Log.i(TAG, "Alert delivery result=${smsResult.name}, target=${maskPhone(settings.emergencyContact.phoneNumber)}")
        if (smsResult == AlertDelivery.NotificationFallback) {
            Log.w(TAG, "SMS unavailable; showing local notification fallback.")
            notifier.showEmergencyFallback(message)
        }
        return EmergencyResult(
            delivery = smsResult,
            message = message,
            latitude = location?.latitude,
            longitude = location?.longitude,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocation(): Location? {
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val tokenSource = CancellationTokenSource()
                locationClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                    .await()
            }.getOrNull() ?: runCatching {
                locationClient.lastLocation.await()
            }.getOrNull()
        }
    }

    private suspend fun sendSmsIfAllowed(phoneNumber: String, message: String): AlertDelivery {
        if (phoneNumber.isBlank()) return AlertDelivery.Failed
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return AlertDelivery.NotificationFallback
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appContext.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                AlertDelivery.SmsSent
            }.getOrDefault(AlertDelivery.NotificationFallback)
        }
    }

    private fun buildMessage(settings: EscapeSettings, location: Location?): String {
        val locationText = if (location == null) {
            "Location unavailable"
        } else {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        }
        val contactName = settings.emergencyContact.name.ifBlank { "Emergency contact" }
        return "$contactName, I may be unsafe. EscapeCall detected my safety phrase during a call with ${settings.persona.displayName}. Location: $locationText"
    }

    private fun maskPhone(phoneNumber: String): String {
        val digits = phoneNumber.filter(Char::isDigit)
        if (digits.isBlank()) return "<blank>"
        return if (digits.length <= 4) "****$digits" else "****${digits.takeLast(4)}"
    }

    private companion object {
        const val TAG = "EscapeCallEmergency"
    }
}
