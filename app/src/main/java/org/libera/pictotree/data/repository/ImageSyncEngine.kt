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

class UnauthorizedException(message: String = "Session expired (401)") : Exception(message)

data class SyncResult(val total: Int, val errors: Int)

class ImageSyncEngine(
    private val context: Context,
    private val imageDao: ImageDao,
    private val username: String,
    private val hostUrl: String,
    private val authToken: String? = null
) {
    companion object {
        private const val TAG = "ImageSyncEngine"
        private const val CONNECTION_TIMEOUT_MS = 15000
    }

    private fun shouldInjectToken(absoluteUrl: String): Boolean {
        if (authToken.isNullOrBlank()) return false
        return try {
            val requestHost = URL(absoluteUrl).host
            val hostHost = URL(hostUrl).host
            requestHost.equals(hostHost, ignoreCase = true) &&
                    (absoluteUrl.contains("/api/v1/mobile/") ||
                     absoluteUrl.contains("/uploads/") ||
                     absoluteUrl.contains("/pictograms/"))
        } catch (e: Exception) {
            false
        }
    }

    private fun normalizeUrl(url: String): String {
        return org.libera.pictotree.utils.FileUtils.normalizeUrl(url, hostUrl)
    }

    /**
     * Synchronise les images d'un noeud et de ses enfants.
     * @return SyncResult contenant uniquement les vraies images téléchargées ou vérifiées.
     */
    suspend fun syncImagesFromNode(node: TreeNodeDTO, treeId: Int): SyncResult {
        var total = 0
        var errors = 0
        
        val url = node.imageUrl
        // On ne traite QUE les vraies URLs distantes (pas de color:, pas de file:// déjà local)
        if (url.isNotBlank() && !url.startsWith("color:") && !url.startsWith("file://")) {
            total++
            val success = downloadAndHashImage(url, treeId, node.label, node.description)
            if (!success) {
                errors++
                Log.w(TAG, "Sync error for image: $url")
            }
        }
        
        for (child in node.children) {
            val childResult = syncImagesFromNode(child, treeId)
            total += childResult.total
            errors += childResult.errors
        }
        
        return SyncResult(total, errors)
    }

    private fun downloadFileAtomically(connection: java.net.HttpURLConnection, tempFile: File, destinationFile: File) {
        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        if (destinationFile.exists()) {
            destinationFile.delete()
        }
        if (!tempFile.renameTo(destinationFile)) {
            throw java.io.IOException("Failed to rename temporary download file to ${destinationFile.absolutePath}")
        }
    }

    private fun decodeDescriptionHeader(headerDesc: String?): String? {
        if (headerDesc.isNullOrBlank()) return null
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                java.net.URLDecoder.decode(headerDesc, java.nio.charset.StandardCharsets.UTF_8)
            } else {
                @Suppress("DEPRECATION")
                java.net.URLDecoder.decode(headerDesc, "UTF-8")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode X-Image-Description header: ${e.message}")
            null
        }
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

                var connection: java.net.HttpURLConnection? = null
                try {
                    connection = URL(absoluteUrl).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connectTimeout = CONNECTION_TIMEOUT_MS
                    connection.readTimeout = CONNECTION_TIMEOUT_MS
                    
                    if (shouldInjectToken(absoluteUrl)) {
                        connection.setRequestProperty("Authorization", "Bearer $authToken")
                    }
                    connection.connect()

                    if (connection.responseCode in 200..299) {
                        var finalName = name ?: fileName
                        var finalDesc = description
                        val headerDesc = connection.getHeaderField("X-Image-Description")

                        val decoded = decodeDescriptionHeader(headerDesc)
                        if (!decoded.isNullOrBlank()) {
                            finalName = decoded
                            finalDesc = decoded
                        } else if (name == null) {
                            finalName = ExternalImageMetadataFetcher.fetchRealName(context, username, cleanUrl, finalName)
                        }

                        val tempFile = File.createTempFile("download_${fileName}_", ".tmp", userImagesDir)
                        try {
                            downloadFileAtomically(connection, tempFile, file)
                        } finally {
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                        }

                        if (existing == null) {
                            val insertedId = imageDao.insertImage(
                                     ImageEntity(
                                              remotePath = cleanUrl,
                                              localPath = "images/$fileName",
                                              name = finalName,
                                              description = finalDesc
                                     )
                            )
                            if (insertedId == -1L) {
                                throw java.io.IOException("Failed to insert image entity into database")
                            }
                        } else {
                            imageDao.updateImage(existing.copy(name = finalName, description = finalDesc))
                        }
                        return@withContext localUrl
                    } else if (connection.responseCode == 401) {
                        throw UnauthorizedException("Session expired (401)")
                    }
                } catch (e: UnauthorizedException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed for $cleanUrl: ${e.message}")
                } finally {
                    connection?.disconnect()
                }
                return@withContext null
            }

    /**
     * @return true si l'image est présente localement (déjà là ou téléchargée), false sinon.
     */
    private suspend fun downloadAndHashImage(remoteUrl: String, treeId: Int, name: String? = null, description: String? = null): Boolean =
            withContext(Dispatchers.IO) {
                // SÉCURITÉ SUPPLÉMENTAIRE : On ignore les protocoles non-HTTP
                if (remoteUrl.startsWith("color:") || remoteUrl.startsWith("file://")) return@withContext true

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
                    if ((name != null && existing.name != name) || (description != null && existing.description != description)) {
                        imageDao.updateImage(existing.copy(
                            name = name ?: existing.name,
                            description = description ?: existing.description
                        ))
                    }
                    return@withContext true
                }

                // Sinon (soit pas de fichier, soit pas de BDD), on télécharge
                var connection: java.net.HttpURLConnection? = null
                try {
                    connection = URL(absoluteUrl).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connectTimeout = CONNECTION_TIMEOUT_MS
                    connection.readTimeout = CONNECTION_TIMEOUT_MS

                    if (shouldInjectToken(absoluteUrl)) {
                        connection.setRequestProperty("Authorization", "Bearer $authToken")
                    }

                    connection.connect()

                    if (connection.responseCode == 401) {
                        throw UnauthorizedException("Session expired (401)")
                    }

                    if (connection.responseCode in 200..299) {
                        var finalName = name ?: fileName
                        var finalDesc = description

                        val headerDesc = connection.getHeaderField("X-Image-Description")
                        val decoded = decodeDescriptionHeader(headerDesc)
                        if (!decoded.isNullOrBlank()) {
                            finalName = decoded
                            finalDesc = decoded
                        } else if (name == null) {
                            finalName = ExternalImageMetadataFetcher.fetchRealName(context, username, cleanUrl, finalName)
                        }

                        val tempFile = File.createTempFile("download_${fileName}_", ".tmp", userImagesDir)
                        try {
                            downloadFileAtomically(connection, tempFile, file)
                        } finally {
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                        }

                        val imageId = if (existing != null) {
                            // On a l'entrée BDD mais le fichier manquait : on met à jour
                            imageDao.updateImage(existing.copy(name = finalName, description = finalDesc))
                            existing.id
                        } else {
                            // Nouvelle image complète (avec vérification robuste d'échec SQLite et typage d'IDs conforme)
                            val insertedId = imageDao.insertImage(
                                ImageEntity(
                                    remotePath = cleanUrl,
                                    localPath = localPath,
                                    name = finalName,
                                    description = finalDesc
                                )
                            )
                            if (insertedId == -1L) {
                                throw java.io.IOException("Failed to insert image entity into database")
                            }
                            insertedId.toInt()
                        }

                        imageDao.insertTreeImageCrossRef(
                            org.libera.pictotree.data.database.entity.TreeImageCrossRef(treeId, imageId)
                        )
                        return@withContext true
                    } else {
                        Log.e(TAG, "Server returned ${connection.responseCode} for $absoluteUrl")
                    }
                } catch (e: UnauthorizedException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync image $cleanUrl: ${e.message}")
                } finally {
                    connection?.disconnect()
                }
                return@withContext false
            }
}
