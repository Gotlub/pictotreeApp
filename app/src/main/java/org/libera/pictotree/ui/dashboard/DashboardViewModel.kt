package org.libera.pictotree.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import org.libera.pictotree.network.TreeApiService
import org.libera.pictotree.network.dto.ProfileDTO

import kotlinx.coroutines.flow.combine
import org.libera.pictotree.data.database.entity.UserConfig
import org.libera.pictotree.data.repository.UserConfigRepository
import org.libera.pictotree.data.repository.ImageSyncEngine
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.network.RetrofitClient

class DashboardViewModel(
    application: Application,
    private val profileRepository: ProfileRepository,
    private val userConfigRepository: UserConfigRepository,
    private val treeDao: TreeDao,
    private val imageDao: ImageDao,
    private val treeApiService: TreeApiService
) : AndroidViewModel(application) {

    private val _isAdminMode = MutableStateFlow(false)
    val isAdminMode: StateFlow<Boolean> = _isAdminMode

    private val _navigateToProfileEvent = Channel<Long>(Channel.BUFFERED)
    val navigateToProfileEvent = _navigateToProfileEvent.receiveAsFlow()

    private val _playProfileEvent = Channel<Int>(Channel.BUFFERED)
    val playProfileEvent = _playProfileEvent.receiveAsFlow()

    private val _remoteProfiles = MutableStateFlow<List<ProfileDTO>>(emptyList())
    val remoteProfiles: StateFlow<List<ProfileDTO>> = _remoteProfiles

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    val userConfig: StateFlow<UserConfig?> = userConfigRepository.userConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState: StateFlow<DashboardUiState> =
            profileRepository.allProfiles
                    .map { profilesList ->
                        if (profilesList.isEmpty()) DashboardUiState.Empty
                        else DashboardUiState.Success(profilesList)
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
        return storedPin != null && storedPin == input
    }

    fun addProfile(name: String, avatarUrl: String? = null) {
        viewModelScope.launch {
            val id = profileRepository.insertProfile(Profile(name = name, avatarUrl = avatarUrl))
            _navigateToProfileEvent.send(id)
        }
    }

    fun createQuickProfile() {
        viewModelScope.launch {
            val state = uiState.value
            val currentCount = if (state is DashboardUiState.Success) state.profiles.size else 0
            val defaultName = "Profil ${currentCount + 1}"
            val id = profileRepository.insertProfile(Profile(name = defaultName))
            _navigateToProfileEvent.send(id)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch { profileRepository.deleteProfile(profile) }
    }

    fun playProfile(profileId: Int) {
        viewModelScope.launch { _playProfileEvent.send(profileId) }
    }

    fun fetchRemoteProfiles() {
        viewModelScope.launch {
            try {
                val response = treeApiService.getAvailableProfiles()
                if (response.isSuccessful) {
                    _remoteProfiles.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importRemoteProfile(remoteProfile: ProfileDTO) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val sessionManager = SessionManager(getApplication())
                val username = sessionManager.getUsername() ?: "default"
                val token = sessionManager.getToken() ?: ""
                val hostUrl = RetrofitClient.SERVER_URL
                
                // 1. Fetch full details (with tree list)
                val response = treeApiService.getProfileDetails(remoteProfile.id)
                if (!response.isSuccessful) return@launch
                val detailedProfile = response.body() ?: return@launch
                
                // 2. Cascade Sync: Create Local Profile
                var localAvatarUrl: String? = null
                if (!detailedProfile.remoteAvatarUrl.isNullOrEmpty()) {
                    val engine = ImageSyncEngine(getApplication(), imageDao, username, hostUrl, token)
                    localAvatarUrl = engine.downloadSingleImage(detailedProfile.remoteAvatarUrl)
                }
                
                val localProfileId = profileRepository.insertProfile(Profile(
                    name = detailedProfile.name,
                    avatarUrl = localAvatarUrl,
                    remoteAvatarUrl = detailedProfile.remoteAvatarUrl
                )).toInt()
                
                // 3. Cascade Sync: Download Trees
                detailedProfile.trees?.forEachIndexed { index, treeConfig ->
                    val treeId = treeConfig.treeId
                    val treeResponse = treeApiService.getTree(treeId)
                    if (treeResponse.isSuccessful) {
                        treeResponse.body()?.let { fullTree ->
                            val jsonStr = com.google.gson.Gson().toJson(fullTree)
                            val entity = org.libera.pictotree.data.database.entity.TreeEntity(
                                id = fullTree.treeId,
                                name = fullTree.name,
                                jsonPayload = jsonStr,
                                rootUrl = fullTree.rootNode?.imageUrl
                            )
                            treeDao.insertTree(entity)
                            
                            // Associate with profile using the remote colorCode
                            profileRepository.insertProfileTreeCrossRef(
                                org.libera.pictotree.data.database.entity.ProfileTreeCrossRef(
                                    profileId = localProfileId,
                                    treeId = fullTree.treeId,
                                    displayOrder = index + 1,
                                    colorCode = treeConfig.colorCode ?: "#000000"
                                )
                            )
                            
                            // Synchronize images for this tree
                            val engine = ImageSyncEngine(getApplication(), imageDao, username, hostUrl, token)
                            if (fullTree.rootNode != null) {
                                engine.syncImagesFromNode(fullTree.rootNode, fullTree.treeId)
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }
}

class DashboardViewModelFactory(
    private val application: Application,
    private val profileRepository: ProfileRepository,
    private val userConfigRepository: UserConfigRepository,
    private val treeDao: TreeDao,
    private val imageDao: ImageDao,
    private val treeApiService: TreeApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") 
            return DashboardViewModel(application, profileRepository, userConfigRepository, treeDao, imageDao, treeApiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
