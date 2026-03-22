package org.libera.pictotree.network.dto

import com.google.gson.annotations.SerializedName

data class TreeMetadataDTO(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("owner") val owner: String,
    @SerializedName("is_public") val isPublic: Boolean,
    @SerializedName("root_image_url") val rootImageUrl: String?
)
