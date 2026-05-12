package org.libera.pictotree.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val avatarUrl: String? = null, // Local file:// path or color: hex
    val remoteAvatarUrl: String? = null, // Original remote URL (Arasaac or Flask)
    val settingsJson: String? = null // Stockage flexible des préférences
)
