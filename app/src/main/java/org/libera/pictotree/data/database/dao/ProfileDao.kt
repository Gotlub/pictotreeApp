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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfile(profile: Profile): Long

    @Update
    suspend fun updateProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileWithTrees(profileId: Int): ProfileWithTrees?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileTreeCrossRef(crossRef: ProfileTreeCrossRef)

    @Delete
    suspend fun deleteProfileTreeCrossRef(crossRef: ProfileTreeCrossRef)

    @Query("DELETE FROM profile_tree_cross_ref WHERE profileId = :profileId AND treeId = :treeId")
    suspend fun deleteProfileTreeCrossRefByIds(profileId: Int, treeId: Int)

    @Query("UPDATE profile_tree_cross_ref SET displayOrder = displayOrder - 1 WHERE profileId = :profileId AND displayOrder > :deletedOrder")
    suspend fun reorderAfterDeletion(profileId: Int, deletedOrder: Int)

    @Query("SELECT COUNT(*) FROM profile_tree_cross_ref WHERE treeId = :treeId")
    suspend fun countProfilesForTree(treeId: Int): Int

    @Update
    suspend fun updateProfileTreeCrossRef(crossRef: ProfileTreeCrossRef)

    @Update
    suspend fun updateProfileTreeCrossRefs(crossRefs: List<ProfileTreeCrossRef>)

    @Query("SELECT MAX(displayOrder) FROM profile_tree_cross_ref WHERE profileId = :profileId")
    suspend fun getMaxDisplayOrderForProfile(profileId: Int): Int?

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: Int): Profile?

    @Query("SELECT * FROM profile_tree_cross_ref WHERE profileId = :profileId AND treeId = :treeId")
    suspend fun getProfileTreeCrossRef(profileId: Int, treeId: Int): ProfileTreeCrossRef?

    @Query("UPDATE profile_tree_cross_ref SET colorCode = :colorCode WHERE profileId = :profileId AND treeId = :treeId")
    suspend fun updateTreeColor(profileId: Int, treeId: Int, colorCode: String)

    @Query("SELECT COUNT(*) FROM profiles WHERE remoteAvatarUrl = :remoteUrl")
    suspend fun countProfilesUsingAvatar(remoteUrl: String): Int

    @Query("SELECT remoteAvatarUrl FROM profiles WHERE remoteAvatarUrl IS NOT NULL")
    suspend fun getAllUsedAvatarUrls(): List<String>

    @Query("""
        SELECT trees.*, profile_tree_cross_ref.colorCode FROM trees 
        INNER JOIN profile_tree_cross_ref ON trees.id = profile_tree_cross_ref.treeId 
        WHERE profile_tree_cross_ref.profileId = :profileId 
        ORDER BY profile_tree_cross_ref.displayOrder ASC
    """)
    suspend fun getTreesWithColorForProfile(profileId: Int): List<TreeWithColor>

    @Query("""
        SELECT trees.* FROM trees 
        INNER JOIN profile_tree_cross_ref ON trees.id = profile_tree_cross_ref.treeId 
        WHERE profile_tree_cross_ref.profileId = :profileId 
        ORDER BY profile_tree_cross_ref.displayOrder ASC
    """)
    suspend fun getTreesForProfileOrdered(profileId: Int): List<TreeEntity>
}

data class TreeWithColor(
    @Embedded val tree: TreeEntity,
    val colorCode: String
)
