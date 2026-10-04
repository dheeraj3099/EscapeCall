package com.example.escapecall.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.escapecall.EscapeCallActions
import com.example.escapecall.MainActivity
import com.example.escapecall.R
import com.example.escapecall.data.CallerPersona

class IncomingCallNotifier(private val context: Context) {
    private val appContext = context.applicationContext

    fun showIncomingCall(persona: CallerPersona) {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val caller = Person.Builder()
            .setName(persona.displayName)
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(appContext, CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(persona.displayName)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setColor(Color.rgb(34, 197, 94))
            .setFullScreenIntent(activityIntent(EscapeCallActions.ShowIncomingCall), true)
            .setContentIntent(activityIntent(EscapeCallActions.ShowIncomingCall))
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    activityIntent(EscapeCallActions.DeclineCall),
                    activityIntent(EscapeCallActions.AcceptCall),
                )
            )
            .addPerson(caller)
            .build()

        NotificationManagerCompat.from(appContext).notify(INCOMING_CALL_ID, notification)
    }

    fun showEmergencyFallback(message: String) {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_EMERGENCY_FALLBACK)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Emergency alert prepared")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(appContext).notify(EMERGENCY_FALLBACK_ID, notification)
    }

    fun cancelIncomingCall() {
        NotificationManagerCompat.from(appContext).cancel(INCOMING_CALL_ID)
    }

    private fun activityIntent(action: String): PendingIntent {
        val requestCode = when (action) {
            EscapeCallActions.AcceptCall -> 1
            EscapeCallActions.DeclineCall -> 2
            EscapeCallActions.EndCall -> 3
            else -> 0
        }
        val intent = Intent(appContext, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val callChannel = NotificationChannel(
            CHANNEL_INCOMING_CALL,
            "Incoming fake calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shows EscapeCall incoming-call notifications."
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        val fallbackChannel = NotificationChannel(
            CHANNEL_EMERGENCY_FALLBACK,
            "Emergency fallback alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Used if SMS delivery is blocked by the device."
        }
        notificationManager.createNotificationChannel(callChannel)
        notificationManager.createNotificationChannel(fallbackChannel)
    }

    companion object {
        private const val CHANNEL_INCOMING_CALL = "escape_call_incoming"
        private const val CHANNEL_EMERGENCY_FALLBACK = "escape_call_emergency_fallback"
        private const val INCOMING_CALL_ID = 40_411
        private const val EMERGENCY_FALLBACK_ID = 40_412
    }
}
