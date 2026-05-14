package org.libera.pictotree.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "profile_tree_cross_ref",
    primaryKeys = ["profileId", "treeId"],
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["treeId"])
    ]
)
data class ProfileTreeCrossRef(
    val profileId: Int,
    val treeId: Int,
    val displayOrder: Int = 0,
    val colorCode: String = "#000000"
)
