package org.libera.pictotree.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI State for the Login Screen
 */
data class LoginUiState(
    val availableUsers: List<String> = emptyList(),
    val selectedUser: String? = null,
    val isOnlineMode: Boolean = false,
    val isPasswordVisible: Boolean = false
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Initializing with mock data or fetching from repository.
        // Including "New user" option for the initial setup.
        val defaultUsers = listOf("New user", "User A", "User B")
        _uiState.update { 
            it.copy(availableUsers = defaultUsers) 
        }
    }

    fun onUserSelected(user: String) {
        _uiState.update { currentState ->
            val isNewUser = (user == "New user")
            currentState.copy(
                selectedUser = user,
                // Automatically activate online mode if new user
                isOnlineMode = if (isNewUser) true else currentState.isOnlineMode,
                isPasswordVisible = if (isNewUser) true else currentState.isOnlineMode
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
}
