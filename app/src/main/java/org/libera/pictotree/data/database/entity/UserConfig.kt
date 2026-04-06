package org.libera.pictotree.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Configuration globale de l'utilisateur (une seule ligne par base de données utilisateur).
 * Gère la langue et la sécurité hors-ligne.
 */
@Entity(tableName = "user_config")
data class UserConfig(
    @PrimaryKey val id: Int = 1, // Toujours 1
    val locale: String,
    val offlineSettingsPin: String? = null
)
