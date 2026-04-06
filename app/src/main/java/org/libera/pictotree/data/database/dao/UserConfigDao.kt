package org.libera.pictotree.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.libera.pictotree.data.database.entity.UserConfig

@Dao
interface UserConfigDao {
    @Query("SELECT * FROM user_config WHERE id = 1")
    suspend fun getUserConfig(): UserConfig?

    @Query("SELECT * FROM user_config WHERE id = 1")
    fun getUserConfigFlow(): Flow<UserConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserConfig(config: UserConfig)

    @Update
    suspend fun updateUserConfig(config: UserConfig)
}
