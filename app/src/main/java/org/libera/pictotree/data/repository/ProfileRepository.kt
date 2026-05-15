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
        
        // 1. On normalise le nouvel avatar entrant pour avoir une clé propre
        val cleanNewRemoteUrl = profile.remoteAvatarUrl?.let { 
            FileUtils.getCleanUrl(FileUtils.normalizeUrl(it, hostUrl)) 
        }
        
        // 2. On récupère l'ancienne version avant la mise à jour
        val oldProfile = profileDao.getProfileById(profile.id)
        val oldRemoteUrl = oldProfile?.remoteAvatarUrl // Déjà normalisé car passé par insert/update
        
        val updatedProfile = profile.copy(remoteAvatarUrl = cleanNewRemoteUrl)
        
        // 3. Mise à jour effective (sans DELETE grâce au @Update)
        profileDao.updateProfile(updatedProfile)
        
        // 4. Si le lien distant a changé, on lance le GC pour purger l'ancien fichier s'il est devenu inutile
        if (oldRemoteUrl != null && oldRemoteUrl != cleanNewRemoteUrl) {
            val oldImage = imageDao.getImageByRemotePath(oldRemoteUrl)
            if (oldImage != null) {
                Log.d(TAG, "GC: Avatar changed. Inspecting old image: $oldRemoteUrl")
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
     * ACTION 1 : Retirer un Arbre d'un Profil
     */
    suspend fun removeTreeFromProfile(profileId: Int, treeId: Int) {
        val crossRef = profileDao.getProfileTreeCrossRef(profileId, treeId)
        val orderToDelete = crossRef?.displayOrder ?: 0

        profileDao.deleteProfileTreeCrossRefByIds(profileId, treeId)
        profileDao.reorderAfterDeletion(profileId, orderToDelete)

        if (profileDao.countProfilesForTree(treeId) == 0) {
            val imagesToInspect = imageDao.getImagesForTree(treeId)
            treeDao.getTreeById(treeId)?.let { treeDao.deleteTree(it) }
            runTargetedGarbageCollector(imagesToInspect)
        }
    }

    /**
     * ACTION 2 : Supprimer un Profil Complet
     */
    suspend fun deleteFullProfile(profileId: Int) = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileById(profileId) ?: return@withContext
        val trees = profileDao.getTreesForProfileOrdered(profileId)
        
        val allImagesToInspect = mutableListOf<ImageEntity>()
        
        profile.remoteAvatarUrl?.let { url ->
            imageDao.getImageByRemotePath(url)?.let { allImagesToInspect.add(it) }
        }

        profileDao.deleteProfile(profile)

        trees.forEach { tree ->
            if (profileDao.countProfilesForTree(tree.id) == 0) {
                allImagesToInspect.addAll(imageDao.getImagesForTree(tree.id))
                treeDao.deleteTree(tree)
            }
        }

        runTargetedGarbageCollector(allImagesToInspect.distinctBy { it.id })
    }

    /**
     * MOTEUR DE PURGE CIBLÉ
     */
    internal suspend fun runTargetedGarbageCollector(imagesToCheck: List<ImageEntity>) = withContext(Dispatchers.IO) {
        if (imagesToCheck.isEmpty()) return@withContext
        
        try {
            val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
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
                    try {
                        val actualFile = if (image.localPath.startsWith("/")) File(image.localPath)
                        else File(context.filesDir, "$username/${image.localPath}")

                        if (actualFile.exists()) {
                            if (actualFile.delete()) {
                                purgedFiles++
                                Log.d(TAG, "GC Targeted: Deleted file ${image.localPath}")
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "GC Error: ${e.message}") }
                    imageDao.deleteImageById(image.id)
                }
            }
            Log.i(TAG, "GC Targeted: Finished. $purgedFiles files deleted.")
        } catch (e: Exception) { Log.e(TAG, "GC Targeted Error: ${e.message}") }
    }
}
