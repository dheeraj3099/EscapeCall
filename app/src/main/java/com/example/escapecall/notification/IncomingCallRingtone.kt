package com.example.escapecall.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

/** Plays the device's configured ringtone while the fake incoming-call surface is visible. */
class IncomingCallRingtone(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    fun start() {
        if (player?.isPlaying == true) return
        stop()

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer.create(appContext, ringtoneUri)?.apply {
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                start()
            }
        }.getOrNull()
    }

    fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
    }
}
