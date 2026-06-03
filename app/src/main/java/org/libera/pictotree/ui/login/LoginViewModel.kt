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
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.utils.UiText

/**
 * UI State for the Login Screen
 */
data class LoginUiState(
    val availableUsers: List<String> = emptyList(),
    val selectedUser: String? = null,
    val isOnlineMode: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val isLoginSuccessful: Boolean = false,
    val token: String? = null,
    val refreshToken: String? = null,
    val username: String? = null,
    // NOUVEAU : Information sur la disponibilité du mode hors-ligne
    val isOfflineAvailable: Boolean = true 
)

class LoginViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(RetrofitClient.apiService)
    private val sessionManager = SessionManager(application)
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
            
            // VERIFIER SI LE MODE HORS LIGNE EST AUTORISÉ POUR CETTE PERSONNE
            val offlineAllowed = if (isKnownUser) sessionManager.isOfflineAccessAllowed(username) else false

            currentState.copy(
                selectedUser = username,
                isOnlineMode = if (isNewUser || !offlineAllowed) true else currentState.isOnlineMode,
                isPasswordVisible = if (isNewUser || !offlineAllowed) true else currentState.isPasswordVisible,
                isOfflineAvailable = offlineAllowed
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
            _uiState.update { it.copy(errorMessage = UiText.StringResource(org.libera.pictotree.R.string.error_invalid_username)) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, isLoginSuccessful = false) }

        if (!isOnline) {
            val knownUsers = _uiState.value.availableUsers
            if (knownUsers.contains(username)) {
                val isAllowed = sessionManager.isOfflineAccessAllowed(username)

                if (isAllowed) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        isLoginSuccessful = true,
                        token = null,
                        username = username
                    ) }
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = UiText.StringResource(org.libera.pictotree.R.string.login_offline_not_allowed)
                    ) }
                }
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = UiText.StringResource(org.libera.pictotree.R.string.error_user_unknown_local)
                ) }
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
                val errorUiText = exception.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(org.libera.pictotree.R.string.error_connection)
                _uiState.update { it.copy(errorMessage = errorUiText) }
            }
        }
    }
}
