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
        private const val CHANNEL_ID = "timer_notifications"
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
            val stopLocalIntent = Intent(ACTION_STOP_ALARM)
            context.sendBroadcast(stopLocalIntent)
            return
        }

        val label = intent.getStringExtra("EXTRA_LABEL") ?: "Temps écoulé"
        val playSound = intent.getBooleanExtra("EXTRA_PLAY_SOUND", true)
        val repeatSound = intent.getBooleanExtra("EXTRA_REPEAT_SOUND", false)
        Log.i(TAG, "ALARM RECEIVED: $label, playSound=$playSound, repeatSound=$repeatSound")

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
                    isLooping = repeatSound
                    prepare()
                    start()
                }
                activeMediaPlayer = player

                // Si pas de répétition, on arrête automatiquement le MediaPlayer après 3 secondes
                if (!repeatSound) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        stopActiveRingtone()
                    }, 3000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play alarm sound via MediaPlayer, falling back to Ringtone", e)
                try {
                    val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                    activeRingtone = ringtone
                    ringtone.play()

                    if (!repeatSound) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            stopActiveRingtone()
                        }, 3000)
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to play fallback Ringtone", ex)
                }
            }
        }

        // 2. AFFICHER UNE NOTIFICATION
        showNotification(context, label, repeatSound)

        // 3. SIGNALER L'ALERTE LOCALEMENT (Pour affichage de la bannière in-app dans MainActivity)
        val triggerLocalIntent = Intent(ACTION_ALARM_TRIGGERED).apply {
            putExtra("EXTRA_LABEL", label)
            putExtra("EXTRA_REPEAT_SOUND", repeatSound)
        }
        context.sendBroadcast(triggerLocalIntent)

        // 4. TOAST DE SÉCURITÉ
        Toast.makeText(context, "Fini : $label", Toast.LENGTH_LONG).show()
    }

    private fun showNotification(context: Context, label: String, repeatSound: Boolean) {
        val notificationId = label.hashCode()

        // Création du canal si requis (Oreo API 26+)
        createNotificationChannel(context)

        // Intent d'action pour arrêter l'alarme
        val stopIntent = Intent(context, TimerReceiver::class.java).apply {
            action = ACTION_STOP_ALARM
            putExtra("EXTRA_NOTIFICATION_ID", notificationId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("PictoTree : Temps écoulé !")
            .setContentText("L'activité '$label' est terminée.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(!repeatSound)
            .setOngoing(repeatSound)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Arrêter",
                stopPendingIntent
            )

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Time Timer Alarms"
            val descriptionText = "Notifications de fin d'activité pour le Time Timer"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
