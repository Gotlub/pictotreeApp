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

    fun intercept(context: Context, username: String, imageDao: ImageDao, url: Uri?): WebResourceResponse? {
        val urlString = url?.toString() ?: return null
        
        // On n'intercepte que les requêtes vers notre API de pictos ou les images distantes
        if (!urlString.contains("/api/v1/mobile/pictograms/") && !urlString.startsWith("http")) {
            return null
        }

        // 1. Nettoyage de l'URL (cache-buster et query params)
        var cleanUrl = urlString.substringBefore("?")
        // Gérer le cas où le cache-buster est collé à l'extension sans '?'
        cleanUrl = cleanUrl.replace(Regex("(\\.(jpg|jpeg|png|gif))\\d+$", RegexOption.IGNORE_CASE), "$1")
        
        // 2. Extraire la partie stable (ex: gotlub/unnamed.jpg)
        val relativePart = cleanUrl.substringAfter("/api/v1/mobile/pictograms/", "")
            .substringAfter("/pictograms/", "")

        return runBlocking {
            // Recherche par URL exacte ou relative (robuste aux changements de host)
            val entity = imageDao.getImageByRemotePath(cleanUrl)
                ?: if (relativePart.isNotEmpty()) {
                    imageDao.getImageByRemotePath("/api/v1/mobile/pictograms/$relativePart")
                        ?: imageDao.getImageByRemotePath(relativePart)
                } else null

            if (entity != null) {
                val localFile = File(context.filesDir, "$username/${entity.localPath}")
                if (localFile.exists()) {
                    try {
                        val stream = FileInputStream(localFile)
                        val mimeType = if (localFile.name.endsWith(".png", true)) "image/png" else "image/jpeg"
                        Log.d(TAG, "WebView Intercepted: $cleanUrl -> local cache")
                        return@runBlocking WebResourceResponse(mimeType, "UTF-8", stream)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading intercepted file", e)
                    }
                }
            }
            null
        }
    }
}
