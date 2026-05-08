package org.libera.pictotree.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import kotlinx.coroutines.runBlocking
import org.libera.pictotree.data.database.dao.ImageDao
import java.io.File
import java.io.FileInputStream

/**
 * Intercepteur centralisé pour les images demandées par les WebViews (Treant.js).
 * Gère le nettoyage des cache-busters et le mapping vers le stockage local SQLite.
 */
object WebViewImageInterceptor {
    private const val TAG = "PictoTreeNav"

    /**
     * @param strictOffline Si true, bloque tout accès réseau si l'image n'est pas trouvée localement.
     */
    fun intercept(context: Context, username: String, imageDao: ImageDao, url: Uri?, strictOffline: Boolean = false): WebResourceResponse? {
        val urlString = url?.toString() ?: return null
        
        // On n'intercepte que les requêtes vers notre API de pictos ou les images distantes
        if (!urlString.contains("/api/v1/mobile/pictograms/") && !urlString.startsWith("http")) {
            return null
        }

        // 1. Nettoyage de l'URL (cache-buster et query params) pour la recherche en BDD
        val cleanUrl = FileUtils.getCleanUrl(urlString)
        
        // 2. Extraire la partie stable relative (ex: gotlub/unnamed.jpg)
        val relativePart = cleanUrl.substringAfter("/api/v1/mobile/pictograms/", "")
            .substringAfter("/pictograms/", "")

        return runBlocking {
            // Recherche par URL nettoyée (clé stable en BDD désormais)
            val entity = imageDao.getImageByRemotePath(cleanUrl)
                ?: if (relativePart.isNotEmpty()) {
                    // Fallback sur chemin relatif propre si le host a changé
                    imageDao.getImageByRemotePath("/api/v1/mobile/pictograms/$relativePart")
                        ?: imageDao.getImageByRemotePath(relativePart)
                } else null

            if (entity != null) {
                val localFile = File(context.filesDir, "$username/${entity.localPath}")
                if (localFile.exists()) {
                    try {
                        val stream = FileInputStream(localFile)
                        val extension = localFile.extension.lowercase()
                        val mimeType = if (extension == "png") "image/png" 
                                      else if (extension == "gif") "image/gif"
                                      else "image/jpeg"
                        
                        Log.d(TAG, "WebView Intercepted: $cleanUrl -> local cache")
                        return@runBlocking WebResourceResponse(mimeType, "UTF-8", stream)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading intercepted file", e)
                    }
                } else {
                    Log.w(TAG, "WebView Intercepted: Entity found but file MISSING at ${localFile.absolutePath}")
                }
            }

            if (strictOffline) {
                Log.w(TAG, "WebView BLOCKING Network request (Strict Offline): $urlString")
                // Retourner une réponse vide (404-like) pour bloquer la requête réseau
                return@runBlocking WebResourceResponse("image/png", "UTF-8", null)
            }
            
            null
        }
    }
}
