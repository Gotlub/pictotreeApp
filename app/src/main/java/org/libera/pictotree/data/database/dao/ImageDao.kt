package org.libera.pictotree.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.libera.pictotree.data.database.entity.ImageEntity
import org.libera.pictotree.data.database.entity.TreeImageCrossRef

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageEntity): Long

    @Query("SELECT * FROM images WHERE remote_path = :remotePath LIMIT 1")
    suspend fun getImageByRemotePath(remotePath: String): ImageEntity?
    
    @Query("SELECT * FROM images WHERE local_path = :localPath LIMIT 1")
    suspend fun getImageByLocalPath(localPath: String): ImageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTreeImageCrossRef(crossRef: TreeImageCrossRef)

    @Query("SELECT COUNT(*) FROM tree_image_cross_ref WHERE imageId = :imageId")
    suspend fun countImageReferences(imageId: Int): Int

    @Query("DELETE FROM images WHERE id = :imageId")
    suspend fun deleteImageById(imageId: Int)

    @Query("SELECT images.* FROM images INNER JOIN tree_image_cross_ref ON images.id = tree_image_cross_ref.imageId WHERE tree_image_cross_ref.treeId = :treeId")
    suspend fun getImagesForTree(treeId: Int): List<ImageEntity>
}
