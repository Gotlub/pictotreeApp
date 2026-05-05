package org.libera.pictotree.ui.explorer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.dao.ProfileDao
import java.io.File

/**
 * Représente un noeud de l'arbre avec un ID unique au monde (treeId_nodeId_path).
 */
class TreeNode(
    val id: String,
    val label: String,
    val imageUrl: String,
    val children: List<TreeNode>,
    var parent: TreeNode? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TreeNode) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "TreeNode(id='$id', label='$label')"

    fun copy(
        id: String = this.id, label: String = this.label, imageUrl: String = this.imageUrl,
        children: List<TreeNode> = this.children, parent: TreeNode? = this.parent
    ): TreeNode = TreeNode(id, label, imageUrl, children, parent)

    /**
     * Extrait les composants de l'ID unique (treeId_nodeId_path)
     */
    companion object {
        fun parseTreeId(uniqueId: String): Int? = uniqueId.split("_").firstOrNull()?.toIntOrNull()
        fun parseNodeId(uniqueId: String): String? = uniqueId.split("_").getOrNull(1)
        fun parsePath(uniqueId: String): String? = uniqueId.split("_").drop(2).joinToString("_").takeIf { it.isNotEmpty() }
    }
}

data class HierarchicalUiState(
    val breadcrumbs: List<TreeNode> = emptyList(),
    val parent: TreeNode? = null,
    val siblings: List<TreeNode> = emptyList(),
    val children: List<TreeNode> = emptyList(),
    val focusedNode: TreeNode? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val colorCode: String = "#000000"
)

class TreeExplorerViewModel(
    application: Application,
    private val treeDao: TreeDao,
    private val profileDao: ProfileDao,
    private val userConfigRepository: org.libera.pictotree.data.repository.UserConfigRepository,
    private val hostUrl: String,
    private val username: String
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PictoTreeNav"
    }

    private val _uiState = MutableStateFlow(HierarchicalUiState())
    val uiState: StateFlow<HierarchicalUiState> = _uiState.asStateFlow()

    private val _phraseList = MutableStateFlow<List<TreeNode>>(emptyList())
    val phraseList: StateFlow<List<TreeNode>> = _phraseList.asStateFlow()

    // Configuration réactive (locale, pin, etc.)
    val userConfig = userConfigRepository.userConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private var rootNode: TreeNode? = null
    private var currentTreeId: Int = -1
    private var profileId: Int = -1
    private var profileTreeIds: List<Int> = emptyList()
    private val profileTreeRootsCache = mutableMapOf<Int, TreeNode>()
    private val profileTreeColorsCache = mutableMapOf<Int, String>()

    fun getProfileTreeIds(): IntArray = profileTreeIds.toIntArray()
    fun getCurrentTreeId(): Int = currentTreeId
    
    /**
     * Met à jour le contexte de l'arbre actuel (ID et Couleur) 
     * sans recalculer toute la hiérarchie de l'explorateur.
     */
    fun updateCurrentTreeContext(treeId: Int) {
        if (currentTreeId == treeId) return
        currentTreeId = treeId
        val newColor = profileTreeColorsCache[treeId] ?: "#000000"
        _uiState.value = _uiState.value.copy(colorCode = newColor)
    }

    fun setProfileTreeContext(profileId: Int, treeIds: List<Int>) { 
        this.profileId = profileId
        this.profileTreeIds = treeIds 
        // Pré-charger les racines et les couleurs pour la navigation inter-arbres
        viewModelScope.launch {
            treeIds.forEach { id ->
                if (!profileTreeColorsCache.containsKey(id)) {
                    val color = profileDao.getProfileTreeCrossRef(profileId, id)?.colorCode ?: "#000000"
                    profileTreeColorsCache[id] = color
                }
                if (!profileTreeRootsCache.containsKey(id)) {
                    fetchRootNodePreview(id)?.let { profileTreeRootsCache[id] = it }
                }
            }
        }
    }

    fun loadTree(treeId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentTreeId = treeId

            // Récupérer la couleur CAA pour cet arbre (via cache ou DB)
            val color = profileTreeColorsCache[treeId] ?: if (profileId != -1) {
                val dbColor = profileDao.getProfileTreeCrossRef(profileId, treeId)?.colorCode ?: "#000000"
                profileTreeColorsCache[treeId] = dbColor
                dbColor
            } else "#000000"
            _uiState.value = _uiState.value.copy(colorCode = color)

            try {
                treeDao.getTreeById(treeId)?.let { entity ->
                    val rawJson = JSONObject(entity.jsonPayload)
                    getRootObject(rawJson)?.let { rootObj ->
                        val parsedRoot = parseAndSortNode(rootObj, null, treeId, "r")
                        rootNode = parsedRoot
                        profileTreeRootsCache[treeId] = parsedRoot
                        
                        // Logique demandée : focus sur le premier enfant du root pour que le root soit le "parent"
                        val firstChild = parsedRoot.children.firstOrNull()
                        if (firstChild != null) {
                            focusOnNode(firstChild)
                        } else {
                            focusOnNode(parsedRoot)
                        }
                    } ?: run { _uiState.value = HierarchicalUiState(isLoading = false, error = "Format invalide.") }
                } ?: run { _uiState.value = HierarchicalUiState(isLoading = false, error = "Arbre introuvable.") }
            } catch (e: Exception) {
                _uiState.value = HierarchicalUiState(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    private fun getRootObject(rawJson: JSONObject): JSONObject? {
        return if (rawJson.has("root_node")) rawJson.getJSONObject("root_node")
        else if (rawJson.has("roots") && rawJson.getJSONArray("roots").length() > 0) rawJson.getJSONArray("roots").getJSONObject(0)
        else null
    }

    private fun parseAndSortNode(json: JSONObject, parentRef: TreeNode?, treeId: Int, path: String): TreeNode {
        val rawId = json.optString("node_id", json.optString("id", "unsaved"))
        val id = "${treeId}_${rawId}_$path"
        val label = json.optString("label", json.optString("text", json.optString("name", "Sans Titre")))
        var rawUrl = json.optString("image_url", json.optString("image", json.optString("url", "")))

        if (rawUrl.isNotEmpty() && !rawUrl.startsWith("http") && !rawUrl.startsWith("file")) {
            val normPath = rawUrl.replace("^/+".toRegex(), "").replace("^(pictograms/|images/)".toRegex(), "")
            if (normPath.startsWith("public/") || normPath.startsWith("$username/")) {
                rawUrl = "$hostUrl/api/v1/mobile/pictograms/$normPath"
            }
        }

        if (rawUrl.isNotEmpty() && !rawUrl.startsWith("file")) {
            val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(rawUrl)
            val localFile = File(getApplication<Application>().filesDir, "$username/images/$fileName")
            if (localFile.exists()) rawUrl = "file://${localFile.absolutePath}"
        }

        val childrenList = (0 until (json.optJSONArray("children")?.length() ?: 0)).mapNotNull { i ->
            json.optJSONArray("children")?.optJSONObject(i)?.let { parseAndSortNode(it, null, treeId, "${path}_$i") }
        }

        val node = TreeNode(id, label, rawUrl, childrenList, parentRef)
        childrenList.forEach { it.parent = node }
        return node
    }

    fun focusOnNode(node: TreeNode) {
        Log.d(TAG, "NAV_MOVE: ${node.id} (${node.label})")
        
        // S'assurer que les racines sont prêtes pour l'inter-arbre
        val roots = profileTreeIds.mapNotNull { profileTreeRootsCache[it] }
        
        // Mettre à jour la couleur si on a changé d'arbre (navigation inter-arbres)
        TreeNode.parseTreeId(node.id)?.let { treeId ->
            updateCurrentTreeContext(treeId)
        }
        
        val newState = TreeNavigator.computeHierarchicalState(node, roots)
        _uiState.value = _uiState.value.copy(
            breadcrumbs = newState.breadcrumbs,
            parent = newState.parent,
            siblings = newState.siblings,
            children = newState.children,
            focusedNode = newState.focusedNode,
            isLoading = false
        )
    }

    /**
     * Met à jour uniquement le noeud focus (quand on scrolle dans le carrousel)
     * sans recalculer toute la hiérarchie.
     */
    fun updateFocusWithinSiblings(node: TreeNode) {
        if (_uiState.value.focusedNode?.id == node.id) return
        
        // Mettre à jour la couleur si on a changé d'arbre
        TreeNode.parseTreeId(node.id)?.let { treeId ->
            updateCurrentTreeContext(treeId)
        }

        _uiState.value = _uiState.value.copy(
            focusedNode = node,
            children = node.children
        )
    }

    /**
     * Sélectionne un noeud (ex: un enfant sans sous-enfants) 
     * pour mise à jour de la preview "Ajouter" sans changer de niveau.
     */
    fun selectNodeWithoutNavigating(node: TreeNode) {
        _uiState.value = _uiState.value.copy(focusedNode = node)
    }

    private suspend fun fetchRootNodePreview(treeId: Int): TreeNode? {
        return treeDao.getTreeById(treeId)?.let { entity ->
            getRootObject(JSONObject(entity.jsonPayload))?.let { parseAndSortNode(it, null, treeId, "r") }
        }
    }

    fun addToPhrase(externalNode: TreeNode? = null) {
        val nodeToAdd = externalNode ?: _uiState.value.focusedNode ?: return
        // On crée une copie avec un ID d'instance unique (doublons autorisés mais distincts techniquement)
        val uniqueInstanceNode = nodeToAdd.copy(
            id = "${nodeToAdd.id}_${System.currentTimeMillis()}_${(0..999).random()}"
        )
        _phraseList.value = _phraseList.value + uniqueInstanceNode
    }

    fun addToPhraseById(uniqueId: String): Boolean {
        return findNodeRecursively(rootNode, uniqueId)?.let { addToPhrase(it); true } ?: false
    }

    fun jumpToNodeId(uniqueId: String): Boolean {
        findNodeRecursively(rootNode, uniqueId)?.let { focusOnNode(it); return true }
        TreeNode.parseTreeId(uniqueId)?.let { treeId ->
            if (profileTreeIds.contains(treeId)) { jumpToTreeAndNode(treeId, uniqueId); return true }
        }
        return false
    }

    fun jumpToTreeAndNode(treeId: Int, uniqueId: String, addToBasket: Boolean = false) {
        viewModelScope.launch {
            val loadedTreeId = rootNode?.let { TreeNode.parseTreeId(it.id) } ?: -1
            if (loadedTreeId != treeId) {
                // FORCER le reload si l'ID ne match pas la racine physiquement chargée
                currentTreeId = treeId
                _uiState.value = _uiState.value.copy(isLoading = true)
                treeDao.getTreeById(treeId)?.let { entity ->
                    getRootObject(JSONObject(entity.jsonPayload))?.let { rootNode = parseAndSortNode(it, null, treeId, "r") }
                }
            }
            val target = findNodeRecursively(rootNode, uniqueId) ?: rootNode
            target?.let { focusOnNode(it); if (addToBasket) addToPhrase(it) }
        }
    }

    private fun findNodeRecursively(current: TreeNode?, targetId: String): TreeNode? {
        if (current == null || current.id == targetId) return current
        for (child in current.children) {
            findNodeRecursively(child, targetId)?.let { return it }
        }
        return null
    }

    fun clearPhrase() { _phraseList.value = emptyList() }

    fun moveItemInPhrase(from: Int, to: Int) {
        val list = _phraseList.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _phraseList.value = list
        }
    }

    /**
     * Met à jour la liste sans déclencher de nouvel état (utilisé en fin de Drag & Drop)
     */
    fun updatePhraseListSilently(newList: List<TreeNode>) {
        _phraseList.value = newList
    }

    fun removeItemFromPhrase(position: Int) {
        val list = _phraseList.value.toMutableList()
        if (position in list.indices) {
            list.removeAt(position)
            _phraseList.value = list
        }
    }
}
