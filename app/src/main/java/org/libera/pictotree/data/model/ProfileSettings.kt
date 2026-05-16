package org.libera.pictotree.data.model

import com.google.gson.annotations.SerializedName

/**
 * Preferences specific to a user profile, stored as JSON in the database.
 * Les préférences globales d'affichage ont été déplacées vers UserConfig.
 */
data class ProfileSettings(
    @SerializedName("enable_search") val enableSearch: Boolean = true
)
