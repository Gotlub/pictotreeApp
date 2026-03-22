package org.libera.pictotree.ui.editprofile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.entity.ProfileTreeCrossRef
import org.libera.pictotree.data.database.entity.TreeEntity
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

    fun loadProfile(profileId: Int) {
        viewModelScope.launch {
            try {
                val profile = profileDao.getProfileById(profileId)
                if (profile != null) {
                    val trees = profileDao.getTreesForProfileOrdered(profileId)
                    _uiState.value = EditProfileUiState.Success(profile, trees)
                } else {
                    _uiState.value = EditProfileUiState.Error("Profile completely missing from DB")
                }
            } catch(e: Exception) {
               _uiState.value = EditProfileUiState.Error(e.message ?: "Unknown database error loading profile")
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
                        lastSync = System.currentTimeMillis()
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
