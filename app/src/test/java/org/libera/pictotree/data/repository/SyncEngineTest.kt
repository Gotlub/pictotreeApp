package org.libera.pictotree.data.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.network.dto.TreeNodeDTO
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockContext: Context
    private lateinit var mockImageDao: ImageDao
    private lateinit var tempDir: File

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockContext = mockk()
        mockImageDao = mockk(relaxed = true)

        // Assign a temporary JVM folder impersonating Android's internal `getFilesDir()` Sandbox
        tempDir = Files.createTempDirectory("test_files").toFile()
        every { mockContext.filesDir } returns tempDir
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `test network sync downloads missing image and inserts to database`() = runTest {
        // Enqueue a simple fake image payload representing a raw PNG stream (HTTP 200)
        val mockImageBytes = byteArrayOf(0x00, 0x01, 0x02)
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(mockImageBytes)))

        // Intercepting specific testing localhost URL
        val remoteUrl = mockWebServer.url("/mock/image.png").toString()

        val rootNode = TreeNodeDTO(
            label = "Test Valid Node",
            imageUrl = remoteUrl,
            nodeId = "123",
            children = emptyList()
        )

        // By returning NULL, we trigger the Engine into thinking it's a NEW image (Requires HTTP request)
        coEvery { mockImageDao.getImageByRemotePath(any()) } returns null
        coEvery { mockImageDao.insertImage(any()) } returns 1L

        val engine = ImageSyncEngine(
            context = mockContext,
            imageDao = mockImageDao,
            username = "test_user",
            authToken = "fake_jwt_token"
        )
        
        // Execute execution
        engine.syncImagesFromNode(rootNode, treeId = 404)

        // Verify logical file generation matching Android Files boundaries
        val expectedImagesDir = File(tempDir, "test_user/images")
        assertTrue("User images directory should be scaffolded", expectedImagesDir.exists())
        
        val actualFiles = expectedImagesDir.listFiles()
        assertTrue("The PNG thumbnail should exist physically inside the mocked folder", actualFiles?.isNotEmpty() == true)

        // Ensure database writes mapping correctly onto relation schemas
        coVerify(exactly = 1) { 
            mockImageDao.insertImage(any())
            mockImageDao.insertTreeImageCrossRef(any())
        }
    }
}
