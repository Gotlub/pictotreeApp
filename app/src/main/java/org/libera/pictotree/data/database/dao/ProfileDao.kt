package org.libera.pictotree.data.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.ProfileTreeCrossRef
import org.libera.pictotree.data.database.relation.ProfileWithTrees

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun getAllProfilesFlow(): Flow<List<Profile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileWithTrees(profileId: Int): ProfileWithTrees?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileTreeCrossRef(crossRef: ProfileTreeCrossRef)

    @Delete
    suspend fun deleteProfileTreeCrossRef(crossRef: ProfileTreeCrossRef)
}
