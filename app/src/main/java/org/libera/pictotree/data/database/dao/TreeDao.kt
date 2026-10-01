package org.libera.pictotree.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import org.libera.pictotree.data.database.entity.TreeEntity

@Dao
interface TreeDao {
    @Upsert
    suspend fun insertTree(tree: TreeEntity): Long

    @Query("SELECT * FROM trees WHERE id = :treeId")
    suspend fun getTreeById(treeId: Int): TreeEntity?

    @Delete
    suspend fun deleteTree(tree: TreeEntity)

    @Query("SELECT * FROM trees")
    suspend fun getAllTreesSync(): List<TreeEntity>
}
