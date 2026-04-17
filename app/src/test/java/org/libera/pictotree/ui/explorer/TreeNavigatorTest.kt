package org.libera.pictotree.ui.explorer

import org.junit.Assert.*
import org.junit.Test

class TreeNavigatorTest {

    @Test
    fun `test unique path-based IDs with duplicate pictograms`() {
        val treeId = 6
        val imgId = "529"
        
        // Root path is "r"
        val root = TreeNode("${treeId}_root_r", "Root", "", emptyList())
        
        // Children paths are "r_0", "r_1"
        val nodeA = TreeNode("${treeId}_${imgId}_r_0", "aabb", "", emptyList(), root)
        val nodeB = TreeNode("${treeId}_${imgId}_r_1", "ccdd", "", emptyList(), root)
        
        val rootWithChildren = TreeNode("${treeId}_root_r", "Root", "", listOf(nodeA, nodeB))
        nodeA.parent = rootWithChildren
        nodeB.parent = rootWithChildren

        assertNotEquals("IDs must be unique even for same image", nodeA.id, nodeB.id)
        assertEquals("r_0", TreeNode.parsePath(nodeA.id))
        assertEquals("r_1", TreeNode.parsePath(nodeB.id))
        assertEquals(treeId, TreeNode.parseTreeId(nodeA.id))
        assertEquals(imgId, TreeNode.parseNodeId(nodeA.id))
    }

    @Test
    fun `test spatial navigation with duplicate images using referential equality`() {
        val treeId = 6
        val imgId = "529"
        
        val nodeA = TreeNode("${treeId}_${imgId}_r_0", "aabb", "", emptyList())
        val nodeB = TreeNode("${treeId}_${imgId}_r_1", "ccdd", "", emptyList())
        val root = TreeNode("${treeId}_root_r", "Root", "", listOf(nodeA, nodeB))
        
        nodeA.parent = root
        nodeB.parent = root

        val stateA = TreeNavigator.computeSpatialState(nodeA)
        assertEquals(nodeB, stateA.right)
        assertNull(stateA.left)

        val stateB = TreeNavigator.computeSpatialState(nodeB)
        assertEquals(nodeA, stateB.left)
        assertNull(stateB.right)
    }
}
