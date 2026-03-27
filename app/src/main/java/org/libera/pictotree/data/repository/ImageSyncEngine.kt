package org.libera.pictotree.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.entity.ImageEntity
import org.libera.pictotree.network.dto.TreeNodeDTO
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class ImageSyncEngine(
    private val context: Context,
    private val imageDao: ImageDao,
    private val username: String, // Isoler hermétiquement le stockage par utilisateur (Cahier des charges)
    private val authToken: String // Injection du token JWT pour télécharger les images internes
) {
    suspend fun syncImagesFromNode(node: TreeNodeDTO, treeId: Int) {
        if (node.imageUrl.isNotBlank()) {
            downloadAndHashImage(node.imageUrl, treeId)
        }
        for (child in node.children) {
            syncImagesFromNode(child, treeId)
        }
    }

    private suspend fun downloadAndHashImage(remoteUrl: String, treeId: Int) = withContext(Dispatchers.IO) {
        val hash = hashUrlSha256(remoteUrl)
        val ext = remoteUrl.substringAfterLast('.', "png")
        // Génération d'un anti-doublon universel : a5b3f...c12.png
        val fileName = "$hash.$ext" 
        
        // Si l'application possède DÉJÀ cette empreinte, on la lie directement
        val existing = imageDao.getImageByRemotePath(remoteUrl)
        if (existing != null) {
            imageDao.insertTreeImageCrossRef(org.libera.pictotree.data.database.entity.TreeImageCrossRef(treeId, existing.id))
            return@withContext
        }

        // Scaffold de l'architecture dossier
        val userImagesDir = File(context.filesDir, "$username/images")
        if (!userImagesDir.exists()) userImagesDir.mkdirs()
        
        val file = File(userImagesDir, fileName)
        val localPath = "images/$fileName" // On sauve le chemin de façon relative pour SQLite
        
        if (!file.exists()) {
            try {
                val connection = URL(remoteUrl).openConnection() as java.net.HttpURLConnection
                // Émuler un navigateur pour bypasser les blocages CloudFlare (ex: Arasaac)
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                
                // Passer le token JWT si l'image provient de notre API locale protégée
                if (remoteUrl.contains("/api/v1/mobile/")) {
                    connection.setRequestProperty("Authorization", "Bearer $authToken")
                }
                
                connection.connect()
                
                if (connection.responseCode in 200..299) {
                    connection.inputStream.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    return@withContext
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext
            }
        }
        
        // Finalement, on met la Base de données à jour
        val newImageId = imageDao.insertImage(ImageEntity(
            remotePath = remoteUrl,
            localPath = localPath,
            name = fileName
        ))
        imageDao.insertTreeImageCrossRef(org.libera.pictotree.data.database.entity.TreeImageCrossRef(treeId, newImageId.toInt()))
    }

    private fun hashUrlSha256(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
