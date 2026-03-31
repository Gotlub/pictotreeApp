package org.libera.pictotree.ui.editprofile

import android.app.Application
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.entity.ImageEntity
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.TreeEntity
import org.libera.pictotree.network.TreeApiService
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var mockApplication: Application
    private lateinit var mockProfileDao: ProfileDao
    private lateinit var mockTreeDao: TreeDao
    private lateinit var mockImageDao: ImageDao
    private lateinit var mockTreeApiService: TreeApiService
    private lateinit var tempFilesDir: File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockApplication = mockk()
        mockProfileDao = mockk(relaxed = true)
        mockTreeDao = mockk(relaxed = true)
        mockImageDao = mockk(relaxed = true)
        mockTreeApiService = mockk(relaxed = true)

        // Simuler le stockage I/O système sur le bureau temporaire JVM
        tempFilesDir = Files.createTempDirectory("test_app_files").toFile()
        every { mockApplication.filesDir } returns tempFilesDir
        every { mockApplication.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        
        // Mock SessionManager global construction logic without touching actual SharedPrefs
        mockkConstructor(SessionManager::class)
        every { anyConstructed<SessionManager>().getUsername() } returns "test_user"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll() // Désengager MockK
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun `deleteTree should purge SQLite orphans and physical thumbnails when tree is unique to profile`() = runTest(testDispatcher) {
        // Mock a Tree and Profile state
        val mockTree = TreeEntity(id = 11, name = "Target Tree", jsonPayload = "{}", lastSync = 0L, isPublic = false, rootUrl = null)
        val mockProfile = Profile(id = 1, name = "Kid Profile")
        val mockImage = ImageEntity(id = 55, remotePath = "http://mock.png", localPath = "images/target.png", name = "target.png")

        // Construct fake physically written image in the temp sandbox
        val userFilesDir = File(tempFilesDir, "test_user")
        val fakeStorageImage = File(userFilesDir, "images/target.png")
        fakeStorageImage.parentFile.mkdirs()
        fakeStorageImage.writeText("fake imageData stream block...")

        // Initialiser une UiState.Success parfaite
        coEvery { mockProfileDao.getProfileById(1) } returns mockProfile
        coEvery { mockProfileDao.getTreesForProfileOrdered(1) } returns listOf(mockTree)

        val viewModel = EditProfileViewModel(
            application = mockApplication,
            profileDao = mockProfileDao,
            treeDao = mockTreeDao,
            imageDao = mockImageDao,
            treeApiService = mockTreeApiService
        )
        
        // Charger l'état 
        viewModel.loadProfile(1)
        advanceUntilIdle() 

        // Renseigner le Garbage Collector (Count=0 veut dire destruction justifiée)
        coEvery { mockProfileDao.countProfilesForTree(11) } returns 0
        coEvery { mockImageDao.getImagesForTree(11) } returns listOf(mockImage)
        coEvery { mockImageDao.countImageReferences(55) } returns 0 

        // Exécuter l'action métier dangereuse logicielle (Suppression)
        viewModel.deleteTree(mockTree)
        advanceUntilIdle()

        // S'assurer que le DB DAO a bien effacé l'arbre, la liaison et l'image locale !
        coVerify(exactly = 1) { 
            mockProfileDao.deleteProfileTreeCrossRefByIds(1, 11)
            mockTreeDao.deleteTree(mockTree)
            mockImageDao.deleteImageById(55)
        }
        
        // Vérifier par empreinte Système que I/O Disk a été sollicité
        assertFalse("Le fichier root ciblé aurait du être effacé physiquement par le GC Engine !", fakeStorageImage.exists())
    }
}
