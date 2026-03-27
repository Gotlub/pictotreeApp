package org.libera.pictotree.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "tree_image_cross_ref",
    primaryKeys = ["treeId", "imageId"],
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["imageId"])
    ]
)
data class TreeImageCrossRef(
    val treeId: Int,
    val imageId: Int
)
