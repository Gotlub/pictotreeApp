package org.libera.pictotree.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.SessionManager

class LoginFragment : Fragment() {

    private lateinit var viewModel: LoginViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]
        sessionManager = SessionManager(requireContext())

        // Initialisation avec les IDs exacts du XML
        val actvUsers = view.findViewById<MaterialAutoCompleteTextView>(R.id.actv_users)
        val etPassword = view.findViewById<TextInputEditText>(R.id.et_password)
        val switchOnlineMode = view.findViewById<MaterialSwitch>(R.id.switch_online_mode)
        val btnLogin = view.findViewById<Button>(R.id.btn_login)
        val tilPassword = view.findViewById<TextInputLayout>(R.id.til_password)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val tvOfflineHint = view.findViewById<TextView>(R.id.tv_offline_hint)

        actvUsers.doAfterTextChanged { text ->
            viewModel.onUsernameChanged(text?.toString() ?: "")
        }

        switchOnlineMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onOnlineModeToggled(isChecked)
        }

        btnLogin.setOnClickListener {
            val password = etPassword.text?.toString() ?: ""
            viewModel.login(password)
        }
        
        // Correction type mismatch: Set -> List
        val knownUsers = sessionManager.getKnownUsers().toList()
        viewModel.loadKnownUsers(knownUsers)

        var lastUsersList: List<String>? = null

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Mise à jour de l'adapter auto-completion seulement si la liste a changé
                    if (state.availableUsers != lastUsersList) {
                        lastUsersList = state.availableUsers
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, state.availableUsers)
                        actvUsers.setAdapter(adapter)
                    }

                    // Gestion de la visibilité du mot de passe
                    tilPassword.visibility = if (state.isPasswordVisible) View.VISIBLE else View.GONE
                    
                    // Synchronisation forcée de l'état du switch
                    if (switchOnlineMode.isChecked != state.isOnlineMode) {
                        switchOnlineMode.isChecked = state.isOnlineMode
                    }
                    
                    // FEEDBACK HORS LIGNE
                    if (!state.isOfflineAvailable && state.selectedUser != null && state.availableUsers.contains(state.selectedUser)) {
                        switchOnlineMode.isEnabled = false
                        tvOfflineHint.visibility = View.VISIBLE
                        tvOfflineHint.text = getString(R.string.login_offline_not_allowed)
                    } else {
                        switchOnlineMode.isEnabled = true
                        tvOfflineHint.visibility = if (state.isOnlineMode) View.GONE else View.VISIBLE
                        tvOfflineHint.text = getString(R.string.login_offline_read_only)
                    }

                    // Bouton et chargement
                    btnLogin.isEnabled = !state.isLoading
                    progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    // Erreurs
                    if (state.errorMessage != null) {
                        Toast.makeText(requireContext(), state.errorMessage.asString(requireContext()), Toast.LENGTH_LONG).show()
                    }

                    // Connexion réussie
                    if (state.isLoginSuccessful) {
                        val username = state.username ?: actvUsers.text.toString()
                        sessionManager.saveSession(username, state.token, state.refreshToken)
                        
                        val bundle = Bundle().apply {
                            putBoolean("isAdmin", state.isOnlineMode)
                        }
                        findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment, bundle)
                    }
                }
            }
        }
    }
}
