package org.libera.pictotree.network.dto

import com.google.gson.annotations.SerializedName

data class TreeFullDTO(
    @SerializedName("tree_id") val treeId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("root_node") val rootNode: TreeNodeDTO?
)

data class TreeNodeDTO(
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("label") val label: String,
    @SerializedName("description") val description: String?,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("children") val children: List<TreeNodeDTO>
)
