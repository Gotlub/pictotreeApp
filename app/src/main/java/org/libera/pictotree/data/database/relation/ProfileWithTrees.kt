package org.libera.pictotree.data.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.ProfileTreeCrossRef
import org.libera.pictotree.data.database.entity.TreeEntity

data class ProfileWithTrees(
    @Embedded val profile: Profile,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ProfileTreeCrossRef::class,
            parentColumn = "profileId",
            entityColumn = "treeId"
        )
    )
    val trees: List<TreeEntity>
)
