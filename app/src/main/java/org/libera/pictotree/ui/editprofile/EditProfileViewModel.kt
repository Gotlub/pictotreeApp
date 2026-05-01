package org.libera.pictotree.ui.editprofile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.entity.ProfileTreeCrossRef
import org.libera.pictotree.data.database.entity.TreeEntity
import org.libera.pictotree.network.dto.TreeMetadataDTO
import org.libera.pictotree.data.repository.ImageSyncEngine
import org.libera.pictotree.network.TreeApiService

class EditProfileViewModel(
    application: Application,
    private val profileDao: ProfileDao,
    private val treeDao: TreeDao,
    private val imageDao: ImageDao,
    private val treeApiService: TreeApiService
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _showTreeSelectionEvent = Channel<Unit>(Channel.BUFFERED)
    val showTreeSelectionEvent = _showTreeSelectionEvent.receiveAsFlow()

    private val _remoteTrees = MutableStateFlow<List<TreeMetadataDTO>>(emptyList())
    val remoteTrees: StateFlow<List<TreeMetadataDTO>> = _remoteTrees.asStateFlow()

    fun loadProfile(profileId: Int) {
        viewModelScope.launch {
            try {
                val profile = profileDao.getProfileById(profileId)
                if (profile != null) {
                    val trees = profileDao.getTreesForProfileOrdered(profileId)
                    val sessionManager = SessionManager(getApplication())
                    val username = sessionManager.getUsername()
                    
                    val uiModels = trees.map { tree ->
                        var localPath: String? = null
                        try {
                            val url = tree.rootUrl
                            if (!url.isNullOrEmpty()) {
                                // Rechercher l'Image téléchargée en associant l'URL distante
                                val imageEntity = imageDao.getImageByRemotePath(url)
                                if (imageEntity != null && username != null) {
                                    val file = java.io.File(getApplication<Application>().filesDir, "$username/${imageEntity.localPath}")
                                    if (file.exists()) {
                                        localPath = file.absolutePath
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        ProfileTreeUiModel(tree, localPath)
                    }
                    _uiState.value = EditProfileUiState.Success(profile, uiModels)
                } else {
                    _uiState.value = EditProfileUiState.Error("Profile completely missing from DB")
                }
            } catch(e: Exception) {
               _uiState.value = EditProfileUiState.Error(e.message ?: "Unknown database error loading profile")
            }
        }
    }

    fun openTreeSelection() {
        viewModelScope.launch {
            searchTrees("", true)
            _showTreeSelectionEvent.send(Unit)
        }
    }

    private var currentRemotePage = 1
    private var isRemoteLastPage = false
    private var isRemoteLoadingMore = false
    private var currentRemoteQuery = ""
    private var currentRemoteIsPublic = true

    fun searchTrees(query: String, isPublic: Boolean) {
        currentRemoteQuery = query
        currentRemoteIsPublic = isPublic
        currentRemotePage = 1
        isRemoteLastPage = false
        
        viewModelScope.launch {
            try {
                val response = treeApiService.getAvailableTrees(
                    isPublic = isPublic,
                    search = if (query.isBlank()) null else query,
                    page = currentRemotePage,
                    limit = 50
                )
                if (response.isSuccessful) {
                    val items = response.body() ?: emptyList()
                    _remoteTrees.value = items
                    if (items.size < 50) isRemoteLastPage = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadMoreTrees() {
        if (isRemoteLoadingMore || isRemoteLastPage) return
        isRemoteLoadingMore = true
        currentRemotePage++
        
        viewModelScope.launch {
            try {
                val response = treeApiService.getAvailableTrees(
                    isPublic = currentRemoteIsPublic,
                    search = if (currentRemoteQuery.isBlank()) null else currentRemoteQuery,
                    page = currentRemotePage,
                    limit = 50
                )
                if (response.isSuccessful) {
                    val items = response.body() ?: emptyList()
                    if (items.isEmpty()) {
                        isRemoteLastPage = true
                    } else {
                        _remoteTrees.value = _remoteTrees.value + items
                        if (items.size < 50) isRemoteLastPage = true
                    }
                } else {
                    currentRemotePage--
                }
            } catch (e: Exception) {
                e.printStackTrace()
                currentRemotePage--
            } finally {
                isRemoteLoadingMore = false
            }
        }
    }

    fun synchronizeAndImportTree(treeId: Int, profileId: Int, username: String) {
        val sessionManager = SessionManager(getApplication())
        val authToken = sessionManager.getToken() ?: ""

        viewModelScope.launch {
            try {
                // Étape 1 : Récupérer "l'ADN" de l'Arbre via Retrofit (Token automatique via Interceptor)
                val response = treeApiService.getTree(treeId)
                if (response.isSuccessful && response.body() != null) {
                    val fullTree = response.body()!!
                    
                    // Étape 2 : Insérer l'Entité de Base
                    val jsonPayload = Gson().toJson(fullTree)
                    val treeEntity = TreeEntity(
                        id = fullTree.treeId,
                        name = fullTree.name,
                        jsonPayload = jsonPayload,
                        isPublic = false,
                        lastSync = System.currentTimeMillis(),
                        rootUrl = fullTree.rootNode?.imageUrl
                    )
                    treeDao.insertTree(treeEntity)
                    
                    // Étape 3 : Engine d'Importation (On garde authToken ici car ImageSyncEngine utilise HttpURLConnection manuel)
                    val engine = ImageSyncEngine(getApplication(), imageDao, username, authToken)
                    fullTree.rootNode?.let { engine.syncImagesFromNode(it, fullTree.treeId) }
                    
                    // Étape 4 : Lier à ce Profil
                    val maxOrder = profileDao.getMaxDisplayOrderForProfile(profileId) ?: -1
                    val crossRef = ProfileTreeCrossRef(profileId, treeEntity.id, maxOrder + 1)
                    profileDao.insertProfileTreeCrossRef(crossRef)
                    
                    loadProfile(profileId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteTree(tree: TreeEntity) {
        viewModelScope.launch {
            try {
                val sessionManager = SessionManager(getApplication())
                val username = sessionManager.getUsername() ?: return@launch
                val currentProfile = (_uiState.value as? EditProfileUiState.Success)?.profile ?: return@launch
                
                // 1. SUPPRIMER LE LIEN avec le Profil actuel (et surtout pas l'Arbre entier !)
                profileDao.deleteProfileTreeCrossRefByIds(currentProfile.id, tree.id)
                
                // 2. Compter combien d'AUTRES profils utilisent encore cet arbre
                val remainingProfiles = profileDao.countProfilesForTree(tree.id)
                
                if (remainingProfiles == 0) {
                    // C'EST LE DERNIER PROFIL À UTILISER CET ARBRE ! On le détruit.
                    val images = imageDao.getImagesForTree(tree.id)
                    treeDao.deleteTree(tree) // Cascade détruit ses TreeImageCrossRef
                    
                    val filesDir = java.io.File(getApplication<Application>().filesDir, username)
                    images.forEach { image ->
                        val refCount = imageDao.countImageReferences(image.id)
                        if (refCount == 0) {
                            // C'est la dernière arborescence à utiliser cette image ! Purge intégrale.
                            imageDao.deleteImageById(image.id)
                            val physicalFile = java.io.File(filesDir, image.localPath)
                            if (physicalFile.exists()) {
                                physicalFile.delete()
                            }
                        }
                    }
                }
                
                // 4. Reload Profile
                loadProfile(currentProfile.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateTreesOrder(profileId: Int, reorderedTrees: List<TreeEntity>) {
        viewModelScope.launch {
            try {
                val crossRefs = reorderedTrees.mapIndexed { index, tree ->
                    ProfileTreeCrossRef(profileId, tree.id, index)
                }
                profileDao.updateProfileTreeCrossRefs(crossRefs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateProfile(profileId: Int, newName: String, avatarUrl: String?) {
        viewModelScope.launch {
            try {
                val sessionManager = SessionManager(getApplication())
                val username = sessionManager.getUsername() ?: "default"
                val token = sessionManager.getToken() ?: ""
                
                var finalAvatarUrl = avatarUrl
                
                // Si c'est une image distante (Base ou Arasaac), on la télécharge en local
                if (avatarUrl != null && (avatarUrl.startsWith("http") || avatarUrl.contains("/api/v1/mobile/"))) {
                    val engine = ImageSyncEngine(getApplication(), imageDao, username, token)
                    val localUrl = engine.downloadSingleImage(avatarUrl)
                    if (localUrl != null) {
                        finalAvatarUrl = localUrl
                    }
                }

                val currentProfile = profileDao.getProfileById(profileId)
                if (currentProfile != null) {
                    val updated = currentProfile.copy(name = newName, avatarUrl = finalAvatarUrl)
                    profileDao.insertProfile(updated)
                    loadProfile(profileId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class EditProfileViewModelFactory(
    private val application: Application,
    private val profileDao: ProfileDao,
    private val treeDao: TreeDao,
    private val imageDao: ImageDao,
    private val treeApiService: TreeApiService
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditProfileViewModel(application, profileDao, treeDao, imageDao, treeApiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
