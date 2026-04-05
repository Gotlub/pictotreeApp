package org.libera.pictotree.utils

import java.security.MessageDigest

object FileUtils {
    /**
     * Génère un nom de fichier déterministe basé sur l'URL distante.
     * Utilisé par ImageSyncEngine (Sync) et TreeExplorerViewModel (Visualisation).
     */
    fun getLocalFileNameFromUrl(remoteUrl: String): String {
        if (remoteUrl.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(remoteUrl.toByteArray())
        val hash = bytes.joinToString("") { "%02x".format(it) }
        val ext = remoteUrl.substringAfterLast('.', "png")
        return "$hash.$ext"
    }
}
