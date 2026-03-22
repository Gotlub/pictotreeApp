package org.libera.pictotree.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.libera.pictotree.data.database.entity.TreeEntity

@Dao
interface TreeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTree(tree: TreeEntity): Long

    @Query("SELECT * FROM trees WHERE id = :treeId")
    suspend fun getTreeById(treeId: Int): TreeEntity?
}
