package org.libera.pictotree.ui.explorer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.libera.pictotree.data.database.dao.TreeDao
import java.io.File

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

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "TreeNode(id='$id', label='$label')"
    }
}

data class SpatialUiState(
    val center: TreeNode? = null,
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
    val isLoading: Boolean = true,
    val error: String? = null,
    val prevTreeId: Int? = null,
    val nextTreeId: Int? = null
)

class TreeExplorerViewModel(
    application: Application,
    private val treeDao: TreeDao,
    private val hostUrl: String,
    private val username: String
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PictoTreeNav"
    }

    private val _uiState = MutableStateFlow(SpatialUiState())
    val uiState: StateFlow<SpatialUiState> = _uiState.asStateFlow()

    private val _phraseList = MutableStateFlow<List<TreeNode>>(emptyList())
    val phraseList: StateFlow<List<TreeNode>> = _phraseList.asStateFlow()

    private var rootNode: TreeNode? = null
    private var currentTreeId: Int = -1
    private var profileTreeIds: List<Int> = emptyList()

    fun getProfileTreeIds(): IntArray = profileTreeIds.toIntArray()
    fun getCurrentTreeId(): Int = currentTreeId

    fun setProfileTreeContext(treeIds: List<Int>) {
        this.profileTreeIds = treeIds
    }

    fun loadTree(treeId: Int) {
        viewModelScope.launch {
            Log.d(TAG, "TREE_LOAD: Loading tree $treeId")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentTreeId = treeId
            try {
                val entity = treeDao.getTreeById(treeId)
                if (entity == null) {
                    _uiState.value = SpatialUiState(isLoading = false, error = "Arbre introuvable.")
                    return@launch
                }
                
                val rawJson = JSONObject(entity.jsonPayload)
                val rootJsonObject = getRootObject(rawJson)
                if (rootJsonObject == null) {
                    _uiState.value = SpatialUiState(isLoading = false, error = "Format invalide.")
                    return@launch
                }
                
                rootNode = parseAndSortNode(rootJsonObject, null, treeId)
                Log.d(TAG, "TREE_LOAD: Parsed successfully. Root: ${rootNode?.id}")
                rootNode?.let { focusOnNode(it) }

            } catch (e: Exception) {
                Log.e(TAG, "TREE_LOAD: Error", e)
                _uiState.value = SpatialUiState(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    private fun getRootObject(rawJson: JSONObject): JSONObject? {
        return if (rawJson.has("root_node")) rawJson.getJSONObject("root_node")
        else if (rawJson.has("roots") && rawJson.getJSONArray("roots").length() > 0) rawJson.getJSONArray("roots").getJSONObject(0)
        else null
    }

    private fun parseAndSortNode(json: JSONObject, parentRef: TreeNode?, treeId: Int): TreeNode {
        val rawId = json.optString("node_id", json.optString("id", "unsaved"))
        val id = "${treeId}_$rawId"
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

        val childrenList = mutableListOf<TreeNode>()
        val childrenArray = json.optJSONArray("children")
        if (childrenArray != null) {
            for (i in 0 until childrenArray.length()) {
                val childJson = childrenArray.optJSONObject(i)
                if (childJson != null) childrenList.add(parseAndSortNode(childJson, null, treeId))
            }
        }

        val node = TreeNode(id = id, label = label, imageUrl = rawUrl, children = childrenList, parent = parentRef)
        childrenList.forEach { it.parent = node }
        return node
    }

    fun focusOnNode(node: TreeNode) {
        Log.d(TAG, "NAV_MOVE: Focused on ${node.id} (${node.label}). Tree: $currentTreeId")
        val navState = TreeNavigator.computeSpatialState(node, profileTreeIds, currentTreeId)
        
        Log.d(TAG, "NAV_MAP: T:${navState.top?.id} | B:${navState.bottom?.id} | L:${navState.left?.id} | R:${navState.right?.id}")

        viewModelScope.launch {
            val finalLeft = navState.left ?: navState.prevTreeId?.let { id -> fetchRootNodePreview(id) }
            val finalRight = navState.right ?: navState.nextTreeId?.let { id -> fetchRootNodePreview(id) }

            _uiState.value = SpatialUiState(
                isLoading = false, center = navState.center, top = navState.top, bottom = navState.bottom,
                left = finalLeft, right = finalRight, microTop = navState.microTop,
                microTopCount = navState.microTopCount, microLeft = navState.microLeft,
                microLeftCount = navState.microLeftCount, microRight = navState.microRight,
                microRightCount = navState.microRightCount, prevTreeId = navState.prevTreeId,
                nextTreeId = navState.nextTreeId
            )
        }
    }

    private suspend fun fetchRootNodePreview(treeId: Int): TreeNode? {
        return treeDao.getTreeById(treeId)?.let { entity ->
            val json = JSONObject(entity.jsonPayload)
            getRootObject(json)?.let { parseAndSortNode(it, null, treeId) }
        }
    }

    fun addToPhrase(externalNode: TreeNode? = null) {
        val nodeToAdd = externalNode ?: _uiState.value.center ?: return
        Log.d(TAG, "ACTION: Basket Add -> ${nodeToAdd.id}")
        val currentList = _phraseList.value.toMutableList()
        currentList.add(nodeToAdd)
        _phraseList.value = currentList
    }

    fun addToPhraseById(prefixedId: String): Boolean {
        Log.d(TAG, "ACTION: Basket Add By ID -> $prefixedId")
        val target = findNodeRecursively(rootNode, prefixedId)
        if (target != null) {
            addToPhrase(target)
            return true
        }
        return false
    }

    fun jumpToNodeId(prefixedId: String): Boolean {
        Log.d(TAG, "NAV_JUMP: Target ID $prefixedId")
        val target = findNodeRecursively(rootNode, prefixedId)
        if (target != null) {
            Log.d(TAG, "NAV_JUMP: Found in current tree. Focusing.")
            focusOnNode(target)
            return true
        }

        val parts = prefixedId.split("_", limit = 2)
        if (parts.size == 2) {
            val treeId = parts[0].toIntOrNull()
            if (treeId != null && profileTreeIds.contains(treeId)) {
                Log.d(TAG, "NAV_JUMP: Found in Tree $treeId. Loading tree.")
                jumpToTreeAndNode(treeId, prefixedId)
                return true
            }
        }
        Log.w(TAG, "NAV_JUMP: FAILED to find $prefixedId")
        return false
    }

    fun jumpToTreeAndNode(treeId: Int, prefixedId: String, addToBasket: Boolean = false) {
        Log.d(TAG, "NAV_SYNC: Tree=$treeId Node=$prefixedId Basket=$addToBasket")
        viewModelScope.launch {
            if (currentTreeId != treeId) {
                Log.d(TAG, "NAV_SYNC: Switching tree $currentTreeId -> $treeId")
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                currentTreeId = treeId
                val entity = treeDao.getTreeById(treeId)
                if (entity != null) {
                    val rawJson = JSONObject(entity.jsonPayload)
                    val rootJsonObject = getRootObject(rawJson)
                    if (rootJsonObject != null) rootNode = parseAndSortNode(rootJsonObject, null, treeId)
                }
            }
            
            val targetNode = findNodeRecursively(rootNode, prefixedId) ?: rootNode
            Log.d(TAG, "NAV_SYNC: Result node ${targetNode?.id}")
            targetNode?.let {
                focusOnNode(it)
                if (addToBasket) addToPhrase(it)
            }
        }
    }

    private fun findNodeRecursively(current: TreeNode?, targetId: String): TreeNode? {
        if (current == null) return null
        if (current.id == targetId) return current
        for (child in current.children) {
            val found = findNodeRecursively(child, targetId)
            if (found != null) return found
        }
        return null
    }

    fun clearPhrase() {
        Log.d(TAG, "ACTION: Phrase cleared")
        _phraseList.value = emptyList()
    }
}
