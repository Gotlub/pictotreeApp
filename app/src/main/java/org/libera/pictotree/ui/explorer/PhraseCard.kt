package org.libera.pictotree.ui.explorer

import org.libera.pictotree.data.model.CardTimeConfig

/**
 * Wrapper pour les éléments du bandeau de phrase (PhraseAdapter).
 * Permet d'associer un TreeNode à une configuration de temps.
 */
data class PhraseCard(
    val node: TreeNode,
    val timeConfig: CardTimeConfig = CardTimeConfig(),
    var isSelectedForConfig: Boolean = false
)
