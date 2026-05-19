package org.libera.pictotree.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("EXTRA_LABEL") ?: "Temps écoulé"
        Log.i(TAG, "ALARM RECEIVED: $label")

        // 1. JOUER LE SON D'ALARME
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, alarmUri)
            ringtone.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound", e)
        }

        // 2. AFFICHER UNE NOTIFICATION (Pour Android 10+ et visibilité background)
        showNotification(context, label)

        // 3. TOAST DE SÉCURITÉ
        Toast.makeText(context, "Fini : $label", Toast.LENGTH_LONG).show()
    }

    private fun showNotification(context: Context, label: String) {
        // Note: Le canal de notification doit être créé dans l'Application ou MainActivity
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("PictoTree : Temps écoulé !")
            .setContentText("L'activité '$label' est terminée.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(label.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing", e)
        }
    }
}
