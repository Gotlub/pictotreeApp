package org.libera.pictotree.data.database.entity

import androidx.room.Entity

@Entity(
    tableName = "profile_tree_cross_ref",
    primaryKeys = ["profileId", "treeId"]
)
data class ProfileTreeCrossRef(
    val profileId: Int,
    val treeId: Int,
    val displayOrder: Int = 0,
    val colorCode: String = "#000000"
)
