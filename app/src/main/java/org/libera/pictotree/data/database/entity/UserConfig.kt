package org.libera.pictotree.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Configuration globale de l'utilisateur (une seule ligne par base de données utilisateur).
 * Gère la langue, la sécurité et les préférences d'affichage globales.
 */
@Entity(tableName = "user_config")
data class UserConfig(
    @PrimaryKey val id: Int = 1, // Toujours 1
    val locale: String,
    val startupView: String = "EXPLORER", // "EXPLORER" ou "MAP"
    val defaultOrientation: String = "PORTRAIT", // "PORTRAIT" ou "LANDSCAPE"
    val isOfflineAccessAllowed: Boolean = false, // Autorise l'accès sans mot de passe en mode hors-ligne
    val enableSearch: Boolean = true, // Activer la loupe de recherche (Recherche textuelle)
    val enableRotationButton: Boolean = true, // Afficher le bouton de rotation (card_rotate)
    val enableTTSButton: Boolean = true, // Afficher le bouton de lecture vocale (card_speak)
    val enableViewChangeButton: Boolean = true // Afficher le bouton de changement de vue (card_eye / back_to_nav)
)
