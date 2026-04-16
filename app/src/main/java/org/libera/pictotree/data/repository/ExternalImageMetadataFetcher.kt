package org.libera.pictotree.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

import android.content.Context
import org.libera.pictotree.data.database.AppDatabase

/**
 * Utilitaire évolutif pour extraire la "vraie" description d'une image provenant
 * de banques tierces (Arasaac, etc.) lorsque l'image échappe à notre propre API.
 */
object ExternalImageMetadataFetcher {
    
    suspend fun fetchRealName(context: Context, username: String, remoteUrl: String, fallbackName: String): String = withContext(Dispatchers.IO) {
        try {
            // 1. BANQUE ARASAAC
            // URL typique : https://static.arasaac.org/pictograms/3321/3321_300.png
            val arasaacRegex = Regex("static\\.arasaac\\.org/pictograms/(\\d+)")
            val arasaacMatch = arasaacRegex.find(remoteUrl)
            
            if (arasaacMatch != null) {
                val pictoId = arasaacMatch.groupValues[1]
                
                // Récupération de la locale configurée par l'utilisateur
                val appDatabase = AppDatabase.getDatabase(context, username)
                val userConfig = appDatabase.userConfigDao().getUserConfig()
                val locale = userConfig?.locale ?: java.util.Locale.getDefault().language
                
                // L'API Arasaac utilise "fr", "en", "es", etc.
                val supportedLocales = listOf("fr", "en", "es", "de", "it", "pt", "ru", "ca", "eu", "gl", "val", "zh")
                val safeLocale = if (supportedLocales.contains(locale)) locale else "en"
                
                val apiUrl = "https://api.arasaac.org/api/pictograms/$safeLocale/$pictoId"
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.connect()
                
                if (connection.responseCode in 200..299) {
                    val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(jsonStr)
                    val keywords = jsonObject.optJSONArray("keywords")
                    if (keywords != null && keywords.length() > 0) {
                        val keyword = keywords.getJSONObject(0).optString("keyword")
                        if (keyword.isNotBlank()) {
                            return@withContext keyword.replaceFirstChar { it.uppercase() }
                        }
                    }
                }
            }
            
            // 2. [ESPACE POUR FUTURE BANQUE D'IMAGES : Sclera, Mulberry, etc.]
            // else if (remoteUrl.contains("...sclera...")) { ... }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Par défaut, si rien ne l'a intercepté ou si erreur réseau
        return@withContext fallbackName
    }
}
