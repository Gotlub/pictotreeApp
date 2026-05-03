package org.libera.pictotree.ui.explorer

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.utils.WebViewImageInterceptor
import org.libera.pictotree.utils.TTSManager
import org.json.JSONObject
import coil.load

class TreeGlobalMapDialog : DialogFragment() {

    private var treeIds: IntArray 
        get() = arguments?.getIntArray("treeIds") ?: intArrayOf()
        set(value) { arguments?.putIntArray("treeIds", value) }

    private var currentIndex: Int
        get() = arguments?.getInt("currentIndex", -1) ?: -1
        set(value) { arguments?.putInt("currentIndex", value) }

    private var username: String
        get() = arguments?.getString("username") ?: ""
        set(value) { arguments?.putString("username", value) }

    private var initialSelectedNodeId: String
        get() = arguments?.getString("initialSelectedNodeId") ?: ""
        set(value) { arguments?.putString("initialSelectedNodeId", value) }

    private var globalSelectedTreeId: Int
        get() = arguments?.getInt("globalSelectedTreeId", -1) ?: -1
        set(value) { arguments?.putInt("globalSelectedTreeId", value) }

    private var globalSelectedNodeId: String
        get() = arguments?.getString("globalSelectedNodeId") ?: ""
        set(value) { arguments?.putString("globalSelectedNodeId", value) }

    private val selectedNodesPerTree = mutableMapOf<Int, String>()

    private lateinit var webView: WebView
    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager

    companion object {
        private const val TAG = "PictoTreeNav"

        fun newInstance(treeIds: IntArray, currentTreeId: Int, username: String, selectedNodeId: String): TreeGlobalMapDialog {
            val dialog = TreeGlobalMapDialog()
            val args = Bundle().apply {
                putIntArray("treeIds", treeIds)
                val idx = treeIds.indexOf(currentTreeId)
                putInt("currentIndex", if (idx == -1 && treeIds.isNotEmpty()) 0 else idx)
                putString("username", username)
                putString("initialSelectedNodeId", selectedNodeId)
                putInt("globalSelectedTreeId", currentTreeId)
                putString("globalSelectedNodeId", selectedNodeId)
            }
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Log.d(TAG, "VIEW_CHANGE: Treant Dialog Dismissed")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_tree_global_map, container, false)
        webView = root.findViewById(R.id.web_view_map)
        
        // Shared ViewModel at Activity level
        viewModel = ViewModelProvider(requireActivity())[TreeExplorerViewModel::class.java]
        ttsManager = TTSManager(requireContext())

        root.findViewById<View>(R.id.btn_fullscreen_phrase).setOnClickListener {
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigate(R.id.action_treeExplorerFragment_to_phraseFullscreenFragment)
        }

        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val imageDao = database.imageDao()

        // Setup Phrase Bar (RecyclerView)
        val rvPhrase = root.findViewById<RecyclerView>(R.id.rv_phrase)
        val phraseAdapter = PhraseAdapter()
        rvPhrase.adapter = phraseAdapter
        rvPhrase.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // Drag & Drop / Swipe to Delete
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT,
            androidx.recyclerview.widget.ItemTouchHelper.UP
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                viewModel.moveItemInPhrase(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == androidx.recyclerview.widget.ItemTouchHelper.UP) {
                    viewModel.removeItemFromPhrase(viewHolder.bindingAdapterPosition)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(rvPhrase)

        // Observe Phrase List
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.phraseList.collect { phrase ->
                    phraseAdapter.submitList(phrase)
                    if (phrase.isNotEmpty()) {
                        rvPhrase.smoothScrollToPosition(phrase.size - 1)
                    }
                }
            }
        }

        // Restaurer la sélection dans la map temporaire au démarrage
        if (globalSelectedTreeId != -1 && globalSelectedNodeId.isNotEmpty()) {
            selectedNodesPerTree[globalSelectedTreeId] = globalSelectedNodeId
        }

        val ivPreview = root.findViewById<android.widget.ImageView>(R.id.iv_selection_preview)

        // Initialiser la miniature si une sélection existe déjà
        viewModel.uiState.value.focusedNode?.let { centerNode ->
            if (centerNode.id == globalSelectedNodeId) {
                ivPreview.visibility = View.VISIBLE
                loadPreviewImage(centerNode.imageUrl, ivPreview)
            }
        }

        val bridge = object {
            @JavascriptInterface
            fun onNodeSelected(prefixedNodeId: String, imageUrl: String?) {
                val treeId = prefixedNodeId.split("_").firstOrNull()?.toIntOrNull() ?: return
                
                // UNICITÉ GLOBALE : On vide les sélections des autres arbres
                selectedNodesPerTree.clear()
                
                selectedNodesPerTree[treeId] = prefixedNodeId
                globalSelectedTreeId = treeId
                globalSelectedNodeId = prefixedNodeId
                Log.d(TAG, "TREANT_SELECT: Global target updated to Tree $treeId, Node $prefixedNodeId")

                // Mettre à jour la miniature
                requireActivity().runOnUiThread {
                    if (!imageUrl.isNullOrEmpty()) {
                        ivPreview.visibility = View.VISIBLE
                        loadPreviewImage(imageUrl, ivPreview)
                    } else {
                        ivPreview.visibility = View.GONE
                    }
                }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.addJavascriptInterface(bridge, "AndroidBridge")

        fun loadTree(index: Int) {
            if (treeIds.isEmpty() || index !in treeIds.indices) return
            val treeId = treeIds[index]
            lifecycleScope.launch(Dispatchers.IO) {
                treeDao.getTreeById(treeId)?.let { entity ->
                    withContext(Dispatchers.Main) {
                        val selectedNodeId = selectedNodesPerTree[treeId] ?: ""
                        val safeJson = android.util.Base64.encodeToString(entity.jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                        webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', '$selectedNodeId', false, $treeId);", null)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (currentIndex != -1) loadTree(currentIndex)
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return WebViewImageInterceptor.intercept(requireContext(), username, imageDao, request?.url)
            }
        }

        webView.loadUrl("file:///android_asset/tree_viewer.html")

        root.findViewById<ImageButton>(R.id.btn_prev_tree).setOnClickListener {
            if (currentIndex > 0) { currentIndex--; loadTree(currentIndex) }
        }
        root.findViewById<ImageButton>(R.id.btn_next_tree).setOnClickListener {
            if (currentIndex < treeIds.size - 1) { currentIndex++; loadTree(currentIndex) }
        }

        root.findViewById<View>(R.id.btn_add_to_basket).setOnClickListener {
            val currentTreeId = if (currentIndex in treeIds.indices) treeIds[currentIndex] else -1
            val visibleSelection = selectedNodesPerTree[currentTreeId]
            if (currentTreeId != -1) {
                if (!visibleSelection.isNullOrEmpty()) {
                    viewModel.jumpToTreeAndNode(currentTreeId, visibleSelection, addToBasket = true)
                } else {
                    // Fallback: add root of the current tree
                    lifecycleScope.launch(Dispatchers.IO) {
                        val treeEntity = treeDao.getTreeById(currentTreeId)
                        val rawId = treeEntity?.let { extractRootIdFromJson(it.jsonPayload) } ?: ""
                        val fallbackId = "${currentTreeId}_${rawId}_r"
                        withContext(Dispatchers.Main) { 
                            viewModel.jumpToTreeAndNode(currentTreeId, fallbackId, addToBasket = true) 
                        }
                    }
                }
            }
        }

        root.findViewById<View>(R.id.btn_back_to_nav).setOnClickListener {
            Log.d(TAG, "VIEW_CHANGE: Returning to Nav. Tree $globalSelectedTreeId, Node $globalSelectedNodeId")
            if (globalSelectedTreeId != -1 && globalSelectedNodeId.isNotEmpty()) {
                viewModel.jumpToTreeAndNode(globalSelectedTreeId, globalSelectedNodeId)
            }
            dismiss()
        }

        root.findViewById<View>(R.id.card_search).setOnClickListener {
            val searchDialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            searchDialog.onPictoSelected = { result ->
                val searchNode = TreeNode(
                    id = "search_${result.id}_recherche",
                    label = result.name,
                    imageUrl = result.imageUrl,
                    children = emptyList()
                )
                viewModel.addToPhrase(searchNode)
            }
            searchDialog.show(childFragmentManager, "PictoSearch")
        }

        root.findViewById<View>(R.id.card_back_to_trees).setOnClickListener {
            // Fermer le dialogue et demander à l'explorer de revenir en arrière
            dismiss()
            // On peut envoyer un signal via le ViewModel ou simplement laisser l'utilisateur 
            // cliquer sur le bouton physique retour, mais ici on veut simuler le bouton grille.
            // Le plus simple est de déclencher une navigation arrière sur le parent.
            (parentFragment as? Fragment)?.findNavController()?.popBackStack()
        }

        // TTS Support
        val fabSpeak = root.findViewById<View>(R.id.fab_speak_dialog)
        fabSpeak.setOnClickListener {
            val phrase = viewModel.phraseList.value
            if (phrase.isEmpty()) return@setOnClickListener
            ttsManager.stop()
            phrase.forEachIndexed { index, node ->
                ttsManager.speak(node.label, index.toString())
            }
        }
        fabSpeak.setOnLongClickListener {
            viewModel.clearPhrase()
            ttsManager.stop()
            true
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::ttsManager.isInitialized) {
            ttsManager.shutdown()
        }
    }

    private fun loadPreviewImage(url: String, imageView: android.widget.ImageView) {
        if (url.isEmpty()) {
            imageView.visibility = View.GONE
            return
        }

        val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
        // On nettoie l'URL pour Coil et pour le check local
        val cleanUrl = url.substringBefore('?')
        
        var finalSource: Any = cleanUrl

        // 1. Chercher d'abord en local via le hash de l'URL propre
        val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(cleanUrl)
        val localFile = java.io.File(requireContext().filesDir, "$username/images/$fileName")

        if (localFile.exists()) {
            finalSource = localFile
        } else if (!cleanUrl.startsWith("http") && !cleanUrl.startsWith("file")) {
            // 2. Fallback normalisé si chemin relatif (cas rare ici mais pour sécurité)
            val normPath = cleanUrl.replace("^/+".toRegex(), "").replace("^(pictograms/|images/)".toRegex(), "")
            finalSource = "$hostUrl/api/v1/mobile/pictograms/$normPath"
        }

        imageView.visibility = View.VISIBLE
        imageView.load(finalSource) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        }
    }

    private fun extractRootIdFromJson(jsonPayload: String): String {
        return try {
            val json = JSONObject(jsonPayload)
            val root = if (json.has("root_node")) json.getJSONObject("root_node")
            else if (json.has("roots") && json.getJSONArray("roots").length() > 0) json.getJSONArray("roots").getJSONObject(0)
            else null
            root?.optString("node_id", root.optString("id")) ?: ""
        } catch (e: Exception) { "" }
    }
}

