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
    private val username: String,
    private val hostUrl: String, // Nouveau : Nécessaire pour normaliser les URLs relatives
    private val authToken: String? = null
) {
    /**
     * Nettoie l'URL pour servir de clé stable dans la base de données.
     */
    private fun getCleanUrl(url: String): String {
        return org.libera.pictotree.utils.FileUtils.getCleanUrl(url)
    }

    /**
     * Assure que l'URL est absolue et pointe sur le bon host (émulateur).
     */
    private fun normalizeUrl(url: String): String {
        return org.libera.pictotree.utils.FileUtils.normalizeUrl(url, hostUrl)
    }

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

                val absoluteUrl = normalizeUrl(remoteUrl)
                val cleanUrl = getCleanUrl(absoluteUrl)

                val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(cleanUrl)
                val userImagesDir = File(context.filesDir, "$username/images")
                if (!userImagesDir.exists()) userImagesDir.mkdirs()
                val file = File(userImagesDir, fileName)
                val localUrl = "file://${file.absolutePath}"

                // Recherche par URL nettoyée
                val existing = imageDao.getImageByRemotePath(cleanUrl)
                if (existing != null && file.exists()) {
                    return@withContext localUrl
                }

                try {
                    // Pour la connexion, on utilise l'URL d'origine (avec cache-buster si présent)
                    val connection = URL(absoluteUrl).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    
                    if (absoluteUrl.contains("/api/v1/mobile/") && !authToken.isNullOrBlank()) {
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
                            finalName = ExternalImageMetadataFetcher.fetchRealName(context, username, cleanUrl, finalName)
                        }

                        connection.inputStream.use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }

                        imageDao.insertImage(
                                ImageEntity(
                                        remotePath = cleanUrl, // On stocke l'URL PROPRE
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
                val absoluteUrl = normalizeUrl(remoteUrl)
                val cleanUrl = getCleanUrl(absoluteUrl)
                
                val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(cleanUrl)
                val userImagesDir = File(context.filesDir, "$username/images")
                if (!userImagesDir.exists()) userImagesDir.mkdirs()

                val file = File(userImagesDir, fileName)
                val localPath = "images/$fileName"

                // 1. Check if already known in DB
                val existing = imageDao.getImageByRemotePath(cleanUrl)
                if (existing != null) {
                    // IF ALREADY LINKED AND FILE EXISTS, WE ARE DONE
                    if (file.exists()) {
                        imageDao.insertTreeImageCrossRef(
                            org.libera.pictotree.data.database.entity.TreeImageCrossRef(treeId, existing.id)
                        )
                        return@withContext
                    }
                    // IF FILE MISSING, WE CONTINUE TO DOWNLOAD BELOW
                }

                var finalName = fileName

                if (!file.exists()) {
                    try {
                        val connection = URL(absoluteUrl).openConnection() as java.net.HttpURLConnection
                        connection.setRequestProperty(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                        )

                        if (absoluteUrl.contains("/api/v1/mobile/") && !authToken.isNullOrBlank()) {
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
                            finalName = ExternalImageMetadataFetcher.fetchRealName(context, username, cleanUrl, finalName)
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
                                        remotePath = cleanUrl, // On stocke l'URL PROPRE
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
