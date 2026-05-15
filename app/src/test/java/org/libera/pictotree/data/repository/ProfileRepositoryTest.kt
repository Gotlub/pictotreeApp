package org.libera.pictotree.data.repository

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.entity.ImageEntity
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.TreeEntity
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val profileDao = mockk<ProfileDao>(relaxed = true)
    private val treeDao = mockk<TreeDao>(relaxed = true)
    private val imageDao = mockk<ImageDao>(relaxed = true)
    private val username = "testuser"
    private lateinit var tempFilesDir: File
    
    private lateinit var repository: ProfileRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0

        tempFilesDir = Files.createTempDirectory("test_purge").toFile()
        every { context.filesDir } returns tempFilesDir
        
        repository = ProfileRepository(context, profileDao, treeDao, imageDao, username)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun `deleteFullProfile should purge DB and physical files when orphaned`() = runTest {
        // Setup
        val profileId = 1
        val avatarUrl = "http://server.com/avatar.png"
        val treeUrl = "http://server.com/tree.png"
        
        val profile = Profile(profileId, "Nico", remoteAvatarUrl = avatarUrl)
        val tree = TreeEntity(10, "Tree 10", "{}", rootUrl = treeUrl)
        
        val userDir = File(tempFilesDir, "$username/images")
        userDir.mkdirs()
        val avatarFile = File(userDir, "avatar.png").apply { writeText("data") }
        val treeFile = File(userDir, "tree.png").apply { writeText("data") }

        val avatarImg = ImageEntity(200, avatarUrl, "images/avatar.png")
        val treeImg = ImageEntity(100, treeUrl, "images/tree.png")

        // Mocks
        coEvery { profileDao.getProfileById(profileId) } returns profile
        coEvery { profileDao.getTreesForProfileOrdered(profileId) } returns listOf(tree)
        coEvery { profileDao.countProfilesForTree(10) } returns 0 // Orphan
        coEvery { imageDao.getImagesForTree(10) } returns listOf(treeImg)
        coEvery { imageDao.getImageByRemotePath(avatarUrl) } returns avatarImg
        coEvery { imageDao.getAllImages() } returns listOf(avatarImg, treeImg)
        
        // Simuler qu'après la suppression, plus rien n'est utilisé
        coEvery { imageDao.countImageReferences(any()) } returns 0
        coEvery { profileDao.getAllUsedAvatarUrls() } returns emptyList()
        coEvery { treeDao.getAllTreesSync() } returns emptyList()

        // Act
        repository.deleteFullProfile(profileId)

        // Assert
        coVerify { profileDao.deleteProfile(profile) }
        coVerify { treeDao.deleteTree(tree) }
        coVerify { imageDao.deleteImageById(100) }
        coVerify { imageDao.deleteImageById(200) }
        
        // Vérification physique
        assertFalse("Avatar file should be deleted", avatarFile.exists())
        assertFalse("Tree file should be deleted", treeFile.exists())
    }

    @Test
    fun `runTargetedGarbageCollector should NOT delete image if used by another profile`() = runTest {
        val sharedUrl = "http://server.com/shared.png"
        val image = ImageEntity(1, sharedUrl, "images/shared.png")
        
        val userDir = File(tempFilesDir, "$username/images")
        userDir.mkdirs()
        val sharedFile = File(userDir, "shared.png").apply { writeText("data") }

        coEvery { imageDao.countImageReferences(1) } returns 0
        
        // Simuler que l'image est utilisée par un AUTRE profil
        coEvery { profileDao.getAllUsedAvatarUrls() } returns listOf(sharedUrl)
        coEvery { treeDao.getAllTreesSync() } returns emptyList()

        // Act
        repository.runTargetedGarbageCollector(listOf(image))

        // Assert
        coVerify(exactly = 0) { imageDao.deleteImageById(any()) }
        assertTrue("Shared file should NOT be deleted", sharedFile.exists())
    }
}
