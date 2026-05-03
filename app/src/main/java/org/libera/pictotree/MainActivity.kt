package org.libera.pictotree

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import kotlinx.coroutines.launch
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.utils.AuthEvents

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialiser Retrofit avec les providers du SessionManager
        val sessionManager = SessionManager(this)
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

        // Écouter les événements de déconnexion globale (ex: 401)
        lifecycleScope.launch {
            AuthEvents.logoutEvent.collect {
                showSessionExpiredDialog(sessionManager)
            }
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
        sessionManager.clearSession()
        // Rediriger vers le Login
        try {
            findNavController(R.id.fragment_container).navigate(R.id.loginFragment)
        } catch (e: Exception) {
            // Si on est déjà au login ou ailleurs
        }
    }
}
