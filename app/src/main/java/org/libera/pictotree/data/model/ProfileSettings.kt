package org.libera.pictotree.data.model

import com.google.gson.annotations.SerializedName

/**
 * Preferences specific to a user profile, stored as JSON in the database.
 */
data class ProfileSettings(
    @SerializedName("startup_view") val startupView: String = "EXPLORER", // "EXPLORER" or "MAP"
    @SerializedName("default_orientation") val defaultOrientation: String = "PORTRAIT", // "PORTRAIT" or "LANDSCAPE"
    @SerializedName("enable_search") val enableSearch: Boolean = true
)
