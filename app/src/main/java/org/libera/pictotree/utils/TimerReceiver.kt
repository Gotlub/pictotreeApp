package org.libera.pictotree.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.libera.pictotree.R

/**
 * Gère le réveil du système pour l'alarme de fin du Time Timer.
 * Fonctionne même si l'application est en arrière-plan ou fermée.
 */
class TimerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimerReceiver"
        private const val CHANNEL_ID_SOUND = "timer_notifications_sound"
        private const val CHANNEL_ID_SILENT = "timer_notifications_silent_v4"
        const val ACTION_STOP_ALARM = "org.libera.pictotree.ACTION_STOP_ALARM"
        const val ACTION_ALARM_TRIGGERED = "org.libera.pictotree.ACTION_ALARM_TRIGGERED"

        // Référence statique pour couper la sonnerie en cours
        private var activeRingtone: Ringtone? = null
        private var activeMediaPlayer: android.media.MediaPlayer? = null

        /**
         * Coupe manuellement la sonnerie d'alarme active.
         */
        fun stopActiveRingtone() {
            try {
                activeRingtone?.let {
                    if (it.isPlaying) {
                        it.stop()
                        Log.i(TAG, "Active ringtone stopped successfully.")
                    }
                }
                activeRingtone = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop active ringtone", e)
            }
            try {
                activeMediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                    Log.i(TAG, "Active media player stopped successfully.")
                }
                activeMediaPlayer = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop active media player", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_STOP_ALARM) {
            Log.i(TAG, "ACTION_STOP_ALARM received. Stopping alarm...")
            stopActiveRingtone()

            // Annuler la notification correspondante
            val notificationId = intent.getIntExtra("EXTRA_NOTIFICATION_ID", -1)
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
            
            // Émettre également un broadcast local pour que MainActivity masque la bannière
            val stopLocalIntent = Intent(ACTION_STOP_ALARM).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(stopLocalIntent)
            return
        }

        val label = intent.getStringExtra("EXTRA_LABEL") ?: "Temps écoulé"
        val playSound = intent.getBooleanExtra("EXTRA_PLAY_SOUND", true)
        Log.i(TAG, "ALARM RECEIVED: $label, playSound=$playSound")

        // 1. JOUER LE SON D'ALARME
        if (playSound) {
            try {
                // Arrêter toute alarme précédente
                stopActiveRingtone()

                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                
                val player = android.media.MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = false
                    prepare()
                    start()
                }
                activeMediaPlayer = player

                // On arrête automatiquement le MediaPlayer après 3 secondes
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    stopActiveRingtone()
                }, 3000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play alarm sound via MediaPlayer, falling back to Ringtone", e)
                try {
                    val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                    activeRingtone = ringtone
                    ringtone.play()

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        stopActiveRingtone()
                    }, 3000)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to play fallback Ringtone", ex)
                }
            }
        }

        // 3. SIGNALER L'ALERTE LOCALEMENT (Pour affichage de la bannière in-app dans MainActivity)
        val triggerLocalIntent = Intent(ACTION_ALARM_TRIGGERED).apply {
            setPackage(context.packageName)
            putExtra("EXTRA_LABEL", label)
        }
        context.sendBroadcast(triggerLocalIntent)
    }
}
