package org.libera.pictotree.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.libera.pictotree.data.database.dao.TreeDao
import java.io.File

data class TreeNode(
    val id: String,
    val label: String,
    val imageUrl: String,
    val children: List<TreeNode>,
    var parent: TreeNode? = null
)

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

    private val _uiState = MutableStateFlow(SpatialUiState())
    val uiState: StateFlow<SpatialUiState> = _uiState.asStateFlow()

    private val _phraseList = MutableStateFlow<List<TreeNode>>(emptyList())
    val phraseList: StateFlow<List<TreeNode>> = _phraseList.asStateFlow()

    private var rootNode: TreeNode? = null
    private var currentTreeId: Int = -1
    private var profileTreeIds: List<Int> = emptyList()

    fun setProfileTreeContext(treeIds: List<Int>) {
        this.profileTreeIds = treeIds
    }

    fun loadTree(treeId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentTreeId = treeId
            try {
                val entity = treeDao.getTreeById(treeId)
                if (entity == null) {
                    _uiState.value = SpatialUiState(isLoading = false, error = "Arbre introuvable en bdd.")
                    return@launch
                }
                
                // Parse the JSON based on `json_structure.md` contract
                val rawJson = JSONObject(entity.jsonPayload)
                
                // Fallback struct for backward compatibility if roots array exists
                val rootJsonObject = if (rawJson.has("root_node")) {
                    rawJson.getJSONObject("root_node")
                } else if (rawJson.has("roots") && rawJson.getJSONArray("roots").length() > 0) {
                    rawJson.getJSONArray("roots").getJSONObject(0)
                } else {
                    _uiState.value = SpatialUiState(isLoading = false, error = "Format d'arbre invalide (root_node introuvable).")
                    return@launch
                }
                
                rootNode = parseAndSortNode(rootJsonObject, null)
                
                // Init spatial map on root
                rootNode?.let { focusOnNode(it) }

            } catch (e: Exception) {
                _uiState.value = SpatialUiState(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    private fun parseAndSortNode(json: JSONObject, parentRef: TreeNode?): TreeNode {
        val id = json.optString("node_id", json.optString("id", "unsaved"))
        val label = json.optString("label", json.optString("text", json.optString("name", "Sans Titre")))
        var rawUrl = json.optString("image_url", json.optString("image", json.optString("url", "")))

        // Construct standard URL (Legacy Local vs Remote bypass if the JSON lacks http)
        if (rawUrl.isNotEmpty() && !rawUrl.startsWith("http")) {
            val normPath = rawUrl.replace("^/+".toRegex(), "").replace("^(pictograms/|images/)".toRegex(), "")
            if (normPath.startsWith("public/") || normPath.startsWith("$username/")) {
                rawUrl = "$hostUrl/api/v1/mobile/pictograms/$normPath"
            }
        }

        // RÈGLE ARCHITECTURE : Les images doivent TOUJOURS être cherchées en local sur la View 4 (Offline First)
        // Le stockage Android (via ImageSyncEngine) utilise un hachage SHA-256 strict de l'URL distante.
        if (rawUrl.isNotEmpty()) {
            val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(rawUrl)
            val localFile = java.io.File(getApplication<Application>().filesDir, "$username/images/$fileName")
            rawUrl = "file://${localFile.absolutePath}"
        }

        val childrenList = mutableListOf<TreeNode>()
        val childrenArray = json.optJSONArray("children")
        if (childrenArray != null) {
            for (i in 0 until childrenArray.length()) {
                val childJson = childrenArray.optJSONObject(i)
                if (childJson != null) {
                    // Temporarily add with null parent to avoid chicken/egg infinite loop mapping
                    childrenList.add(parseAndSortNode(childJson, null))
                }
            }
        }

        // L'utilisateur gère l'ordre des fratries directement via le Builder Web, le tri manuel est supprimé.

        val node = TreeNode(
            id = id,
            label = label,
            imageUrl = rawUrl,
            children = childrenList,
            parent = parentRef
        )

        // Assign backwards references after creation
        childrenList.forEach { it.parent = node }

        return node
    }

    /**
     * Moteur Physique Spatial
     * Recalcule instantanément la géométrie de la Croix relative au noeud focus
     */
    fun focusOnNode(node: TreeNode) {
        val navState = TreeNavigator.computeSpatialState(node, profileTreeIds, currentTreeId)

        // Si on a des arbres voisins, on charge les racines en asynchrone pour l'affichage (Pre-fetch images)
        viewModelScope.launch {
            val finalLeft = navState.left ?: navState.prevTreeId?.let { id ->
                fetchRootNodePreview(id)
            }
            
            val finalRight = navState.right ?: navState.nextTreeId?.let { id ->
                fetchRootNodePreview(id)
            }

            _uiState.value = SpatialUiState(
                isLoading = false,
                center = navState.center,
                top = navState.top,
                bottom = navState.bottom,
                left = finalLeft,
                right = finalRight,
                microTop = navState.microTop,
                microTopCount = navState.microTopCount,
                microLeft = navState.microLeft,
                microLeftCount = navState.microLeftCount,
                microRight = navState.microRight,
                microRightCount = navState.microRightCount,
                prevTreeId = navState.prevTreeId,
                nextTreeId = navState.nextTreeId
            )
        }
    }

    private suspend fun fetchRootNodePreview(treeId: Int): TreeNode? {
        return treeDao.getTreeById(treeId)?.let { entity ->
            val json = JSONObject(entity.jsonPayload)
            val rootObj = if (json.has("root_node")) json.getJSONObject("root_node") 
                          else if (json.has("roots") && json.getJSONArray("roots").length() > 0) json.getJSONArray("roots").getJSONObject(0)
                          else null
            rootObj?.let { parseAndSortNode(it, null) }
        }
    }

    /**
     * Action de Panier : Ajoute un picto au panier de phrase.
     */
    fun addToPhrase(externalNode: TreeNode? = null) {
        val nodeToAdd = externalNode ?: _uiState.value.center ?: return
        val currentList = _phraseList.value.toMutableList()
        currentList.add(nodeToAdd)
        _phraseList.value = currentList
    }

    fun jumpToNodeId(searchId: String): Boolean {
        val target = findNodeRecursively(rootNode, searchId)
        if (target != null) {
            focusOnNode(target)
            return true
        }
        return false
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
        _phraseList.value = emptyList()
    }
}
