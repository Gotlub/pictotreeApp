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
import org.libera.pictotree.data.repository.ProfileRepository
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
    private lateinit var mockProfileRepository: ProfileRepository
    private lateinit var tempFilesDir: File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockApplication = mockk()
        mockProfileDao = mockk(relaxed = true)
        mockTreeDao = mockk(relaxed = true)
        mockImageDao = mockk(relaxed = true)
        mockTreeApiService = mockk(relaxed = true)
        mockProfileRepository = mockk(relaxed = true)

        // Simuler le stockage I/O système
        tempFilesDir = Files.createTempDirectory("test_app_files").toFile()
        every { mockApplication.filesDir } returns tempFilesDir
        every { mockApplication.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        
        mockkConstructor(SessionManager::class)
        every { anyConstructed<SessionManager>().getUsername() } returns "test_user"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun `deleteTreeFromProfile should delegate to repository and reload profile`() = runTest(testDispatcher) {
        val viewModel = EditProfileViewModel(
            application = mockApplication,
            profileRepository = mockProfileRepository,
            profileDao = mockProfileDao,
            treeDao = mockTreeDao,
            imageDao = mockImageDao,
            treeApiService = mockTreeApiService
        )
        
        // When: Deleting a tree from profile
        viewModel.deleteTreeFromProfile(1, 10)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { mockProfileRepository.removeTreeFromProfile(1, 10) }
        // And: Profile should be reloaded
        coVerify { mockProfileDao.getProfileById(1) }
    }

    @Test
    fun `deleteFullProfile should delegate to repository and call completion`() = runTest(testDispatcher) {
        val viewModel = EditProfileViewModel(
            application = mockApplication,
            profileRepository = mockProfileRepository,
            profileDao = mockProfileDao,
            treeDao = mockTreeDao,
            imageDao = mockImageDao,
            treeApiService = mockTreeApiService
        )
        
        var completed = false
        // When: Deleting full profile
        viewModel.deleteFullProfile(1) { completed = true }
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { mockProfileRepository.deleteFullProfile(1) }
        // And: Completion callback should be triggered
        assertEquals(true, completed)
    }
}
