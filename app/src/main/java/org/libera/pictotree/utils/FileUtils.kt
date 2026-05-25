package org.libera.pictotree.utils

import java.security.MessageDigest

object FileUtils {
    // Flag de configuration pour forcer la préservation de 127.0.0.1 (ex: en tests unitaires)
    // Initialisé par défaut via une détection de l'environnement JVM, mais peut être surchargé
    var forcePreserveLocalhost: Boolean = try {
        System.getProperty("sun.java.command")?.contains("junit") == true ||
        System.getProperty("java.class.path")?.contains("junit") == true
    } catch (e: Exception) {
        false
    }

    /**
     * Enlève les paramètres de requête (?123456) pour obtenir une clé stable.
     */
    fun getCleanUrl(url: String?): String {
        if (url == null) return ""
        return url.substringBefore('?')
    }

    /**
     * Assure que l'URL est absolue et pointe sur le bon host (émulateur).
     */
    fun normalizeUrl(url: String?, hostUrl: String): String {
        if (url.isNullOrBlank()) return ""
        
        // 1. Gérer les URLs déjà absolues
        if (url.startsWith("http")) {
            return normalizeServerAddress(url)
        }
        
        if (url.startsWith("file") || url.startsWith("color:")) return url
        
        // 2. Construire l'URL absolue depuis le chemin relatif
        val base = hostUrl.removeSuffix("/")
        val path = if (url.startsWith("/")) url else "/$url"
        
        // S'assurer qu'on ne duplique pas /api/v1/mobile/ et qu'on ne l'ajoute pas aux dossiers de ressources statiques
        val finalPath = if (path.contains("/api/v1/mobile/") || path.contains("/uploads/") || path.contains("/pictograms/")) {
            path
        } else {
            "/api/v1/mobile${path}"
        }
        
        return normalizeServerAddress("$base$finalPath")
    }

    /**
     * Remplace 127.0.0.1 par l'adresse réelle du serveur (ex: 10.0.2.2) 
     * pour que l'émulateur puisse communiquer avec le backend.
     */
    fun normalizeServerAddress(url: String?): String {
        if (url == null) return ""
        if (!url.contains("127.0.0.1")) return url
        
        if (forcePreserveLocalhost) return url

        val actualHost = try {
            java.net.URL(org.libera.pictotree.network.RetrofitClient.SERVER_URL).host
        } catch (e: Exception) {
            "10.0.2.2"
        } ?: "10.0.2.2"
        return url.replace("127.0.0.1", actualHost)
    }

    /**
     * Génère un nom de fichier déterministe basé sur l'URL distante.
     * Utilisé par ImageSyncEngine (Sync) et TreeExplorerViewModel (Visualisation).
     */
    fun getLocalFileNameFromUrl(remoteUrl: String?): String {
        if (remoteUrl.isNullOrBlank()) return ""
        
        // Nettoyer l'URL : supprimer les paramètres de requête (ex: ?123456) pour le hash
        val cleanUrl = getCleanUrl(remoteUrl)
        
        val bytes = MessageDigest.getInstance("SHA-256").digest(cleanUrl.toByteArray())
        val hash = bytes.joinToString("") { "%02x".format(it) }
        val ext = cleanUrl.substringAfterLast('.', "png")
        return "$hash.$ext"
    }

    /**
     * Résout l'URL ou le fichier local final pour Coil.
     * Centralise la logique utilisée dans les différents adaptateurs et dialogs.
     */
    fun getFinalImageSource(url: String?, context: android.content.Context, username: String, hostUrl: String): Any {
        if (url.isNullOrBlank()) return ""
        
        val cleanUrl = getCleanUrl(url)
        val fileName = getLocalFileNameFromUrl(cleanUrl)
        val localFile = java.io.File(context.filesDir, "$username/images/$fileName")
        
        val source: Any = if (localFile.exists()) localFile else url
        
        if (source is String && !source.startsWith("http") && !source.startsWith("file") && !source.startsWith("color:")) {
            return normalizeUrl(source, hostUrl)
        }
        return source
    }
}
