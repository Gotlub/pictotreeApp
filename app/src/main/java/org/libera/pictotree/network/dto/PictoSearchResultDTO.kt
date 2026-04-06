package org.libera.pictotree.network.dto

import com.google.gson.annotations.SerializedName

data class PictoSearchResultDTO(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("image_url") val imageUrl: String
)
