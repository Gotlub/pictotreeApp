package org.libera.pictotree.data.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.ProfileTreeCrossRef
import org.libera.pictotree.data.database.relation.ProfileWithTrees
import org.libera.pictotree.data.database.entity.TreeEntity

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

    @Update
    suspend fun updateProfileTreeCrossRef(crossRef: ProfileTreeCrossRef)

    @Update
    suspend fun updateProfileTreeCrossRefs(crossRefs: List<ProfileTreeCrossRef>)

    @Query("SELECT MAX(displayOrder) FROM profile_tree_cross_ref WHERE profileId = :profileId")
    suspend fun getMaxDisplayOrderForProfile(profileId: Int): Int?

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: Int): Profile?

    @Query("""
        SELECT trees.* FROM trees 
        INNER JOIN profile_tree_cross_ref ON trees.id = profile_tree_cross_ref.treeId 
        WHERE profile_tree_cross_ref.profileId = :profileId 
        ORDER BY profile_tree_cross_ref.displayOrder ASC
    """)
    suspend fun getTreesForProfileOrdered(profileId: Int): List<TreeEntity>
}
