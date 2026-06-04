package org.libera.pictotree.ui.editprofile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.ProfileTreeCrossRef
import org.libera.pictotree.data.database.entity.TreeEntity
import org.libera.pictotree.network.dto.TreeMetadataDTO
import org.libera.pictotree.network.dto.TreeFullDTO
import org.libera.pictotree.data.repository.ImageSyncEngine
import org.libera.pictotree.data.repository.SyncResult
import org.libera.pictotree.data.repository.ProfileRepository
import org.libera.pictotree.network.TreeApiService
import org.libera.pictotree.data.model.ProfileSettings
import org.libera.pictotree.ui.explorer.TreeNode
import java.io.File

class EditProfileViewModel(
    application: Application,
    private val profileRepository: ProfileRepository,
    private val profileDao: ProfileDao,
    private val treeDao: TreeDao,
    private val imageDao: ImageDao,
    private val treeApiService: TreeApiService
) : AndroidViewModel(application) {

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

    private val TAG = "EditProfileViewModel"

    private val _uiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(ProfileSettings())
    val settings: StateFlow<ProfileSettings> = _settings.asStateFlow()

    private val _showTreeSelectionEvent = Channel<Unit>(Channel.BUFFERED)
    val showTreeSelectionEvent = _showTreeSelectionEvent.receiveAsFlow()

    private val _syncResultEvent = Channel<SyncResult>(Channel.BUFFERED)
    val syncResultEvent = _syncResultEvent.receiveAsFlow()

    private val _saveCompletedEvent = Channel<Unit>(Channel.BUFFERED)
    val saveCompletedEvent = _saveCompletedEvent.receiveAsFlow()

    private val _remoteTrees = MutableStateFlow<List<TreeMetadataDTO>>(emptyList())
    val remoteTrees: StateFlow<List<TreeMetadataDTO>> = _remoteTrees.asStateFlow()

    private var profileId: Int = -1

    fun loadProfile(profileId: Int) {
        this.profileId = profileId
        viewModelScope.launch {
            try {
                val profile = profileDao.getProfileById(profileId)
                if (profile != null) {
                    val savedSettings = profile.settingsJson?.let {
                        try { Gson().fromJson(it, ProfileSettings::class.java) }
                        catch (e: Exception) { ProfileSettings() }
                    } ?: ProfileSettings()
                    
                    _settings.value = savedSettings

                    val treesWithColor = profileDao.getTreesWithColorForProfile(profileId)
                    val sessionManager = SessionManager(getApplication())
                    val username = sessionManager.getUsername() ?: "default"
                    
                    val uiModels = treesWithColor.map { item ->
                        val tree = item.tree
                        var localPath: String? = null
                        try {
                            if (!tree.rootUrl.isNullOrEmpty()) {
                                val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
                                val normalizedUrl = org.libera.pictotree.utils.FileUtils.normalizeUrl(tree.rootUrl!!, hostUrl)
                                val cleanUrl = org.libera.pictotree.utils.FileUtils.getCleanUrl(normalizedUrl)
                                val imageEntity = imageDao.getImageByRemotePath(cleanUrl)
                                if (imageEntity != null) {
                                    val file = File(getApplication<Application>().filesDir, "$username/${imageEntity.localPath}")
                                    if (file.exists()) localPath = file.absolutePath
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                        ProfileTreeUiModel(tree, localPath, item.colorCode)
                    }
                    _uiState.value = EditProfileUiState.Success(profile, uiModels)
                } else {
                    _uiState.value = EditProfileUiState.Error("Profile not found")
                }
            } catch(e: Exception) {
               _uiState.value = EditProfileUiState.Error(e.message ?: "Error loading profile")
            }
        }
    }

    fun searchTrees(query: String) {
        viewModelScope.launch {
            try {
                val response = treeApiService.getAvailableTrees(isPublic = false, search = query.takeIf { it.isNotBlank() }, page = 1, limit = 50)
                if (response.isSuccessful) _remoteTrees.value = response.body() ?: emptyList()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadMoreTrees() {
        // Version simplifiée : on pourrait ajouter de la pagination réelle ici
    }

    fun openTreeSelection() {
        viewModelScope.launch { searchTrees(""); _showTreeSelectionEvent.send(Unit) }
    }

    fun synchronizeAndImportTree(treeId: Int, profileId: Int, username: String) {
        val sessionManager = SessionManager(getApplication())
        val authToken = sessionManager.getToken() ?: ""
        val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL

        viewModelScope.launch {
            try {
                val response = treeApiService.getTree(treeId)
                if (response.isSuccessful && response.body() != null) {
                    val fullTree = response.body()!!
                    val jsonPayload = Gson().toJson(fullTree)
                    val rawRootUrl = fullTree.rootNode?.imageUrl ?: ""
                    val cleanRootUrl = rawRootUrl.takeIf { it.isNotEmpty() }?.let {
                        val normalized = org.libera.pictotree.utils.FileUtils.normalizeUrl(it, hostUrl)
                        org.libera.pictotree.utils.FileUtils.getCleanUrl(normalized)
                    }

                    val treeEntity = TreeEntity(
                        id = fullTree.treeId,
                        name = fullTree.name,
                        jsonPayload = jsonPayload,
                        isPublic = false,
                        lastSync = System.currentTimeMillis(),
                        rootUrl = cleanRootUrl,
                        lastModif = fullTree.lastModif
                    )
                    treeDao.insertTree(treeEntity)

                    val engine = ImageSyncEngine(getApplication(), imageDao, username, hostUrl, authToken)
                    val result = fullTree.rootNode?.let { engine.syncImagesFromNode(it, fullTree.treeId) } ?: SyncResult(0, 0)
                    
                    val maxOrder = profileDao.getMaxDisplayOrderForProfile(profileId) ?: -1
                    profileRepository.insertProfileTreeCrossRef(ProfileTreeCrossRef(profileId, treeEntity.id, maxOrder + 1))
                    
                    _syncResultEvent.send(result)
                    loadProfile(profileId)
                }
            } catch (e: org.libera.pictotree.data.repository.UnauthorizedException) {
                org.libera.pictotree.utils.AuthEvents.triggerLogout()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /**
     * Tente de réparer un arbre en téléchargeant les images manquantes.
     */
    fun repairTree(treeId: Int, username: String) {
        viewModelScope.launch {
            try {
                val treeEntity = treeDao.getTreeById(treeId) ?: return@launch
                val fullTree = Gson().fromJson(treeEntity.jsonPayload, TreeFullDTO::class.java)
                
                val sessionManager = SessionManager(getApplication())
                val authToken = sessionManager.getToken() ?: ""
                val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
                
                val engine = ImageSyncEngine(getApplication(), imageDao, username, hostUrl, authToken)
                val result = fullTree.rootNode?.let { engine.syncImagesFromNode(it, treeId) } ?: SyncResult(0, 0)
                
                _syncResultEvent.send(result)
                loadProfile(profileId)
            } catch (e: org.libera.pictotree.data.repository.UnauthorizedException) {
                org.libera.pictotree.utils.AuthEvents.triggerLogout()
            } catch (e: Exception) {
                Log.e(TAG, "Repair failed for tree $treeId", e)
            }
        }
    }

    fun deleteTreeFromProfile(profileId: Int, treeId: Int) {
        viewModelScope.launch {
            profileRepository.removeTreeFromProfile(profileId, treeId)
            loadProfile(profileId)
        }
    }

    fun updateTreesOrder(profileId: Int, reorderedTrees: List<ProfileTreeUiModel>) {
        viewModelScope.launch {
            val crossRefs = reorderedTrees.mapIndexed { index, model ->
                ProfileTreeCrossRef(profileId = profileId, treeId = model.tree.id, displayOrder = index, colorCode = model.colorCode)
            }
            profileDao.updateProfileTreeCrossRefs(crossRefs)
        }
    }

    fun updateTreeColor(profileId: Int, treeId: Int, colorCode: String) {
        viewModelScope.launch { profileDao.updateTreeColor(profileId, treeId, colorCode); loadProfile(profileId) }
    }

    fun updateProfile(profileId: Int, newName: String, avatarUrl: String?) {
        viewModelScope.launch {
            updateProfileSuspend(profileId, newName, avatarUrl)
        }
    }

    fun saveProfile(profileId: Int, newName: String, avatarUrl: String?) {
        viewModelScope.launch {
            try {
                updateProfileSuspend(profileId, newName, avatarUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _saveCompletedEvent.send(Unit)
            }
        }
    }

    suspend fun updateProfileSuspend(profileId: Int, newName: String, avatarUrl: String?) = withContext(ioDispatcher) {
        try {
            val sessionManager = SessionManager(getApplication())
            val username = sessionManager.getUsername() ?: "default"
            val token = sessionManager.getToken() ?: ""
            val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
            
            val currentProfile = profileDao.getProfileById(profileId)
            if (currentProfile == null) return@withContext
            
            var finalLocalAvatarUrl = avatarUrl
            var finalRemoteAvatarUrl = currentProfile.remoteAvatarUrl
            
            val isInputRemote = avatarUrl != null && !avatarUrl.startsWith("file://") && !avatarUrl.startsWith("color:")
            
            if (isInputRemote) {
                finalRemoteAvatarUrl = avatarUrl
                val engine = ImageSyncEngine(getApplication(), imageDao, username, hostUrl, token)
                finalLocalAvatarUrl = engine.downloadSingleImage(avatarUrl!!) ?: avatarUrl
            } else {
                finalLocalAvatarUrl = avatarUrl ?: currentProfile.avatarUrl
            }

            val updated = currentProfile.copy(
                name = newName, 
                avatarUrl = finalLocalAvatarUrl, 
                remoteAvatarUrl = finalRemoteAvatarUrl, 
                settingsJson = Gson().toJson(_settings.value)
            )
            
            profileRepository.updateProfile(updated)
            withContext(Dispatchers.Main) {
                loadProfile(profileId)
            }
        } catch (e: org.libera.pictotree.data.repository.UnauthorizedException) {
            org.libera.pictotree.utils.AuthEvents.triggerLogout()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateSettings(newSettings: ProfileSettings, profileId: Int) {
        _settings.value = newSettings
        val state = _uiState.value
        if (state is EditProfileUiState.Success) updateProfile(profileId, state.profile.name, state.profile.avatarUrl)
    }

    fun deleteFullProfile(profileId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            profileRepository.deleteFullProfile(profileId)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }
}

class EditProfileViewModelFactory(
    private val application: Application,
    private val profileRepository: ProfileRepository,
    private val profileDao: ProfileDao,
    private val treeDao: TreeDao,
    private val imageDao: ImageDao,
    private val treeApiService: TreeApiService
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditProfileViewModel(application, profileRepository, profileDao, treeDao, imageDao, treeApiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
