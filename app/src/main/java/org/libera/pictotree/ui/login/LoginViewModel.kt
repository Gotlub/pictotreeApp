package org.libera.pictotree.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.data.repository.AuthRepository

/**
 * UI State for the Login Screen
 */
data class LoginUiState(
    val availableUsers: List<String> = emptyList(),
    val selectedUser: String? = null,
    val isOnlineMode: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository(RetrofitClient.apiService)
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // TODO: Charger la vraie liste des utilisateurs depuis EncryptedSharedPreferences
        val defaultUsers = listOf("User A", "User B")
        _uiState.update { 
            it.copy(availableUsers = defaultUsers) 
        }
    }

    fun onUsernameChanged(username: String) {
        _uiState.update { currentState ->
            val isKnownUser = currentState.availableUsers.contains(username)
            val isNewUser = !isKnownUser && username.isNotBlank()
            
            currentState.copy(
                selectedUser = username,
                // Automatically activate online mode if new user
                isOnlineMode = if (isNewUser) true else currentState.isOnlineMode,
                isPasswordVisible = if (isNewUser) true else currentState.isPasswordVisible
            )
        }
    }

    fun onOnlineModeToggled(isOnline: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                isOnlineMode = isOnline,
                isPasswordVisible = isOnline
            )
        }
    }

    fun login(password: String) {
        val username = _uiState.value.selectedUser
        
        if (username.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Veuillez entrer un nom d'utilisateur valide.") }
            return
        }

        // On affiche le loading et on efface l'erreur précédente
        _uiState.update { it.copy(isLoading = true, errorMessage = null, isLoginSuccessful = false) }

        viewModelScope.launch {
            val result = authRepository.login(username, password)
            
            // À la fin de la requête, on retire le loader
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess { response ->
                // TODO: Succès ! Sauvegarder `response.accessToken` (ex: SharedPreferences ou DataStore)
                // tokenManager.saveToken(response.accessToken)
                
                // Mettre à jour l'état si besoin ou déclencher un événement de navigation
                _uiState.update { it.copy(isLoginSuccessful = true) }
            }.onFailure { exception ->
                // Afficher le message d'erreur
                _uiState.update { it.copy(errorMessage = exception.message ?: "Erreur inconnue de connexion") }
            }
        }
    }
}
