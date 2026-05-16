package org.libera.pictotree.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.data.repository.AuthRepository
import org.libera.pictotree.data.database.AppDatabase

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
    val isLoginSuccessful: Boolean = false,
    val token: String? = null,
    val refreshToken: String? = null,
    val username: String? = null
)

class LoginViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(RetrofitClient.apiService)
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun loadKnownUsers(users: List<String>) {
        if (_uiState.value.availableUsers != users) {
             _uiState.update { it.copy(availableUsers = users) }
        }
    }

    fun onUsernameChanged(username: String) {
        _uiState.update { currentState ->
            val isKnownUser = currentState.availableUsers.contains(username)
            val isNewUser = !isKnownUser && username.isNotBlank()
            
            currentState.copy(
                selectedUser = username,
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
        val isOnline = _uiState.value.isOnlineMode
        
        if (username.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Veuillez entrer un nom d'utilisateur valide.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, isLoginSuccessful = false) }

        if (!isOnline) {
            viewModelScope.launch {
                val knownUsers = _uiState.value.availableUsers
                if (knownUsers.contains(username)) {
                    // VERIFIER L'AUTORISATION HORS LIGNE
                    val db = AppDatabase.getDatabase(getApplication(), username)
                    val config = db.userConfigDao().getUserConfigFlow().first()
                    
                    if (config?.isOfflineAccessAllowed == true) {
                        _uiState.update { it.copy(
                            isLoading = false,
                            isLoginSuccessful = true,
                            token = null,
                            username = username
                        ) }
                    } else {
                        _uiState.update { it.copy(
                            isLoading = false,
                            errorMessage = "L'accès hors-ligne n'est pas autorisé pour ce compte. Connectez-vous en ligne."
                        ) }
                    }
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = "Utilisateur inconnu localement. Connectez-vous en ligne."
                    ) }
                }
            }
            return
        }

        viewModelScope.launch {
            val result = authRepository.login(username, password)
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess { response ->
                _uiState.update { it.copy(
                    isLoginSuccessful = true,
                    token = response.accessToken,
                    refreshToken = response.refreshToken,
                    username = username
                ) }
            }.onFailure { exception ->
                _uiState.update { it.copy(errorMessage = exception.message ?: "Erreur de connexion") }
            }
        }
    }
}
