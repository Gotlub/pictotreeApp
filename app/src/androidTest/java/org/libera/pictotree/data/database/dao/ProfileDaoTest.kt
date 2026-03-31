package org.libera.pictotree.data.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.TreeEntity
import org.libera.pictotree.data.database.entity.ProfileTreeCrossRef

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ProfileDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var profileDao: ProfileDao
    private lateinit var treeDao: TreeDao

    @Before
    fun initDb() {
        // La RAM (Memory) accélère 100x l'exécution sur tests et n'altère pas le télphone réel
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        profileDao = database.profileDao()
        treeDao = database.treeDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndCountProfiles_crossRefIsRobust() = runTest {
        // Enregistrer l'Arbre originel
        val tree = TreeEntity(id = 1, name = "Test Tree", jsonPayload = "{}", isPublic = true, lastSync = 0, rootUrl = null)
        treeDao.insertTree(tree)

        // Simuler 2 comptes ayant l'arbre
        val profile1 = Profile(id = 1, name = "Enfant 1")
        val profile2 = Profile(id = 2, name = "Enfant 2")
        profileDao.insertProfile(profile1)
        profileDao.insertProfile(profile2)

        profileDao.insertProfileTreeCrossRef(ProfileTreeCrossRef(profile1.id, tree.id, 0))
        profileDao.insertProfileTreeCrossRef(ProfileTreeCrossRef(profile2.id, tree.id, 1))

        // Évaluer le comportement algorithmique : On attend 2 comptes impactés
        val initialCount = profileDao.countProfilesForTree(tree.id)
        assertEquals("Il devrait y avoir deux enfants liés à l'arbre", 2, initialCount)
        
        // Simuler la destruction partielle
        profileDao.deleteProfileTreeCrossRefByIds(profile1.id, tree.id)
        
        // Vérification du maintien relationnel pour l'Enfant 2
        val finalCount = profileDao.countProfilesForTree(tree.id)
        assertEquals("L'extraction doit avoir réussi proprement", 1, finalCount)
    }
}
