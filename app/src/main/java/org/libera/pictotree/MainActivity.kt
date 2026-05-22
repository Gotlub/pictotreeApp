package org.libera.pictotree

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import kotlinx.coroutines.launch
import android.content.pm.ActivityInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import org.libera.pictotree.utils.TimerReceiver
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.utils.AuthEvents

class MainActivity : AppCompatActivity() {

    private var activeAlarmLabel: String? = null

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == TimerReceiver.ACTION_ALARM_TRIGGERED) {
                val label = intent.getStringExtra("EXTRA_LABEL") ?: "Temps écoulé"
                val repeatSound = intent.getBooleanExtra("EXTRA_REPEAT_SOUND", false)
                if (repeatSound) {
                    activeAlarmLabel = label
                    showAlarmBanner(label)
                }
            } else if (action == TimerReceiver.ACTION_STOP_ALARM) {
                hideAlarmBanner()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialiser Retrofit avec les providers du SessionManager
        val sessionManager = SessionManager(this)
        
        // On ne force plus l'orientation au démarrage global pour laisser le Login/Profil libres
        // requestedOrientation = sessionManager.getPreferredOrientation()

        RetrofitClient.init(
            tokenProvider = { sessionManager.getToken() },
            refreshTokenProvider = { sessionManager.getRefreshToken() },
            onTokenRefreshed = { newToken -> sessionManager.updateAccessToken(newToken) }
        )

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // On ne met pas de padding en bas pour laisser le bandeau de phrase 
            // descendre jusqu'au bord de l'écran (edge-to-edge) comme la vue Treant.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        // Configurer le clic sur le bouton Arrêter de la bannière globale
        findViewById<View>(R.id.btn_stop_alarm)?.setOnClickListener {
            TimerReceiver.stopActiveRingtone()
            val stopIntent = Intent(this, TimerReceiver::class.java).apply {
                action = TimerReceiver.ACTION_STOP_ALARM
                activeAlarmLabel?.let {
                    putExtra("EXTRA_NOTIFICATION_ID", it.hashCode())
                }
            }
            sendBroadcast(stopIntent)
            hideAlarmBanner()
        }

        // Enregistrer le récepteur d'alarme
        val filter = IntentFilter().apply {
            addAction(TimerReceiver.ACTION_ALARM_TRIGGERED)
            addAction(TimerReceiver.ACTION_STOP_ALARM)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alarmReceiver, filter)
        }

        // Écouter les événements de déconnexion globale (ex: 401)
        lifecycleScope.launch {
            AuthEvents.logoutEvent.collect {
                showSessionExpiredDialog(sessionManager)
            }
        }
    }

    private fun showAlarmBanner(label: String) {
        val banner = findViewById<MaterialCardView>(R.id.card_global_alarm_banner) ?: return
        val tvMessage = findViewById<TextView>(R.id.tv_alarm_message) ?: return
        
        tvMessage.text = "Minuteur terminé : $label"
        
        if (banner.visibility != View.VISIBLE) {
            banner.visibility = View.VISIBLE
            banner.alpha = 0f
            banner.translationY = -100f
            banner.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun hideAlarmBanner() {
        val banner = findViewById<MaterialCardView>(R.id.card_global_alarm_banner) ?: return
        if (banner.visibility == View.VISIBLE) {
            banner.animate()
                .alpha(0f)
                .translationY(-100f)
                .setDuration(300)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    banner.visibility = View.GONE
                }
                .start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(alarmReceiver)
        } catch (e: Exception) {
            // Déjà désenregistré ou jamais enregistré
        }
    }

    private fun showSessionExpiredDialog(sessionManager: SessionManager) {
        // Éviter d'empiler les dialogues si plusieurs 401 arrivent
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Session expirée")
            .setMessage("Votre connexion au serveur a expiré. Souhaitez-vous continuer en mode hors-ligne (votre travail actuel sera conservé) ou vous reconnecter ?")
            .setCancelable(false)
            .setPositiveButton("Rester hors-ligne") { _, _ ->
                sessionManager.switchToOfflineMode()
                // L'utilisateur reste là où il est, les fonctions online se griseront
            }
            .setNegativeButton("Se reconnecter") { _, _ ->
                handleLogout(sessionManager)
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Vérification sommaire au réveil de l'app (AFK check)
        val sessionManager = SessionManager(this)
        if (sessionManager.isOnline() && sessionManager.getToken() == null) {
            handleLogout(sessionManager)
        }
    }

    private fun handleLogout(sessionManager: SessionManager) {
        sessionManager.logout()
        // Rediriger vers le Login
        try {
            findNavController(R.id.fragment_container).navigate(R.id.loginFragment)
        } catch (e: Exception) {
            // Si on est déjà au login ou ailleurs
        }
    }

    private var isOrientationLockDisabled = false

    fun disableOrientationLock() { isOrientationLockDisabled = true }
    fun enableOrientationLock() { isOrientationLockDisabled = false }

    fun applyUserOrientation() {
        if (isOrientationLockDisabled) return
        val sessionManager = SessionManager(this)
        val username = sessionManager.getUsername() ?: return
        val preferred = sessionManager.getPreferredOrientation(username)
        if (requestedOrientation != preferred) {
            requestedOrientation = preferred
        }
    }

    fun restoreSystemOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun toggleOrientation() {
        val sessionManager = SessionManager(this)
        val username = sessionManager.getUsername() ?: return
        val current = sessionManager.getPreferredOrientation(username)
        val next = if (current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        sessionManager.setPreferredOrientation(username, next)
        requestedOrientation = next
    }
}
