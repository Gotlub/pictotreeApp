package org.libera.pictotree.ui.explorer

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
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
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.utils.WebViewImageInterceptor
import org.libera.pictotree.utils.TTSManager
import org.json.JSONObject

class TreeGlobalMapDialog : DialogFragment() {

    companion object {
        private const val TAG = "TreeGlobalMapDialog"

        fun newInstance(treeIds: IntArray, currentTreeId: Int, username: String, selectedNodeId: String = ""): TreeGlobalMapDialog {
            val dialog = TreeGlobalMapDialog()
            val args = Bundle().apply {
                putIntArray("treeIds", treeIds)
                putInt("currentTreeId", currentTreeId)
                putString("username", username)
                putString("globalSelectedNodeId", selectedNodeId)
            }
            dialog.arguments = args
            return dialog
        }
    }

    private lateinit var webView: WebView
    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager
    
    private var treeIds: IntArray = intArrayOf()
    private var currentIndex: Int = -1
    private var username: String = ""
    private var globalSelectedNodeId: String = ""
    private var globalSelectedTreeId: Int = -1

    // Map pour garder la trace des sélections par arbre (mémoire temporaire de la session Treant)
    private val selectedNodesPerTree = mutableMapOf<Int, String>()
    
    private var isDraggingPhrase = false

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

        treeIds = arguments?.getIntArray("treeIds") ?: intArrayOf()
        val startTreeId = arguments?.getInt("currentTreeId", -1) ?: -1
        username = arguments?.getString("username") ?: ""
        globalSelectedNodeId = arguments?.getString("globalSelectedNodeId") ?: ""
        globalSelectedTreeId = startTreeId
        
        currentIndex = treeIds.indexOf(startTreeId)

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

        // Drag & Drop / Swipe to Delete pour la phrase (Synchronisé avec les fragments)
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT,
            androidx.recyclerview.widget.ItemTouchHelper.UP
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                phraseAdapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == androidx.recyclerview.widget.ItemTouchHelper.UP) {
                    viewModel.removeItemFromPhrase(viewHolder.bindingAdapterPosition)
                }
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
                    isDraggingPhrase = true
                    viewHolder?.itemView?.apply {
                        alpha = 0.8f
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                isDraggingPhrase = false
                viewHolder.itemView.apply {
                    alpha = 1.0f
                    scaleX = 1.0f
                    scaleY = 1.0f
                }
                val finalList = phraseAdapter.getCurrentList().toList()
                viewModel.updatePhraseListSilently(finalList)
            }
        })
        itemTouchHelper.attachToRecyclerView(rvPhrase)

        // Observe Phrase List (Synchronisé avec les fragments)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    var lastPhraseSize = 0
                    viewModel.phraseList.collect { phrase ->
                        if (!isDraggingPhrase) {
                            phraseAdapter.submitList(phrase)
                            if (phrase.size > lastPhraseSize) {
                                rvPhrase.smoothScrollToPosition(phrase.size - 1)
                            }
                        }
                        lastPhraseSize = phrase.size
                    }
                }

                // Etape 3 : Injection CSS pour la couleur CAA dans Treant.js (Observer réactif)
                launch {
                    viewModel.uiState.collect { state ->
                        injectCaaStyle(state.colorCode)
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
                applyCaaColorToPreview(viewModel.uiState.value.colorCode, ivPreview)
            }
        }

        val bridge = object {
            @JavascriptInterface
            fun onNodeSelected(prefixedNodeId: String, imageUrl: String?) {
                val treeId = prefixedNodeId.split("_").firstOrNull()?.toIntOrNull() ?: return
                
                // Mettre à jour le contexte couleur CAA dans le ViewModel partagé
                viewModel.updateCurrentTreeContext(treeId)

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
                        applyCaaColorToPreview(viewModel.uiState.value.colorCode, ivPreview)
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
            
            // Mettre à jour la couleur CAA et la cible globale en temps réel
            viewModel.updateCurrentTreeContext(treeId)
            globalSelectedTreeId = treeId
            globalSelectedNodeId = selectedNodesPerTree[treeId] ?: ""

            // Cacher la miniature si on n'a pas encore de sélection dans cet arbre
            if (globalSelectedNodeId.isEmpty()) ivPreview.visibility = View.GONE

            lifecycleScope.launch(Dispatchers.IO) {
                treeDao.getTreeById(treeId)?.let { entity ->
                    withContext(Dispatchers.Main) {
                        val selectedNodeId = selectedNodesPerTree[treeId] ?: ""
                        val safeJson = android.util.Base64.encodeToString(entity.jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                        webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', '$selectedNodeId', false, $treeId);", null)
                        // Forcer l'injection du style immédiatement après le rendu
                        injectCaaStyle(viewModel.uiState.value.colorCode)
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
            if (currentIndex > 0) { 
                currentIndex--
                loadTree(currentIndex) 
            }
        }
        root.findViewById<ImageButton>(R.id.btn_next_tree).setOnClickListener {
            if (currentIndex < treeIds.size - 1) { 
                currentIndex++
                loadTree(currentIndex) 
            }
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
            if (globalSelectedTreeId != -1) {
                // On déclenche le retour même si nodeId est vide (le ViewModel se rabattra sur la racine)
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
            dismiss()
            (parentFragment as? Fragment)?.findNavController()?.popBackStack()
        }

        val fabSpeak = root.findViewById<View>(R.id.fab_speak_dialog)
        fabSpeak.setOnClickListener {
            val phrase = viewModel.phraseList.value
            if (phrase.isEmpty()) return@setOnClickListener
            ttsManager.stop()
            phrase.forEachIndexed { index, node ->
                ttsManager.speak(node.label, index.toString())
            }
        }

        return root
    }

    private fun injectCaaStyle(color: String) {
        val css = ".node { border: 3px solid $color !important; } " +
                 ".node.selected { box-shadow: 0 0 10px $color !important; }"
        webView.evaluateJavascript("""
            (function() {
                var style = document.getElementById('caa-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'caa-style';
                    document.head.appendChild(style);
                }
                style.innerHTML = '$css';
            })();
        """.trimIndent(), null)
    }

    private fun applyCaaColorToPreview(colorCode: String, imageView: ImageView) {
        try {
            imageView.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = (12 * resources.displayMetrics.density)
                setColor(android.graphics.Color.parseColor("#F0F0F0"))
                setStroke((3 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor(colorCode))
            }
        } catch (e: Exception) {}
    }

    private fun loadPreviewImage(url: String, imageView: android.widget.ImageView) {
        val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(url)
        val localFile = java.io.File(requireContext().filesDir, "$username/images/$fileName")
        val finalSource = if (localFile.exists()) localFile else url
        
        imageView.load(finalSource) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
        }
    }

    private fun extractRootIdFromJson(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val root = if (json.has("root_node")) json.getJSONObject("root_node")
            else if (json.has("roots") && json.getJSONArray("roots").length() > 0) json.getJSONArray("roots").getJSONObject(0)
            else null
            root?.optString("node_id", root.optString("id")) ?: ""
        } catch (e: Exception) { "" }
    }
}
