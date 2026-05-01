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

import kotlinx.coroutines.flow.combine
import org.libera.pictotree.data.database.entity.UserConfig
import org.libera.pictotree.data.repository.UserConfigRepository

class DashboardViewModel(
    private val profileRepository: ProfileRepository,
    private val userConfigRepository: UserConfigRepository
) : ViewModel() {

    private val _isAdminMode = MutableStateFlow(false)
    val isAdminMode: StateFlow<Boolean> = _isAdminMode

    private val _navigateToProfileEvent = Channel<Long>(Channel.BUFFERED)
    val navigateToProfileEvent = _navigateToProfileEvent.receiveAsFlow()

    private val _playProfileEvent = Channel<Int>(Channel.BUFFERED)
    val playProfileEvent = _playProfileEvent.receiveAsFlow()

    val userConfig: StateFlow<UserConfig?> = userConfigRepository.userConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // État de l'interface utilisateur exposé à la vue
    val uiState: StateFlow<DashboardUiState> =
            profileRepository.allProfiles
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

    init {
        viewModelScope.launch {
            userConfigRepository.initializeDefaultIfNeeded()
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { userConfigRepository.saveLocale(lang) }
    }

    fun setPin(pin: String?) {
        viewModelScope.launch { userConfigRepository.savePin(pin) }
    }

    fun setAdminMode(isAdmin: Boolean) {
        _isAdminMode.value = isAdmin
    }

    fun verifyPin(input: String): Boolean {
        val storedPin = userConfig.value?.offlineSettingsPin
        // Si aucun PIN n'est défini, on refuse l'accès par sécurité (ou on pourrait autoriser, mais ton choix était de verrouiller)
        return storedPin != null && storedPin == input
    }

    fun addProfile(name: String, avatarUrl: String? = null) {
        viewModelScope.launch {
            val id = profileRepository.insertProfile(Profile(name = name, avatarUrl = avatarUrl))
            _navigateToProfileEvent.send(id)
        }
    }

    /**
     * Crée un profil par défaut (ex: Profil 3) et déclenche la navigation immédiate
     */
    fun createQuickProfile() {
        viewModelScope.launch {
            val state = uiState.value
            val currentCount = if (state is DashboardUiState.Success) state.profiles.size else 0
            val defaultName = "Profil ${currentCount + 1}"
            
            // On peut choisir un avatar par défaut au hasard ou laisser vide
            val id = profileRepository.insertProfile(Profile(name = defaultName))
            _navigateToProfileEvent.send(id)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch { profileRepository.deleteProfile(profile) }
    }

    fun playProfile(profileId: Int) {
        viewModelScope.launch {
            _playProfileEvent.send(profileId)
        }
    }
}

class DashboardViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val userConfigRepository: UserConfigRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return DashboardViewModel(profileRepository, userConfigRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
