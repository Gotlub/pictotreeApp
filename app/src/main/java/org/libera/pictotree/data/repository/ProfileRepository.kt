package org.libera.pictotree.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.entity.ImageEntity
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.utils.FileUtils
import java.io.File

class ProfileRepository(
    private val context: Context,
    private val profileDao: ProfileDao,
    private val treeDao: TreeDao,
    private val imageDao: ImageDao,
    private val username: String
) {

    private val TAG = "ProfileRepository"

    val allProfiles: Flow<List<Profile>> = profileDao.getAllProfilesFlow()

    /**
     * Insertion avec nettoyage forcé de l'URL.
     */
    suspend fun insertProfile(profile: Profile): Long {
        val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
        val cleanRemoteUrl = profile.remoteAvatarUrl?.let { 
            FileUtils.getCleanUrl(FileUtils.normalizeUrl(it, hostUrl)) 
        }
        return profileDao.insertProfile(profile.copy(remoteAvatarUrl = cleanRemoteUrl))
    }

    /**
     * Mise à jour avec nettoyage ciblé de l'avatar si changement.
     */
    suspend fun updateProfile(profile: Profile) {
        val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
        val cleanRemoteUrl = profile.remoteAvatarUrl?.let { 
            FileUtils.getCleanUrl(FileUtils.normalizeUrl(it, hostUrl)) 
        }
        
        val updatedProfile = profile.copy(remoteAvatarUrl = cleanRemoteUrl)
        val oldProfile = profileDao.getProfileById(profile.id)
        val oldAvatarUrl = oldProfile?.remoteAvatarUrl
        Log.d(TAG, "GC updateProfile old : $oldAvatarUrl  updatedProfile ava : ${updatedProfile.avatarUrl}   &oldProfile.remote  ${oldProfile?.avatarUrl} ")
        profileDao.updateProfile(updatedProfile)
        // Si l'avatar a changé, on nettoie l'ancien s'il est orphelin
        if (oldAvatarUrl != null && oldProfile.avatarUrl != updatedProfile.avatarUrl) {
            Log.d(TAG, "GC updateProfile old : $oldAvatarUrl  updated : ${updatedProfile.remoteAvatarUrl}")
            val oldImage = imageDao.getImageByRemotePath(oldAvatarUrl)
            if (oldImage != null) {
                Log.d(TAG, "GC oldImage  not null")
                runTargetedGarbageCollector(listOf(oldImage))
            }
        }
    }

    suspend fun insertProfileTreeCrossRef(ref: org.libera.pictotree.data.database.entity.ProfileTreeCrossRef) {
        profileDao.insertProfileTreeCrossRef(ref)
    }

    suspend fun getTreesWithColorForProfile(profileId: Int) = profileDao.getTreesWithColorForProfile(profileId)
    suspend fun getTreesForProfileOrdered(profileId: Int) = profileDao.getTreesForProfileOrdered(profileId)

    /**
     * ACTION 1 : Retirer un Arbre d'un Profil (Optimisé)
     */
    suspend fun removeTreeFromProfile(profileId: Int, treeId: Int) {
        val crossRef = profileDao.getProfileTreeCrossRef(profileId, treeId)
        val orderToDelete = crossRef?.displayOrder ?: 0

        profileDao.deleteProfileTreeCrossRefByIds(profileId, treeId)
        profileDao.reorderAfterDeletion(profileId, orderToDelete)

        if (profileDao.countProfilesForTree(treeId) == 0) {
            // ÉTAPE 1 : Récupérer les images de l'arbre avant destruction
            val imagesToInspect = imageDao.getImagesForTree(treeId)
            
            // ÉTAPE 2 : Détruire l'arbre
            treeDao.getTreeById(treeId)?.let { treeDao.deleteTree(it) }
            
            // ÉTAPE 3 : Purge ciblée
            runTargetedGarbageCollector(imagesToInspect)
            Log.d(TAG, "GC Targeted: Tree $treeId and its unique images purged.")
        }
    }

    /**
     * ACTION 2 : Supprimer un Profil Complet (Optimisé)
     */
    suspend fun deleteFullProfile(profileId: Int) = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileById(profileId) ?: return@withContext
        val trees = profileDao.getTreesForProfileOrdered(profileId)
        
        val allImagesToInspect = mutableListOf<ImageEntity>()
        
        // 1. Collecter l'avatar actuel
        profile.remoteAvatarUrl?.let { url ->
            imageDao.getImageByRemotePath(url)?.let { allImagesToInspect.add(it) }
        }

        // 2. Supprimer le profil
        profileDao.deleteProfile(profile)
        Log.d(TAG, "PURGE: Profile $profileId deleted.")

        // 3. Collecter les images des arbres orphelins avant suppression
        trees.forEach { tree ->
            if (profileDao.countProfilesForTree(tree.id) == 0) {
                allImagesToInspect.addAll(imageDao.getImagesForTree(tree.id))
                treeDao.deleteTree(tree)
            }
        }

        // 4. Lancer une seule fois le GC ciblé sur la liste unique
        runTargetedGarbageCollector(allImagesToInspect.distinctBy { it.id })
    }

    /**
     * MOTEUR DE PURGE CIBLÉ (Performance ++ )
     * Vérifie uniquement les images fournies en paramètre.
     */
    private suspend fun runTargetedGarbageCollector(imagesToCheck: List<ImageEntity>) = withContext(Dispatchers.IO) {
        if (imagesToCheck.isEmpty()) return@withContext
        
        try {
            val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
            
            // Collecte des références vivantes (Normalisées pour comparaison)
            val usedAvatarUrls = profileDao.getAllUsedAvatarUrls().map { url ->
                FileUtils.getCleanUrl(FileUtils.normalizeUrl(url, hostUrl))
            }.toSet()

            val usedTreeRootUrls = treeDao.getAllTreesSync().mapNotNull { it.rootUrl }.map { url ->
                FileUtils.getCleanUrl(FileUtils.normalizeUrl(url, hostUrl))
            }.toSet()

            var purgedFiles = 0

            imagesToCheck.forEach { image ->
                val cleanImagePath = FileUtils.getCleanUrl(FileUtils.normalizeUrl(image.remotePath, hostUrl))

                val treeRefs = imageDao.countImageReferences(image.id)
                val isUsedAsAvatar = usedAvatarUrls.contains(cleanImagePath)
                val isUsedAsTreeRoot = usedTreeRootUrls.contains(cleanImagePath)

                if (treeRefs == 0 && !isUsedAsAvatar && !isUsedAsTreeRoot) {
                    // PURGE
                    try {
                        val actualFile = if (image.localPath.startsWith("/")) {
                            File(image.localPath)
                        } else {
                            File(context.filesDir, "$username/${image.localPath}")
                        }

                        if (actualFile.exists()) {
                            if (actualFile.delete()) {
                                purgedFiles++
                                Log.d(TAG, "GC Targeted: Deleted file ${image.localPath}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "GC Targeted: Failed to delete physical file: ${e.message}")
                    }

                    imageDao.deleteImageById(image.id)
                    Log.d(TAG, "GC Targeted: DB entry removed for: $cleanImagePath")
                }
            }
            Log.i(TAG, "GC Targeted: Finished. $purgedFiles files deleted among ${imagesToCheck.size} inspected.")
        } catch (e: Exception) {
            Log.e(TAG, "GC Targeted Error: ${e.message}")
        }
    }
}
