package org.libera.pictotree.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.repository.ProfileRepository

class DashboardViewModel(private val repository: ProfileRepository) : ViewModel() {

    // En temps normal, cette valeur viendrait de SharedPreferences ou du système
    // d'authentification.
    // Pour l'instant on expose un MutableStateFlow qu'on peut modifier pour tester.
    private val _isAdminMode = MutableStateFlow(false)
    val isAdminMode: StateFlow<Boolean> = _isAdminMode

    private val _navigateToProfileEvent = Channel<Long>(Channel.BUFFERED)
    val navigateToProfileEvent = _navigateToProfileEvent.receiveAsFlow()

    // État de l'interface utilisateur exposé à la vue
    val uiState: StateFlow<DashboardUiState> =
            repository
                    .allProfiles
                    .map { profilesList ->
                        if (profilesList.isEmpty()) {
                            DashboardUiState.Empty
                        } else {
                            DashboardUiState.Success(profilesList)
                        }
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = DashboardUiState.Loading
                    )

    fun setAdminMode(isAdmin: Boolean) {
        _isAdminMode.value = isAdmin
    }

    fun addProfile(name: String, avatarUrl: String? = null) {
        viewModelScope.launch {
            val id = repository.insertProfile(Profile(name = name, avatarUrl = avatarUrl))
            _navigateToProfileEvent.send(id)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch { repository.deleteProfile(profile) }
    }
}

class DashboardViewModelFactory(private val repository: ProfileRepository) :
        ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return DashboardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
