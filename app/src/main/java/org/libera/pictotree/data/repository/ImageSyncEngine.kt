package org.libera.pictotree.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.entity.ImageEntity
import org.libera.pictotree.network.dto.TreeNodeDTO
import org.libera.pictotree.utils.FileUtils.getCleanUrl
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class SyncResult(val total: Int, val errors: Int)

class ImageSyncEngine(
    private val context: Context,
    private val imageDao: ImageDao,
    private val username: String,
    private val hostUrl: String,
    private val authToken: String? = null
) {
    private val TAG = "ImageSyncEngine"

    private fun normalizeUrl(url: String): String {
        return org.libera.pictotree.utils.FileUtils.normalizeUrl(url, hostUrl)
    }

    /**
     * Synchronise les images d'un noeud et de ses enfants.
     * @return SyncResult contenant le nombre total d'images traitées et le nombre d'erreurs.
     */
    suspend fun syncImagesFromNode(node: TreeNodeDTO, treeId: Int): SyncResult {
        var total = 0
        var errors = 0
        
        if (node.imageUrl.isNotBlank()) {
            total++
            val success = downloadAndHashImage(node.imageUrl, treeId, node.label, node.description)
            if (!success) errors++
        }
        
        for (child in node.children) {
            val childResult = syncImagesFromNode(child, treeId)
            total += childResult.total
            errors += childResult.errors
        }
        
        return SyncResult(total, errors)
    }

    suspend fun downloadSingleImage(remoteUrl: String, name: String? = null, description: String? = null): String? =
            withContext(Dispatchers.IO) {
                if (remoteUrl.isBlank()) return@withContext null
                if (remoteUrl.startsWith("file://") || remoteUrl.startsWith("color:"))
                        return@withContext remoteUrl

                val absoluteUrl = normalizeUrl(remoteUrl)
                val cleanUrl = getCleanUrl(absoluteUrl)

                val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(cleanUrl)
                val userImagesDir = File(context.filesDir, "$username/images")
                if (!userImagesDir.exists()) userImagesDir.mkdirs()
                val file = File(userImagesDir, fileName)
                val localUrl = "file://${file.absolutePath}"

                val existing = imageDao.getImageByRemotePath(cleanUrl)
                if (existing != null && file.exists()) {
                    return@withContext localUrl
                }

                try {
                    val connection = URL(absoluteUrl).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    
                    if (absoluteUrl.contains("/api/v1/mobile/") && !authToken.isNullOrBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer $authToken")
                    }
                    connection.connect()

                    if (connection.responseCode in 200..299) {
                        var finalName = name ?: fileName
                        var finalDesc = description
                        val headerDesc = connection.getHeaderField("X-Image-Description")

                        if (!headerDesc.isNullOrBlank()) {
                            try {
                                val decoded = java.net.URLDecoder.decode(headerDesc, "UTF-8")
                                if (decoded.isNotBlank()) {
                                    finalName = decoded
                                    finalDesc = decoded
                                }
                            } catch (e: Exception) {}
                        } else if (name == null) {
                            finalName = ExternalImageMetadataFetcher.fetchRealName(context, username, cleanUrl, finalName)
                        }

                        connection.inputStream.use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }

                        if (existing == null) {
                            imageDao.insertImage(
                                    ImageEntity(
                                            remotePath = cleanUrl,
                                            localPath = "images/$fileName",
                                            name = finalName,
                                            description = finalDesc
                                    )
                            )
                        }
                        return@withContext localUrl
                    } else if (connection.responseCode == 401) {
                         org.libera.pictotree.utils.AuthEvents.triggerLogout()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed for $cleanUrl: ${e.message}")
                }
                return@withContext null
            }

    /**
     * @return true si l'image est présente localement (déjà là ou téléchargée), false sinon.
     */
    private suspend fun downloadAndHashImage(remoteUrl: String, treeId: Int, name: String? = null, description: String? = null): Boolean =
            withContext(Dispatchers.IO) {
                val absoluteUrl = normalizeUrl(remoteUrl)
                val cleanUrl = getCleanUrl(absoluteUrl)
                
                val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(cleanUrl)
                val userImagesDir = File(context.filesDir, "$username/images")
                if (!userImagesDir.exists()) userImagesDir.mkdirs()

                val file = File(userImagesDir, fileName)
                val localPath = "images/$fileName"

                val existing = imageDao.getImageByRemotePath(cleanUrl)
                
                // Si l'entrée existe ET le fichier existe, on lie juste à l'arbre et on skip
                if (existing != null && file.exists()) {
                    imageDao.insertTreeImageCrossRef(
                        org.libera.pictotree.data.database.entity.TreeImageCrossRef(treeId, existing.id)
                    )
                    return@withContext true
                }

                // Sinon (soit pas de fichier, soit pas de BDD), on télécharge
                try {
                    val connection = URL(absoluteUrl).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    if (absoluteUrl.contains("/api/v1/mobile/") && !authToken.isNullOrBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer $authToken")
                    }

                    connection.connect()

                    if (connection.responseCode == 401) {
                        org.libera.pictotree.utils.AuthEvents.triggerLogout()
                        return@withContext false
                    }

                    if (connection.responseCode in 200..299) {
                        var finalName = name ?: fileName
                        var finalDesc = description

                        val headerDesc = connection.getHeaderField("X-Image-Description")
                        if (!headerDesc.isNullOrBlank()) {
                            try {
                                val decoded = java.net.URLDecoder.decode(headerDesc, "UTF-8")
                                if (decoded.isNotBlank()) {
                                    finalName = decoded
                                    finalDesc = decoded
                                }
                            } catch (e: Exception) {}
                        } else if (name == null) {
                            finalName = ExternalImageMetadataFetcher.fetchRealName(context, username, cleanUrl, finalName)
                        }

                        connection.inputStream.use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }

                        val imageId = if (existing != null) {
                            // On a l'entrée BDD mais le fichier manquait : on met à jour
                            imageDao.updateImage(existing.copy(name = finalName, description = finalDesc))
                            existing.id
                        } else {
                            // Nouvelle image complète
                            imageDao.insertImage(
                                ImageEntity(
                                    remotePath = cleanUrl,
                                    localPath = localPath,
                                    name = finalName,
                                    description = finalDesc
                                )
                            ).toInt()
                        }

                        imageDao.insertTreeImageCrossRef(
                            org.libera.pictotree.data.database.entity.TreeImageCrossRef(treeId, imageId)
                        )
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync image $cleanUrl: ${e.message}")
                }
                return@withContext false
            }
}
