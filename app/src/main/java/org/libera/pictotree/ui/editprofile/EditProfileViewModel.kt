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

    fun openTreeSelection(authToken: String) {
        viewModelScope.launch {
            searchTrees(authToken, "", true)
            _showTreeSelectionEvent.send(Unit)
        }
    }

    fun searchTrees(authToken: String, query: String, isPublic: Boolean) {
        viewModelScope.launch {
            try {
                val response = treeApiService.getAvailableTrees(
                    authHeader = "Bearer $authToken",
                    isPublic = isPublic,
                    search = if (query.isBlank()) null else query,
                    limit = 50
                )
                if (response.isSuccessful) {
                    _remoteTrees.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun synchronizeAndImportTree(treeId: Int, profileId: Int, authToken: String, username: String) {
        viewModelScope.launch {
            try {
                // Étape 1 : Récupérer "l'ADN" de l'Arbre via Retrofit
                val response = treeApiService.getTree("Bearer $authToken", treeId)
                if (response.isSuccessful && response.body() != null) {
                    val fullTree = response.body()!!
                    
                    // Étape 2 : Engine d'Importation Hachée d'images
                    val engine = ImageSyncEngine(getApplication(), imageDao, username)
                    fullTree.rootNode?.let { engine.syncImagesFromNode(it) }
                    
                    // Étape 3 : Insérer l'Entité de Base (Arbre Textuel)
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
                    
                    // Étape 4 : Lier à ce Profil spécifique (En le mettant tout en bas de la liste)
                    val maxOrder = profileDao.getMaxDisplayOrderForProfile(profileId) ?: -1
                    val crossRef = ProfileTreeCrossRef(profileId, treeEntity.id, maxOrder + 1)
                    profileDao.insertProfileTreeCrossRef(crossRef)
                    
                    // Étape 5 : Rafraichir le Flow Dynamique Android
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
