package org.libera.pictotree.ui.explorer

/**
 * Moteur de calcul spatial pour la navigation dans l'arbre.
 * Extrait du ViewModel pour être réutilisable (ex: vue Treant.js).
 */
object TreeNavigator {

    /**
     * Calcule l'état hiérarchique pour un noeud donné.
     */
    fun computeHierarchicalState(
        currentNode: TreeNode,
        profileTreeRoots: List<TreeNode> = emptyList()
    ): HierarchicalUiState {
        val parent = currentNode.parent
        val siblings = parent?.children ?: profileTreeRoots
        
        // Calcul du fil d'Ariane (Breadcrumbs)
        val breadcrumbs = mutableListOf<TreeNode>()
        var p = parent?.parent
        while (p != null) {
            breadcrumbs.add(0, p)
            p = p.parent
        }

        return HierarchicalUiState(
            breadcrumbs = breadcrumbs,
            parent = parent,
            siblings = siblings,
            children = currentNode.children,
            focusedNode = currentNode
        )
    }

    data class HierarchicalUiState(
        val breadcrumbs: List<TreeNode> = emptyList(),
        val parent: TreeNode? = null,
        val siblings: List<TreeNode> = emptyList(),
        val children: List<TreeNode> = emptyList(),
        val focusedNode: TreeNode
    )
}
