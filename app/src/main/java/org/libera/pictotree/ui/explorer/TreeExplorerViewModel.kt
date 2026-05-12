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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.dao.ProfileDao
import java.io.File
import com.google.gson.Gson

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
    
    // NAVIGATION vs PREVIEW
    val navigationNode: TreeNode? = null, // Le noeud autour duquel on navigue
    val previewNode: TreeNode? = null,    // Le noeud affiché dans la preview "Ajouter"
    
    val phraseSize: Int = 1, // 0: Small, 1: Medium, 2: Large
    
    val isLoading: Boolean = true,
    val error: String? = null,
    val colorCode: String = "#000000"
)

class TreeExplorerViewModel(
    application: Application,
    private val treeDao: TreeDao,
    private val profileDao: ProfileDao,
    private val imageDao: org.libera.pictotree.data.database.dao.ImageDao,
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
    
    fun updateCurrentTreeContext(treeId: Int) {
        if (currentTreeId == treeId) return
        currentTreeId = treeId
        val newColor = profileTreeColorsCache[treeId] ?: "#000000"
        _uiState.value = _uiState.value.copy(colorCode = newColor)
    }

    fun setProfileTreeContext(profileId: Int, treeIds: List<Int>) { 
        this.profileId = profileId
        this.profileTreeIds = treeIds 
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

    /**
     * Charge un arbre. 
     * SI l'arbre est déjà chargé (ex: rotation), on ignore pour garder l'état.
     */
    fun loadTree(treeId: Int) {
        if (currentTreeId == treeId && rootNode != null) {
            Log.d(TAG, "LOAD_TREE: Already loaded Tree $treeId, skipping reset (Survive Rotation)")
            return 
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentTreeId = treeId

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
                        
                        val firstChild = parsedRoot.children.firstOrNull()
                        val startNode = firstChild ?: parsedRoot
                        
                        // Si on n'a pas de sélection (ex: retour de l'inventaire), 
                        // on initialise la preview sur le point d'entrée
                        if (_uiState.value.previewNode == null) {
                            focusOnNode(startNode, updatePreview = true)
                        } else {
                            focusOnNode(startNode, updatePreview = false)
                        }
                    } ?: run { _uiState.value = _uiState.value.copy(isLoading = false, error = "Format invalide.") }
                } ?: run { _uiState.value = _uiState.value.copy(isLoading = false, error = "Arbre introuvable.") }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
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

    /**
     * @param updatePreview Si true, ce noeud devient la nouvelle sélection pour le panier.
     */
    fun focusOnNode(node: TreeNode, updatePreview: Boolean = true) {
        Log.d(TAG, "NAV_MOVE: ${node.id} (UpdatePreview: $updatePreview)")
        val roots = profileTreeIds.mapNotNull { profileTreeRootsCache[it] }
        
        TreeNode.parseTreeId(node.id)?.let { treeId ->
            updateCurrentTreeContext(treeId)
        }
        
        val newState = TreeNavigator.computeHierarchicalState(node, roots)
        _uiState.value = _uiState.value.copy(
            breadcrumbs = newState.breadcrumbs,
            parent = newState.parent,
            siblings = newState.siblings,
            children = newState.children,
            navigationNode = node,
            previewNode = if (updatePreview) node else _uiState.value.previewNode,
            isLoading = false
        )
    }

    fun updateFocusWithinSiblings(node: TreeNode) {
        // Même si on ne change pas de niveau de navigation, on veut que ce noeud devienne la preview
        if (_uiState.value.navigationNode?.id == node.id) {
            if (_uiState.value.previewNode?.id != node.id) {
                _uiState.value = _uiState.value.copy(previewNode = node)
            }
            return
        }
        
        TreeNode.parseTreeId(node.id)?.let { treeId ->
            updateCurrentTreeContext(treeId)
        }

        _uiState.value = _uiState.value.copy(
            navigationNode = node,
            previewNode = node,
            children = node.children
        )
    }

    fun selectNodeWithoutNavigating(node: TreeNode) {
        _uiState.value = _uiState.value.copy(previewNode = node)
    }

    /**
     * Sélectionne un noeud via son ID (venant de Treant.js ou recherche)
     * On cherche d'abord dans l'arbre actif, puis dans le cache des autres racines.
     */
    fun selectNodeWithoutNavigatingById(uniqueId: String) {
        // 1. Chercher dans l'arbre actuellement chargé
        var target = findNodeRecursively(rootNode, uniqueId)
        
        // 2. Si pas trouvé, chercher dans le cache des racines (cas navigation inter-arbres Treant)
        if (target == null) {
            val treeId = TreeNode.parseTreeId(uniqueId) ?: -1
            target = findNodeRecursively(profileTreeRootsCache[treeId], uniqueId)
        }

        target?.let { node ->
            selectNodeWithoutNavigating(node)
        } ?: run {
            Log.w(TAG, "SELECT_BY_ID: Node $uniqueId NOT FOUND in current or cached trees")
        }
    }

    /**
     * Appelé lors du retour au menu Inventaire (TreeSelectionFragment)
     */
    fun resetSelection() {
        _uiState.value = _uiState.value.copy(previewNode = null)
    }

    fun updatePhraseSize(size: Int) {
        if (size in 0..2) {
            _uiState.value = _uiState.value.copy(phraseSize = size)
        }
    }

    private suspend fun fetchRootNodePreview(treeId: Int): TreeNode? {
        return treeDao.getTreeById(treeId)?.let { entity ->
            getRootObject(JSONObject(entity.jsonPayload))?.let { parseAndSortNode(it, null, treeId, "r") }
        }
    }

    fun addToPhrase(externalNode: TreeNode? = null) {
        val nodeToAdd = externalNode ?: _uiState.value.previewNode ?: return
        
        val uniqueInstanceNode = nodeToAdd.copy(
            id = "${nodeToAdd.id}_${System.currentTimeMillis()}_${(0..999).random()}"
        )
        
        if (nodeToAdd.imageUrl.startsWith("http") || nodeToAdd.imageUrl.contains("/api/v1/mobile/")) {
            viewModelScope.launch {
                val sessionManager = org.libera.pictotree.data.SessionManager(getApplication())
                val token = sessionManager.getToken()
                val engine = org.libera.pictotree.data.repository.ImageSyncEngine(getApplication(), imageDao, username, hostUrl, token)
                engine.downloadSingleImage(nodeToAdd.imageUrl, nodeToAdd.label)
            }
        }
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
                currentTreeId = treeId
                _uiState.value = _uiState.value.copy(isLoading = true)
                treeDao.getTreeById(treeId)?.let { entity ->
                    getRootObject(JSONObject(entity.jsonPayload))?.let { rootNode = parseAndSortNode(it, null, treeId, "r") }
                }
            }
            val target = findNodeRecursively(rootNode, uniqueId) ?: rootNode
            target?.let { 
                focusOnNode(it, updatePreview = true) 
                if (addToBasket) addToPhrase(it) 
            }
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
