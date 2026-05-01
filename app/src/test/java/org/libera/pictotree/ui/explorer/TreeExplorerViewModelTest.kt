package org.libera.pictotree.ui.explorer

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.repository.UserConfigRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TreeExplorerViewModelTest {

    private lateinit var viewModel: TreeExplorerViewModel
    private val application = mockk<Application>(relaxed = true)
    private val treeDao = mockk<TreeDao>()
    private val userConfigRepository = mockk<UserConfigRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userConfigRepository.userConfig } returns flowOf(null)
        
        viewModel = TreeExplorerViewModel(
            application,
            treeDao,
            userConfigRepository,
            "http://localhost",
            "testuser"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addToPhrase adds node to phrase list`() {
        val node = TreeNode("1_101_r", "Test", "", emptyList())
        
        viewModel.addToPhrase(node)
        
        assertEquals(1, viewModel.phraseList.value.size)
        assertEquals(node, viewModel.phraseList.value[0])
    }

    @Test
    fun `moveItemInPhrase changes item order`() {
        val node1 = TreeNode("1_101_r", "Node 1", "", emptyList())
        val node2 = TreeNode("1_102_r", "Node 2", "", emptyList())
        viewModel.addToPhrase(node1)
        viewModel.addToPhrase(node2)
        
        viewModel.moveItemInPhrase(0, 1)
        
        assertEquals(node2, viewModel.phraseList.value[0])
        assertEquals(node1, viewModel.phraseList.value[1])
    }

    @Test
    fun `removeItemFromPhrase removes correct item`() {
        val node1 = TreeNode("1_101_r", "Node 1", "", emptyList())
        val node2 = TreeNode("1_102_r", "Node 2", "", emptyList())
        viewModel.addToPhrase(node1)
        viewModel.addToPhrase(node2)
        
        viewModel.removeItemFromPhrase(0)
        
        assertEquals(1, viewModel.phraseList.value.size)
        assertEquals(node2, viewModel.phraseList.value[0])
    }

    @Test
    fun `clearPhrase empties the phrase list`() {
        val node = TreeNode("1_101_r", "Node 1", "", emptyList())
        viewModel.addToPhrase(node)
        
        viewModel.clearPhrase()
        
        assertEquals(0, viewModel.phraseList.value.size)
    }
}
