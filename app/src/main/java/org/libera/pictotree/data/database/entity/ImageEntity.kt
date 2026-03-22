package org.libera.pictotree.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "remote_path")
    val remotePath: String,
    @ColumnInfo(name = "local_path")
    val localPath: String,
    val name: String? = null
)
