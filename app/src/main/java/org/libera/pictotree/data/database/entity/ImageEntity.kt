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
    val name: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "remote_id")
    val remoteId: Int = -1,
    @ColumnInfo(name = "last_modif")
    val lastModif: String? = null,
    val hash: String? = null
)
