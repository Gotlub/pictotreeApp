package org.libera.pictotree.ui.explorer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import android.app.Application
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.repository.UserConfigRepository
import org.libera.pictotree.data.database.entity.TreeEntity

@OptIn(ExperimentalCoroutinesApi::class)
class TreeExplorerViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val application = mockk<Application>(relaxed = true)
    private val treeDao = mockk<TreeDao>(relaxed = true)
    private val profileDao = mockk<ProfileDao>(relaxed = true)
    private val imageDao = mockk<ImageDao>(relaxed = true)
    private val userConfigRepository = mockk<UserConfigRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: TreeExplorerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        every { userConfigRepository.userConfig } returns flowOf(null)
        
        viewModel = TreeExplorerViewModel(
            application,
            treeDao,
            profileDao,
            imageDao,
            userConfigRepository,
            "http://test.com",
            "testuser"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectNodeWithoutNavigatingById should update previewNode when found in current tree`() = runTest {
        // Given a dummy tree structure in current tree
        val rootNode = TreeNode("1_r_r", "Root", "", listOf(
            TreeNode("1_child_r_0", "Child", "", emptyList())
        ))
        // We need to inject this rootNode into the private field via reflection or by loading a tree
        // Since we can't easily inject private fields, we'll mock the treeDao and load it
        val json = """{"root_node": {"id": "r", "label": "Root", "children": [{"id": "child", "label": "Child"}]}}"""
        coEvery { treeDao.getTreeById(1) } returns TreeEntity(1, "Tree 1", json)
        
        viewModel.loadTree(1)
        advanceUntilIdle()
        
        // When selecting the child ID
        viewModel.selectNodeWithoutNavigatingById("1_child_r_0")
        
        // Then previewNode should be updated
        assertEquals("1_child_r_0", viewModel.uiState.value.previewNode?.id)
        assertEquals("Child", viewModel.uiState.value.previewNode?.label)
    }

    @Test
    fun `loadTree should NOT reset previewNode if already set (Rotation Survival)`() = runTest {
        // Given a previewNode is already set
        val existingNode = TreeNode("1_old_r_0", "Old Selection", "", emptyList())
        viewModel.selectNodeWithoutNavigating(existingNode)
        
        // When loading the same tree (simulating rotation)
        val json = """{"root_node": {"id": "r", "label": "Root"}}"""
        coEvery { treeDao.getTreeById(1) } returns TreeEntity(1, "Tree 1", json)
        
        viewModel.loadTree(1)
        advanceUntilIdle()
        
        // Then previewNode should still be the old one
        assertEquals("1_old_r_0", viewModel.uiState.value.previewNode?.id)
    }

    @Test
    fun `addToPhrase should create a unique instance of the node`() = runTest {
        // Given a node to add
        val node = TreeNode("1_picto_r", "Apple", "url", emptyList())
        viewModel.selectNodeWithoutNavigating(node)
        
        // When adding to phrase twice
        viewModel.addToPhrase()
        viewModel.addToPhrase()
        
        // Then phraseList should have 2 items with unique IDs
        val phrase = viewModel.phraseList.value
        assertEquals(2, phrase.size)
        assertNotEquals(phrase[0].id, phrase[1].id)
        assertTrue(phrase[0].id.startsWith("1_picto_r_"))
        assertEquals("Apple", phrase[0].label)
    }

    @Test
    fun `clearPhrase should empty the phrase list`() = runTest {
        // Given a phrase with items
        val node = TreeNode("1_picto_r", "Apple", "url", emptyList())
        viewModel.selectNodeWithoutNavigating(node)
        viewModel.addToPhrase()
        
        // When clearing
        viewModel.clearPhrase()
        
        // Then list is empty
        assertTrue(viewModel.phraseList.value.isEmpty())
    }

    @Test
    fun `resetSelection should set previewNode to null`() = runTest {
        // Given a selection
        viewModel.selectNodeWithoutNavigating(TreeNode("1_r", "Root", "", emptyList()))
        assertNotNull(viewModel.uiState.value.previewNode)
        
        // When resetting (Inventory return)
        viewModel.resetSelection()
        
        // Then selection is gone
        assertNull(viewModel.uiState.value.previewNode)
    }

    @Test
    fun `selectNodeWithoutNavigatingById should find node in cached roots if not in current tree`() = runTest {
        // Given a cached tree (Tree 2)
        val json2 = """{"root_node": {"id": "r2", "label": "Tree 2 Root"}}"""
        coEvery { treeDao.getTreeById(2) } returns TreeEntity(2, "Tree 2", json2)
        
        // We set the context for Tree 2 so it caches the root
        viewModel.setProfileTreeContext(1, listOf(2))
        advanceUntilIdle()
        
        // Given current tree is Tree 1
        val json1 = """{"root_node": {"id": "r1", "label": "Tree 1 Root"}}"""
        coEvery { treeDao.getTreeById(1) } returns TreeEntity(1, "Tree 1", json1)
        viewModel.loadTree(1)
        advanceUntilIdle()
        
        // When selecting an ID from Tree 2
        viewModel.selectNodeWithoutNavigatingById("2_r2_r")
        
        // Then previewNode should be updated from Tree 2
        assertEquals("2_r2_r", viewModel.uiState.value.previewNode?.id)
        assertEquals("Tree 2 Root", viewModel.uiState.value.previewNode?.label)
    }
}
