package org.libera.pictotree.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.libera.pictotree.data.database.entity.ImageEntity

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageEntity): Long

    @Query("SELECT * FROM images WHERE remote_path = :remotePath LIMIT 1")
    suspend fun getImageByRemotePath(remotePath: String): ImageEntity?
    
    @Query("SELECT * FROM images WHERE local_path = :localPath LIMIT 1")
    suspend fun getImageByLocalPath(localPath: String): ImageEntity?
}
