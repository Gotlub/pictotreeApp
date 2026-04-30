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
    private val userConfigRepository: org.libera.pictotree.data.repository.UserConfigRepository,
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

    // Configuration réactive (locale, pin, etc.)
    val userConfig = userConfigRepository.userConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private var rootNode: TreeNode? = null
    private var currentTreeId: Int = -1
    private var profileTreeIds: List<Int> = emptyList()

    fun getProfileTreeIds(): IntArray = profileTreeIds.toIntArray()
    fun getCurrentTreeId(): Int = currentTreeId
    fun setProfileTreeContext(treeIds: List<Int>) { this.profileTreeIds = treeIds }

    fun loadTree(treeId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentTreeId = treeId
            try {
                treeDao.getTreeById(treeId)?.let { entity ->
                    val rawJson = JSONObject(entity.jsonPayload)
                    getRootObject(rawJson)?.let { rootObj ->
                        rootNode = parseAndSortNode(rootObj, null, treeId, "r")
                        rootNode?.let { focusOnNode(it) }
                    } ?: run { _uiState.value = SpatialUiState(isLoading = false, error = "Format invalide.") }
                } ?: run { _uiState.value = SpatialUiState(isLoading = false, error = "Arbre introuvable.") }
            } catch (e: Exception) {
                _uiState.value = SpatialUiState(isLoading = false, error = e.localizedMessage)
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
        val navState = TreeNavigator.computeSpatialState(node, profileTreeIds, currentTreeId)
        viewModelScope.launch {
            val finalLeft = navState.left ?: navState.prevTreeId?.let { fetchRootNodePreview(it) }
            val finalRight = navState.right ?: navState.nextTreeId?.let { fetchRootNodePreview(it) }
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
            getRootObject(JSONObject(entity.jsonPayload))?.let { parseAndSortNode(it, null, treeId, "r") }
        }
    }

    fun addToPhrase(externalNode: TreeNode? = null) {
        val nodeToAdd = externalNode ?: _uiState.value.center ?: return
        _phraseList.value = _phraseList.value + nodeToAdd
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
            if (currentTreeId != treeId) {
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

    fun removeItemFromPhrase(position: Int) {
        val list = _phraseList.value.toMutableList()
        if (position in list.indices) {
            list.removeAt(position)
            _phraseList.value = list
        }
    }
}
