package org.libera.pictotree.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trees")
data class TreeEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val jsonPayload: String
)
