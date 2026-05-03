package org.libera.pictotree.utils

import java.security.MessageDigest

object FileUtils {
    /**
     * Génère un nom de fichier déterministe basé sur l'URL distante.
     * Utilisé par ImageSyncEngine (Sync) et TreeExplorerViewModel (Visualisation).
     */
    fun getLocalFileNameFromUrl(remoteUrl: String): String {
        if (remoteUrl.isBlank()) return ""
        
        // Nettoyer l'URL : supprimer les paramètres de requête (ex: ?123456) pour le hash
        val cleanUrl = remoteUrl.substringBefore('?')
        
        val bytes = MessageDigest.getInstance("SHA-256").digest(cleanUrl.toByteArray())
        val hash = bytes.joinToString("") { "%02x".format(it) }
        val ext = cleanUrl.substringAfterLast('.', "png")
        return "$hash.$ext"
    }
}
