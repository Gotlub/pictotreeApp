package org.libera.pictotree.ui.explorer

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.model.CardTimeConfig
import org.libera.pictotree.data.model.ProfileSettings
import org.libera.pictotree.data.model.TimeMode
import java.io.File

/**
 * Représente un noeud de l'arbre avec un ID unique au monde (treeId_nodeId_path).
 */
class TreeNode(
    val id: String,
    val label: String,
    val imageUrl: String,
    val children: List<TreeNode>,
    val description: String? = null,
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
        children: List<TreeNode> = this.children, description: String? = this.description, parent: TreeNode? = this.parent
    ): TreeNode = TreeNode(id, label, imageUrl, children, description, parent)

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
        private const val TAG = "TreeExplorerViewModel"
    }

    private val _uiState = MutableStateFlow(HierarchicalUiState())
    val uiState: StateFlow<HierarchicalUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(ProfileSettings())
    val settings: StateFlow<ProfileSettings> = _settings.asStateFlow()

    // BANDEAU DE PHRASE ENRICHI
    private val _phraseList = MutableStateFlow<List<PhraseCard>>(emptyList())
    val phraseList: StateFlow<List<PhraseCard>> = _phraseList.asStateFlow()

    // ÉTATS DE CONFIGURATION (Master/Detail)
    val isClockModeActive = MutableStateFlow(false)
    val isTimerActivated = MutableStateFlow(false)
    val selectedIndexForConfig = MutableStateFlow<Int?>(null)

    // BATTEMENT DE CŒUR (Pulse)
    val currentTimeFlow = flow {
        while (true) {
            emit(SystemClock.elapsedRealtime())
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SystemClock.elapsedRealtime())

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
    fun getProfileId(): Int = profileId
    
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

    fun loadTree(treeId: Int) {
        if (currentTreeId == treeId && rootNode != null) return 

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentTreeId = treeId

            try {
                if (profileId != -1) {
                    profileDao.getProfileById(profileId)?.let { profile ->
                        profile.settingsJson?.let {
                            try {
                                val savedSettings = Gson().fromJson(it, ProfileSettings::class.java)
                                _settings.value = savedSettings
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }

                val color = profileTreeColorsCache[treeId] ?: "#000000"
                _uiState.value = _uiState.value.copy(colorCode = color)

                treeDao.getTreeById(treeId)?.let { entity ->
                    val rawJson = JSONObject(entity.jsonPayload)
                    getRootObject(rawJson)?.let { rootObj ->
                        val parsedRoot = parseAndSortNode(rootObj, null, treeId, "r")
                        rootNode = parsedRoot
                        profileTreeRootsCache[treeId] = parsedRoot
                        
                        val firstChild = parsedRoot.children.firstOrNull()
                        val startNode = firstChild ?: parsedRoot
                        
                        focusOnNode(startNode, updatePreview = _uiState.value.previewNode == null)
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
        val description = json.optString("description", null)
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

        val node = TreeNode(id, label, rawUrl, childrenList, description, parentRef)
        childrenList.forEach { it.parent = node }
        return node
    }

    fun focusOnNode(node: TreeNode, updatePreview: Boolean = true) {
        val roots = profileTreeIds.mapNotNull { profileTreeRootsCache[it] }
        TreeNode.parseTreeId(node.id)?.let { updateCurrentTreeContext(it) }
        
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
        if (_uiState.value.navigationNode?.id == node.id) {
            if (_uiState.value.previewNode?.id != node.id) _uiState.value = _uiState.value.copy(previewNode = node)
            return
        }
        TreeNode.parseTreeId(node.id)?.let { updateCurrentTreeContext(it) }
        _uiState.value = _uiState.value.copy(navigationNode = node, previewNode = node, children = node.children)
    }

    fun selectNodeWithoutNavigating(node: TreeNode) {
        _uiState.value = _uiState.value.copy(previewNode = node)
    }

    fun selectNodeWithoutNavigatingById(uniqueId: String) {
        var target = findNodeRecursively(rootNode, uniqueId)
        if (target == null) {
            val treeId = TreeNode.parseTreeId(uniqueId) ?: -1
            target = findNodeRecursively(profileTreeRootsCache[treeId], uniqueId)
        }
        target?.let { selectNodeWithoutNavigating(it) }
    }

    fun resetSelection() {
        _uiState.value = _uiState.value.copy(previewNode = null)
    }

    fun updatePhraseSize(size: Int) {
        if (size in 0..2) _uiState.value = _uiState.value.copy(phraseSize = size)
    }

    private suspend fun fetchRootNodePreview(treeId: Int): TreeNode? {
        return treeDao.getTreeById(treeId)?.let { entity ->
            getRootObject(JSONObject(entity.jsonPayload))?.let { parseAndSortNode(it, null, treeId, "r") }
        }
    }

    fun addToPhrase(externalNode: TreeNode? = null) {
        val nodeToAdd = externalNode ?: _uiState.value.previewNode ?: return
        val uniqueInstanceNode = nodeToAdd.copy(id = "${nodeToAdd.id}_${System.currentTimeMillis()}_${(0..999).random()}")
        
        if (nodeToAdd.imageUrl.startsWith("http") || nodeToAdd.imageUrl.contains("/api/v1/mobile/")) {
            viewModelScope.launch {
                val token = org.libera.pictotree.data.SessionManager(getApplication()).getToken()
                org.libera.pictotree.data.repository.ImageSyncEngine(getApplication(), imageDao, username, hostUrl, token)
                    .downloadSingleImage(nodeToAdd.imageUrl, nodeToAdd.label)
            }
        }
        _phraseList.value = _phraseList.value + PhraseCard(uniqueInstanceNode)
    }

    fun addToPhraseById(uniqueId: String): Boolean {
        return findNodeRecursively(rootNode, uniqueId)?.let { addToPhrase(it); true } ?: false
    }

    // LOGIQUE SÉQUENTIELLE DU TIMER
    fun startTimerForFirstCard() {
        if (!isTimerActivated.value) return
        val list = _phraseList.value.toMutableList()
        if (list.isEmpty()) return

        val firstCard = list[0]
        if (firstCard.timeConfig.mode == TimeMode.TIMER && firstCard.timeConfig.endTimeMillis == 0L) {
            val durationMs = firstCard.timeConfig.durationMinutes * 60 * 1000L
            val endTime = SystemClock.elapsedRealtime() + durationMs
            
            list[0] = firstCard.copy(timeConfig = firstCard.timeConfig.copy(
                startTimeMillis = SystemClock.elapsedRealtime(),
                endTimeMillis = endTime
            ))
            _phraseList.value = list
            scheduleSystemAlarm(endTime, firstCard.node.label)
        }
    }

    fun stopAllTimers() {
        isTimerActivated.value = false
        val list = _phraseList.value.toMutableList()
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        for (i in list.indices) {
            val card = list[i]
            if (card.timeConfig.endTimeMillis > 0) {
                val intent = Intent(getApplication(), org.libera.pictotree.utils.TimerReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    getApplication(), card.node.label.hashCode(), intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
            list[i] = card.copy(timeConfig = card.timeConfig.copy(
                startTimeMillis = 0L,
                endTimeMillis = 0L
            ))
        }
        _phraseList.value = list
        
        // Stop ringing receiver if any
        val stopIntent = Intent(getApplication(), org.libera.pictotree.utils.TimerReceiver::class.java).apply {
            action = "ACTION_STOP_ALARM"
        }
        getApplication<Application>().sendBroadcast(stopIntent)
    }

    private fun scheduleSystemAlarm(triggerAtMillis: Long, label: String) {
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // SÉCURITÉ ANDROID 12+ : Vérifier si on a le droit de programmer une alarme exacte
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Si pas de permission, on utilise une alarme inexacte (ou on pourrait ouvrir les réglages)
                Log.e(TAG, "Missing SCHEDULE_EXACT_ALARM permission, falling back to inexact alarm")
                val intent = Intent(getApplication(), org.libera.pictotree.utils.TimerReceiver::class.java).apply {
                    putExtra("EXTRA_LABEL", label)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    getApplication(), label.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                return
            }
        }

        val intent = Intent(getApplication(), org.libera.pictotree.utils.TimerReceiver::class.java).apply {
            putExtra("EXTRA_LABEL", label)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(), label.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
    }

    fun updateCardTimeConfig(index: Int, config: CardTimeConfig) {
        val list = _phraseList.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(timeConfig = config)
            _phraseList.value = list
            // Si on modifie la carte 0, on redémarre peut-être le timer
            if (index == 0) startTimerForFirstCard()
        }
    }

    fun removeItemFromPhrase(position: Int) {
        val list = _phraseList.value.toMutableList()
        if (position in list.indices) {
            list.removeAt(position)
            _phraseList.value = list
            // Passage automatique à la carte suivante
            if (position == 0) startTimerForFirstCard()
        }
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
        for (child in current.children) findNodeRecursively(child, targetId)?.let { return it }
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

    fun updatePhraseListSilently(newList: List<PhraseCard>) {
        _phraseList.value = newList
    }
}
