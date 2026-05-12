package org.libera.pictotree.network.dto

import com.google.gson.annotations.SerializedName

data class ProfileDTO(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("remote_avatar_url") val remoteAvatarUrl: String?,
    @SerializedName("trees") val trees: List<ProfileTreeDTO>? = null
)

data class ProfileTreeDTO(
    @SerializedName("tree_id") val treeId: Int,
    @SerializedName("colorCode") val colorCode: String?
)
