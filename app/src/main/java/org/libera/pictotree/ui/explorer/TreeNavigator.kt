package org.libera.pictotree.ui.explorer

/**
 * Moteur de calcul spatial pour la navigation dans l'arbre.
 * Extrait du ViewModel pour être réutilisable (ex: vue Treant.js).
 */
object TreeNavigator {

    /**
     * Calcule l'état de la croix directionnelle pour un noeud donné.
     */
    fun computeSpatialState(
        currentNode: TreeNode,
        profileTreeIds: List<Int> = emptyList(),
        currentTreeId: Int = -1
    ): NeighborState {
        val parent = currentNode.parent
        // Utiliser l'égalité référentielle (===) pour distinguer les doublons de pictos
        val myIndex = parent?.children?.indexOfFirst { it === currentNode } ?: 0
        
        val leftSibling = if (parent != null && myIndex > 0) parent.children[myIndex - 1] else null
        val rightSibling = if (parent != null && myIndex < parent.children.size - 1) parent.children[myIndex + 1] else null
        val topNode = parent
        val bottomNode = currentNode.children.firstOrNull()

        var prevTreeId: Int? = null
        var nextTreeId: Int? = null

        // Navigation Inter-Arbres (uniquement à la racine)
        if (parent == null && profileTreeIds.isNotEmpty()) {
            val currentIdx = profileTreeIds.indexOf(currentTreeId)
            if (currentIdx > 0) prevTreeId = profileTreeIds[currentIdx - 1]
            if (currentIdx != -1 && currentIdx < profileTreeIds.size - 1) nextTreeId = profileTreeIds[currentIdx + 1]
        }

        // Lookahead (Micro-miniatures)
        val microLeftCount = if (parent != null) Math.max(0, myIndex - 1) else 0
        val microLeft = if (microLeftCount > 0) parent!!.children[myIndex - 2] else null
        
        val microRightCount = if (parent != null) Math.max(0, parent.children.size - 1 - myIndex - 1) else 0
        val microRight = if (microRightCount > 0) parent!!.children[myIndex + 2] else null
        
        var depthCount = 0
        var currentAncestry = parent?.parent
        val grandParentNode: TreeNode? = currentAncestry
        while (currentAncestry != null) {
            depthCount++
            currentAncestry = currentAncestry.parent
        }

        return NeighborState(
            center = currentNode,
            top = topNode,
            bottom = bottomNode,
            left = leftSibling,
            right = rightSibling,
            microTop = grandParentNode,
            microTopCount = depthCount,
            microLeft = microLeft,
            microLeftCount = microLeftCount,
            microRight = microRight,
            microRightCount = microRightCount,
            prevTreeId = prevTreeId,
            nextTreeId = nextTreeId
        )
    }

    data class NeighborState(
        val center: TreeNode,
        val top: TreeNode? = null,
        val bottom: TreeNode? = null,
        val left: TreeNode? = null,
        val right: TreeNode? = null,
        val microTop: TreeNode? = null,
        val microTopCount: Int = 0,
        val microLeft: TreeNode? = null,
        val microLeftCount: Int = 0,
        val microRight: TreeNode? = null,
        val microRightCount: Int = 0,
        val prevTreeId: Int? = null,
        val nextTreeId: Int? = null
    )
}
