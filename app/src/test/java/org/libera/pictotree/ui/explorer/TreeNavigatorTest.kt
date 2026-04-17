package org.libera.pictotree.ui.explorer

import org.junit.Assert.*
import org.junit.Test

class TreeNavigatorTest {

    @Test
    fun `test unique path-based IDs with duplicate pictograms`() {
        // Mock nodes with same image ID but different positions
        val treeId = 6
        val imgId = "529"
        
        // Simulating the logic from parseAndSortNode
        val root = TreeNode("${treeId}_root_r", "Root", "", emptyList())
        
        val nodeA = TreeNode("${treeId}_${imgId}_r_0", "aabb", "", emptyList())
        val nodeB = TreeNode("${treeId}_${imgId}_r_1", "ccdd", "", emptyList())
        
        nodeA.parent = root
        nodeB.parent = root
        
        // Manual construction for test (instead of full parsing logic)
        val rootWithChildren = TreeNode("${treeId}_root_r", "Root", "", listOf(nodeA, nodeB))
        nodeA.parent = rootWithChildren
        nodeB.parent = rootWithChildren

        assertNotEquals("IDs must be unique even for same image", nodeA.id, nodeB.id)
        assertTrue(nodeA.id.contains("r_0"))
        assertTrue(nodeB.id.contains("r_1"))
    }

    @Test
    fun `test spatial navigation with duplicate images using referential equality`() {
        val treeId = 6
        val imgId = "529"
        
        // Path-based unique IDs
        val nodeA = TreeNode("${treeId}_${imgId}_r_0", "aabb", "", emptyList())
        val nodeB = TreeNode("${treeId}_${imgId}_r_1", "ccdd", "", emptyList())
        val root = TreeNode("${treeId}_root_r", "Root", "", listOf(nodeA, nodeB))
        
        nodeA.parent = root
        nodeB.parent = root

        // Navigate to nodeA
        val stateA = TreeNavigator.computeSpatialState(nodeA)
        assertEquals("aabb", stateA.center.label)
        assertEquals(nodeB, stateA.right)
        assertNull(stateA.left)

        // Navigate to nodeB
        val stateB = TreeNavigator.computeSpatialState(nodeB)
        assertEquals("ccdd", stateB.center.label)
        assertEquals(nodeA, stateB.left)
        assertNull(stateB.right)
    }

    @Test
    fun `test inter-tree navigation logic`() {
        val treeIds = listOf(1, 2, 3)
        val rootNode = TreeNode("1_root_r", "Root Tree 1", "", emptyList())
        
        val state = TreeNavigator.computeSpatialState(rootNode, profileTreeIds = treeIds, currentTreeId = 1)
        
        assertNull("Tree 1 has no previous tree", state.prevTreeId)
        assertEquals(2, state.nextTreeId)
    }
}
