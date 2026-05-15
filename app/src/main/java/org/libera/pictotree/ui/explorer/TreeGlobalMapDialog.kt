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
            }
            dialog.arguments = args
            return dialog
        }
    }

    private lateinit var webView: WebView
    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager
    
    // CACHE DU CONTEXTE pour éviter IllegalStateException pendant les rotations
    private var appContext: android.content.Context? = null
    
    private var treeIds: IntArray = intArrayOf()
    private var currentIndex: Int = -1
    private var username: String = ""

    private var isDraggingPhrase = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("saved_currentIndex", currentIndex)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Log.d(TAG, "VIEW_CHANGE: Treant Dialog Dismissed")
    }

    override fun onStart() {
        super.onStart()
        // On capture le contexte de l'application dès le départ
        appContext = requireContext().applicationContext
        (requireActivity() as? org.libera.pictotree.MainActivity)?.applyUserOrientation()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_tree_global_map, container, false)
        webView = root.findViewById(R.id.web_view_map)
        
        // Shared ViewModel at Activity level
        viewModel = ViewModelProvider(requireActivity())[TreeExplorerViewModel::class.java]
        ttsManager = TTSManager(requireContext())

        treeIds = arguments?.getIntArray("treeIds") ?: intArrayOf()
        username = arguments?.getString("username") ?: ""

        if (savedInstanceState != null) {
            currentIndex = savedInstanceState.getInt("saved_currentIndex", -1)
        } else {
            val startTreeId = arguments?.getInt("currentTreeId", -1) ?: -1
            currentIndex = treeIds.indexOf(startTreeId)
        }

        root.findViewById<View>(R.id.btn_fullscreen_phrase).setOnClickListener {
            val navController = findNavController()
            // Détection dynamique de l'action selon la vue parente pour éviter le crash
            val actionId = if (navController.currentDestination?.id == R.id.treeSelectionFragment) {
                R.id.action_treeSelectionFragment_to_phraseFullscreenFragment
            } else {
                R.id.action_treeExplorerFragment_to_phraseFullscreenFragment
            }
            navController.navigate(actionId)
        }

        root.findViewById<View>(R.id.btn_clear_phrase)?.setOnClickListener {
            showClearPhraseConfirmation()
        }

        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val imageDao = database.imageDao()

        // Setup Phrase Bar
        val rvPhrase = root.findViewById<RecyclerView>(R.id.rv_phrase)
        val phraseAdapter = PhraseAdapter(username = username)
        rvPhrase.adapter = phraseAdapter
        rvPhrase.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // Drag & Drop / Swipe to Delete
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT,
            androidx.recyclerview.widget.ItemTouchHelper.UP
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                phraseAdapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
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
                    viewHolder?.itemView?.apply { alpha = 0.8f; scaleX = 1.05f; scaleY = 1.05f }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                isDraggingPhrase = false
                viewHolder.itemView.apply { alpha = 1.0f; scaleX = 1.0f; scaleY = 1.0f }
                viewModel.updatePhraseListSilently(phraseAdapter.getCurrentList().toList())
            }
        })
        itemTouchHelper.attachToRecyclerView(rvPhrase)

        val ivPreview = root.findViewById<android.widget.ImageView>(R.id.iv_selection_preview)

        // Observe State (Synchronisé avec les fragments)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Phrase observation
                launch {
                    var lastPhraseSize = 0
                    viewModel.phraseList.collect { phrase ->
                        if (!isDraggingPhrase) {
                            phraseAdapter.submitList(phrase)
                            if (phrase.size > lastPhraseSize) rvPhrase.smoothScrollToPosition(phrase.size - 1)
                        }
                        lastPhraseSize = phrase.size
                    }
                }

                // Global State observation (Colors & Preview Node)
                launch {
                    viewModel.uiState.collect { state ->
                        injectCaaStyle(state.colorCode)
                        
                        // MISE À JOUR RÉACTIVE DE LA PREVIEW
                        state.previewNode?.let { node ->
                            ivPreview.visibility = View.VISIBLE
                            loadPreviewImage(node.imageUrl, ivPreview)
                            applyCaaColorToPreview(state.colorCode, ivPreview)
                        } ?: run {
                            ivPreview.visibility = View.GONE
                        }
                    }
                }

                // Observation des réglages de recherche
                launch {
                    viewModel.settings.collect { settings ->
                        root.findViewById<View>(R.id.card_search)?.visibility = 
                            if (settings.enableSearch) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        val bridge = object {
            @JavascriptInterface
            fun onNodeSelected(prefixedNodeId: String, imageUrl: String?) {
                // On met à jour DIRECTEMENT le ViewModel, qui est notre seule source de vérité
                viewModel.selectNodeWithoutNavigatingById(prefixedNodeId)
                Log.d(TAG, "TREANT_SELECT: Node $prefixedNodeId selected")
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
            val orientation = "NORTH"
            
            // On informe le ViewModel qu'on change d'arbre (pour la couleur CAA)
            viewModel.updateCurrentTreeContext(treeId)

            lifecycleScope.launch(Dispatchers.IO) {
                treeDao.getTreeById(treeId)?.let { entity ->
                    withContext(Dispatchers.Main) {
                        // On récupère l'ID du picto sélectionné depuis le ViewModel
                        // S'il appartient à cet arbre, on le passe au moteur de rendu pour le highlight
                        val previewNode = viewModel.uiState.value.previewNode
                        val highlightId = if (previewNode != null && TreeNode.parseTreeId(previewNode.id) == treeId) {
                            previewNode.id
                        } else ""
                        
                        val safeJson = android.util.Base64.encodeToString(entity.jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                        webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', '$highlightId', false, $treeId, '$orientation');", null)
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
                // UTILISATION DU CONTEXTE CACHÉ pour éviter le crash en cas de fragment détaché
                val ctx = appContext ?: return null
                return WebViewImageInterceptor.intercept(ctx, username, imageDao, request?.url, strictOffline = true)
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
            viewModel.addToPhrase() // Utilise directement previewNode du ViewModel
        }

        root.findViewById<View>(R.id.btn_back_to_nav).setOnClickListener {
            // RETOUR À LA NAVIGATION NATIVE
            val previewNode = viewModel.uiState.value.previewNode
            if (previewNode != null) {
                val treeId = TreeNode.parseTreeId(previewNode.id) ?: -1
                viewModel.jumpToTreeAndNode(treeId, previewNode.id)
                
                val navController = findNavController()
                // FIX STARTUP MAP : Si on vient de la sélection, on saute vers l'explorateur
                if (navController.currentDestination?.id == R.id.treeSelectionFragment) {
                    val bundle = Bundle().apply {
                        putInt("treeId", treeId)
                        putInt("profileId", viewModel.getProfileId())
                        putString("username", username)
                    }
                    navController.navigate(R.id.action_treeSelectionFragment_to_treeExplorerFragment, bundle)
                }
            }
            dismiss()
        }

        root.findViewById<View>(R.id.card_search).setOnClickListener {
            val searchDialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            searchDialog.onPictoSelected = { result ->
                val searchNode = TreeNode("search_${result.id}_recherche", result.name, result.imageUrl, emptyList())
                viewModel.addToPhrase(searchNode)
            }
            searchDialog.show(childFragmentManager, "PictoSearch")
        }

        root.findViewById<View>(R.id.card_speak).setOnClickListener {
            val phrase = viewModel.phraseList.value
            if (phrase.isNotEmpty()) {
                ttsManager.stop()
                phrase.forEachIndexed { index, node -> ttsManager.speak(node.label, index.toString()) }
            }
        }

        root.findViewById<View>(R.id.card_rotate).setOnClickListener {
            (requireActivity() as? org.libera.pictotree.MainActivity)?.toggleOrientation()
        }

        root.findViewById<View>(R.id.card_back_to_trees).setOnClickListener {
            dismiss()
            val navController = findNavController()
            // On s'assure de revenir à la sélection d'arbres si on n'y est pas déjà
            if (navController.currentDestination?.id != R.id.treeSelectionFragment) {
                navController.popBackStack(R.id.treeSelectionFragment, false)
            }
        }

        return root
    }

    private fun showClearPhraseConfirmation() {
        val ctx = context ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Effacer le bandeau ?")
            .setMessage("Voulez-vous vraiment vider toute la phrase ?")
            .setPositiveButton("Oui") { _, _ ->
                viewModel.clearPhrase()
            }
            .setNegativeButton("Non", null)
            .setIcon(android.R.drawable.ic_menu_delete)
            .show()
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
        val ctx = appContext ?: return
        val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(url)
        val localFile = java.io.File(ctx.filesDir, "$username/images/$fileName")
        val finalSource: Any = if (localFile.exists()) localFile else url
        
        val imageLoader = org.libera.pictotree.network.RetrofitClient.getImageLoader(ctx)
        imageView.load(finalSource, imageLoader) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            if (finalSource is String && !finalSource.startsWith("file")) {
                networkCachePolicy(coil.request.CachePolicy.DISABLED)
            }
        }
    }
}
