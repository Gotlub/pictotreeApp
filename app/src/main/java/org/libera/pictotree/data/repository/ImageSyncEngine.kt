package org.libera.pictotree.data.repository

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.entity.ImageEntity
import org.libera.pictotree.network.dto.TreeNodeDTO

class ImageSyncEngine(
        private val context: Context,
        private val imageDao: ImageDao,
        private val username: String, // Isoler hermétiquement le stockage par utilisateur
        private val authToken: String? = null // Conservé pour compatibilité URL manuelle mais optionnel
) {
    suspend fun syncImagesFromNode(node: TreeNodeDTO, treeId: Int) {
        if (node.imageUrl.isNotBlank()) {
            downloadAndHashImage(node.imageUrl, treeId)
        }
        for (child in node.children) {
            syncImagesFromNode(child, treeId)
        }
    }

    suspend fun downloadSingleImage(remoteUrl: String, name: String? = null): String? =
            withContext(Dispatchers.IO) {
                if (remoteUrl.isBlank()) return@withContext null
                if (remoteUrl.startsWith("file://") || remoteUrl.startsWith("color:"))
                        return@withContext remoteUrl

                val fileName =
                        org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(remoteUrl)
                val userImagesDir = File(context.filesDir, "$username/images")
                if (!userImagesDir.exists()) userImagesDir.mkdirs()
                val file = File(userImagesDir, fileName)
                val localUrl = "file://${file.absolutePath}"

                val existing = imageDao.getImageByRemotePath(remoteUrl)
                if (existing != null && file.exists()) {
                    return@withContext localUrl
                }

                try {
                    val connection = URL(remoteUrl).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    
                    // Note: Si on utilise HttpURLConnection au lieu de Retrofit, l'intercepteur global ne s'applique pas.
                    // On garde donc la logique manuelle ici pour les téléchargements directs de fichiers.
                    if (remoteUrl.contains("/api/v1/mobile/") && authToken != null) {
                        connection.setRequestProperty("Authorization", "Bearer $authToken")
                    }
                    connection.connect()

                    if (connection.responseCode in 200..299) {
                        var finalName = name ?: fileName
                        val headerDesc = connection.getHeaderField("X-Image-Description")

                        if (!headerDesc.isNullOrBlank()) {
                            try {
                                val decoded = java.net.URLDecoder.decode(headerDesc, "UTF-8")
                                if (decoded.isNotBlank()) finalName = decoded
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            finalName = ExternalImageMetadataFetcher.fetchRealName(context, username, remoteUrl, finalName)
                        }

                        connection.inputStream.use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }

                        imageDao.insertImage(
                                ImageEntity(
                                        remotePath = remoteUrl,
                                        localPath = "images/$fileName",
                                        name = finalName
                                )
                        )
                        return@withContext localUrl
                    } else if (connection.responseCode == 401) {
                         org.libera.pictotree.utils.AuthEvents.triggerLogout()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return@withContext null
            }

    private suspend fun downloadAndHashImage(remoteUrl: String, treeId: Int) =
            withContext(Dispatchers.IO) {
                val fileName =
                        org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(remoteUrl)

                val existing = imageDao.getImageByRemotePath(remoteUrl)
                if (existing != null) {
                    imageDao.insertTreeImageCrossRef(
                            org.libera.pictotree.data.database.entity.TreeImageCrossRef(
                                    treeId,
                                    existing.id
                            )
                    )
                    return@withContext
                }

                val userImagesDir = File(context.filesDir, "$username/images")
                if (!userImagesDir.exists()) userImagesDir.mkdirs()

                val file = File(userImagesDir, fileName)
                val localPath = "images/$fileName"

                var finalName = fileName

                if (!file.exists()) {
                    try {
                        val connection =
                                URL(remoteUrl).openConnection() as java.net.HttpURLConnection
                        connection.setRequestProperty(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                        )

                        if (remoteUrl.contains("/api/v1/mobile/") && authToken != null) {
                            connection.setRequestProperty("Authorization", "Bearer $authToken")
                        }

                        connection.connect()

                        if (connection.responseCode == 401) {
                            org.libera.pictotree.utils.AuthEvents.triggerLogout()
                            return@withContext
                        }

                        val headerDesc = connection.getHeaderField("X-Image-Description")
                        if (!headerDesc.isNullOrBlank()) {
                            try {
                                val decoded = java.net.URLDecoder.decode(headerDesc, "UTF-8")
                                if (decoded.isNotBlank()) finalName = decoded
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            finalName = ExternalImageMetadataFetcher.fetchRealName(context, username, remoteUrl, finalName)
                        }

                        if (connection.responseCode in 200..299) {
                            connection.inputStream.use { input ->
                                FileOutputStream(file).use { output -> input.copyTo(output) }
                            }
                        } else {
                            return@withContext
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@withContext
                    }
                }

                val newImageId =
                        imageDao.insertImage(
                                ImageEntity(
                                        remotePath = remoteUrl,
                                        localPath = localPath,
                                        name = finalName
                                )
                        )
                imageDao.insertTreeImageCrossRef(
                        org.libera.pictotree.data.database.entity.TreeImageCrossRef(
                                treeId,
                                newImageId.toInt()
                        )
                )
            }
}
