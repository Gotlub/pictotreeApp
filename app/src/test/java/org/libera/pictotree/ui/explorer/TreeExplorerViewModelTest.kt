package org.libera.pictotree.ui.explorer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import android.app.Application
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
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
        
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any()) } returns 0
        
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().setDataSource(any<Application>(), any()) } returns Unit
        every { anyConstructed<MediaPlayer>().setAudioAttributes(any()) } returns Unit
        every { anyConstructed<MediaPlayer>().prepare() } returns Unit
        every { anyConstructed<MediaPlayer>().start() } returns Unit
        every { anyConstructed<MediaPlayer>().release() } returns Unit
        
        mockkStatic(RingtoneManager::class)
        every { RingtoneManager.getDefaultUri(any()) } returns null
        
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
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
        unmockkStatic(Log::class)
        unmockkStatic(RingtoneManager::class)
        unmockkConstructor(MediaPlayer::class)
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
        runCurrent()
        
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
        runCurrent()
        
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
        assertNotEquals(phrase[0].node.id, phrase[1].node.id)
        assertTrue(phrase[0].node.id.startsWith("1_picto_r_"))
        assertEquals("Apple", phrase[0].node.label)
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
        runCurrent()
        
        // Given current tree is Tree 1
        val json1 = """{"root_node": {"id": "r1", "label": "Tree 1 Root"}}"""
        coEvery { treeDao.getTreeById(1) } returns TreeEntity(1, "Tree 1", json1)
        viewModel.loadTree(1)
        runCurrent()
        
        // When selecting an ID from Tree 2
        viewModel.selectNodeWithoutNavigatingById("2_r2_r")
        
        // Then previewNode should be updated from Tree 2
        assertEquals("2_r2_r", viewModel.uiState.value.previewNode?.id)
        assertEquals("Tree 2 Root", viewModel.uiState.value.previewNode?.label)
    }

    @Test
    fun `updateCardTimeConfig should change mode from TIMER to JALON`() = runTest {
        val node = TreeNode("1_node_1", "Test", "", emptyList())
        val card = PhraseCard(node, org.libera.pictotree.data.model.CardTimeConfig(
            mode = org.libera.pictotree.data.model.TimeMode.TIMER,
            durationMinutes = 5,
            endTimeMillis = 1000L
        ))
        viewModel.updatePhraseListSilently(listOf(card))

        val newConfig = org.libera.pictotree.data.model.CardTimeConfig(
            mode = org.libera.pictotree.data.model.TimeMode.JALON,
            durationMinutes = 0
        )
        viewModel.updateCardTimeConfig(0, newConfig)

        assertEquals(org.libera.pictotree.data.model.TimeMode.JALON, viewModel.phraseList.value[0].timeConfig.mode)
    }

    @Test
    fun `removeItemFromPhrase should schedule next card if first is timer`() = runTest {
        val node1 = TreeNode("1_node_1", "Card 1", "", emptyList())
        val card1 = PhraseCard(node1, org.libera.pictotree.data.model.CardTimeConfig(
            mode = org.libera.pictotree.data.model.TimeMode.TIMER,
            endTimeMillis = 5000L
        ))
        
        val node2 = TreeNode("1_node_2", "Card 2", "", emptyList())
        val card2 = PhraseCard(node2, org.libera.pictotree.data.model.CardTimeConfig(
            mode = org.libera.pictotree.data.model.TimeMode.TIMER,
            durationMinutes = 3,
            endTimeMillis = 0L
        ))
        
        viewModel.updatePhraseListSilently(listOf(card1, card2))
        viewModel.isTimerActivated.value = true

        viewModel.removeItemFromPhrase(0)
        
        val phrase = viewModel.phraseList.value
        assertEquals(1, phrase.size)
        assertEquals("1_node_2", phrase[0].node.id)
        assertTrue(phrase[0].timeConfig.endTimeMillis > 0L)
    }

    @Test
    fun `stopAllTimers should reset active timers`() = runTest {
        val node = TreeNode("1_node_1", "Card 1", "", emptyList())
        val card = PhraseCard(node, org.libera.pictotree.data.model.CardTimeConfig(
            mode = org.libera.pictotree.data.model.TimeMode.TIMER,
            endTimeMillis = 5000L
        ))
        viewModel.updatePhraseListSilently(listOf(card))
        viewModel.isTimerActivated.value = true
        
        viewModel.stopAllTimers()
        
        assertFalse(viewModel.isTimerActivated.value)
        assertEquals(0L, viewModel.phraseList.value[0].timeConfig.endTimeMillis)
    }

    @Test
    fun `jumpToTreeAndNode should load new tree if different and focus on node`() = runTest {
        val treeId = 42
        val nodeUniqueId = "42_leaf_r"
        
        val json = """{"root_node": {"id": "leaf", "label": "Leaf"}}"""
        coEvery { treeDao.getTreeById(treeId) } returns TreeEntity(treeId, "Tree 42", json)
        
        viewModel.jumpToTreeAndNode(treeId, nodeUniqueId)
        runCurrent()
        
        assertEquals(treeId, viewModel.getCurrentTreeId())
        assertEquals("42_leaf_r", viewModel.uiState.value.navigationNode?.id)
    }
}
