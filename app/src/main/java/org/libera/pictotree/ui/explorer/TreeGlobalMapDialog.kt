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
import org.libera.pictotree.utils.FileUtils
import org.json.JSONObject

class TreeGlobalMapDialog : DialogFragment() {

    companion object {
        private const val TAG = "TreeGlobalMapDialog"

        fun newInstance(
            treeIds: IntArray, 
            currentTreeId: Int, 
            username: String, 
            selectedNodeId: String = "",
            isSimplePreview: Boolean = false
        ): TreeGlobalMapDialog {
            val dialog = TreeGlobalMapDialog()
            val args = Bundle().apply {
                putIntArray("treeIds", treeIds)
                putInt("currentTreeId", currentTreeId)
                putString("username", username)
                putBoolean("isSimplePreview", isSimplePreview)
            }
            dialog.arguments = args
            return dialog
        }
    }

    private lateinit var webView: WebView
    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager
    private var phraseAdapter: PhraseAdapter? = null
    private var rvPhrase: RecyclerView? = null
    
    private var appContext: android.content.Context? = null
    
    private var treeIds: IntArray = intArrayOf()
    private var currentIndex: Int = -1
    private var username: String = ""
    private var isSimplePreview: Boolean = false

    private var isDraggingPhrase = false
    private var imageDao: org.libera.pictotree.data.database.dao.ImageDao? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_PictotreeApp_FullscreenDialog)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("saved_currentIndex", currentIndex)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Log.d(TAG, "VIEW_CHANGE: Treant Dialog Dismissed")
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        appContext = requireContext().applicationContext
        (requireActivity() as? org.libera.pictotree.MainActivity)?.applyUserOrientation()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))
            
            decorView.setPadding(0, 0, 0, 0)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
                insetsController?.let { controller ->
                    controller.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    controller.setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    } else 0
                )
            }
            
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            
            val attrs = attributes
            attrs.width = ViewGroup.LayoutParams.MATCH_PARENT
            attrs.height = ViewGroup.LayoutParams.MATCH_PARENT
            attrs.horizontalMargin = 0f
            attrs.verticalMargin = 0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                attrs.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            attributes = attrs
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_tree_global_map, container, false)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            val containerActions = view.findViewById<View>(R.id.container_actions)
            containerActions?.apply {
                val params = layoutParams as? ViewGroup.MarginLayoutParams
                params?.topMargin = systemBars.top + (16 * resources.displayMetrics.density).toInt()
                params?.rightMargin = systemBars.right + (16 * resources.displayMetrics.density).toInt()
                layoutParams = params
            }
            
            val panelPhrase = view.findViewById<View>(R.id.panel_phrase)
            panelPhrase?.setPadding(
                systemBars.left + (16 * resources.displayMetrics.density).toInt(),
                panelPhrase.paddingTop,
                systemBars.right + (16 * resources.displayMetrics.density).toInt(),
                systemBars.bottom
            )

            val btnPrev = view.findViewById<View>(R.id.btn_prev_tree)
            btnPrev?.apply {
                val params = layoutParams as? ViewGroup.MarginLayoutParams
                params?.leftMargin = systemBars.left + (8 * resources.displayMetrics.density).toInt()
                layoutParams = params
            }

            val btnNext = view.findViewById<View>(R.id.btn_next_tree)
            btnNext?.apply {
                val params = layoutParams as? ViewGroup.MarginLayoutParams
                val baseMargin = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 100 else 8
                params?.rightMargin = systemBars.right + (baseMargin * resources.displayMetrics.density).toInt()
                layoutParams = params
            }
            
            insets
        }
        
        webView = root.findViewById(R.id.web_view_map)
        
        username = arguments?.getString("username") ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        imageDao = database.imageDao()
        val userConfigRepository = org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao())
        
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return TreeExplorerViewModel(
                    requireActivity().application,
                    database.treeDao(),
                    database.profileDao(),
                    database.imageDao(),
                    userConfigRepository,
                    org.libera.pictotree.network.RetrofitClient.SERVER_URL,
                    username
                ) as T
            }
        }
        
        viewModel = ViewModelProvider(requireActivity(), factory)[TreeExplorerViewModel::class.java]
        ttsManager = TTSManager(requireContext())

        treeIds = arguments?.getIntArray("treeIds") ?: intArrayOf()
        isSimplePreview = arguments?.getBoolean("isSimplePreview") ?: false

        if (savedInstanceState != null) {
            currentIndex = savedInstanceState.getInt("saved_currentIndex", -1)
        } else {
            val startTreeId = arguments?.getInt("currentTreeId", -1) ?: -1
            currentIndex = treeIds.indexOf(startTreeId)
        }

        if (isSimplePreview) {
            root.findViewById<View>(R.id.panel_phrase).visibility = View.GONE
            root.findViewById<View>(R.id.panel_selection_preview).visibility = View.GONE
            root.findViewById<View>(R.id.btn_prev_tree).visibility = View.GONE
            root.findViewById<View>(R.id.btn_next_tree).visibility = View.GONE
            root.findViewById<View>(R.id.card_search).visibility = View.GONE
            root.findViewById<View>(R.id.card_speak).visibility = View.GONE
            root.findViewById<View>(R.id.card_rotate).visibility = View.GONE
            root.findViewById<View>(R.id.btn_back_to_nav).visibility = View.GONE
            
            val btnClose = root.findViewById<View>(R.id.card_back_to_trees)
            btnClose.visibility = View.VISIBLE
            btnClose.setOnClickListener { dismiss() }
        } else {
            root.findViewById<View>(R.id.btn_fullscreen_phrase).setOnClickListener {
                val navController = findNavController()
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

            rvPhrase = root.findViewById(R.id.rv_phrase)
            phraseAdapter = PhraseAdapter(username = username, onItemClick = { position ->
                val card = phraseAdapter?.getCurrentList()?.getOrNull(position)
                card?.let { ttsManager.speak(it.node.label) }
            })
            rvPhrase?.adapter = phraseAdapter
            (rvPhrase?.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
            rvPhrase?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

            val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT,
                androidx.recyclerview.widget.ItemTouchHelper.UP
            ) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    phraseAdapter?.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
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
                    phraseAdapter?.let { viewModel.updatePhraseListSilently(it.getCurrentList().toList()) }
                }
            })
            rvPhrase?.let { itemTouchHelper.attachToRecyclerView(it) }

            root.findViewById<View>(R.id.btn_add_to_basket).setOnClickListener {
                viewModel.addToPhrase()
            }

            root.findViewById<View>(R.id.btn_back_to_nav).setOnClickListener {
                val previewNode = viewModel.uiState.value.previewNode
                if (previewNode != null) {
                    val treeId = TreeNode.parseTreeId(previewNode.id) ?: -1
                    viewModel.jumpToTreeAndNode(treeId, previewNode.id)
                    val navController = findNavController()
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

            root.findViewById<View>(R.id.card_back_to_trees).setOnClickListener {
                dismiss()
                val navController = findNavController()
                if (navController.currentDestination?.id != R.id.treeSelectionFragment) {
                    navController.popBackStack(R.id.treeSelectionFragment, false)
                }
            }

            root.findViewById<View>(R.id.card_search).setOnClickListener {
                val searchDialog = org.libera.pictotree.ui.common.PictoSearchDialog()
                searchDialog.onPictoSelected = { result ->
                    val searchNode = TreeNode("search_${result.id}_recherche", result.name ?: "", result.imageUrl ?: "", emptyList())
                    viewModel.addToPhrase(searchNode)
                }
                searchDialog.show(childFragmentManager, "PictoSearch")
            }

            root.findViewById<View>(R.id.card_speak).setOnClickListener {
                val phrase = viewModel.phraseList.value
                if (phrase.isNotEmpty()) {
                    ttsManager.stop()
                    phrase.forEachIndexed { index, card -> ttsManager.speak(card.node.label, index.toString()) }
                }
            }

            root.findViewById<View>(R.id.card_rotate).setOnClickListener {
                (requireActivity() as? org.libera.pictotree.MainActivity)?.toggleOrientation()
            }

            root.findViewById<ImageButton>(R.id.btn_prev_tree).setOnClickListener {
                if (currentIndex > 0) { currentIndex--; loadTree(currentIndex) }
            }
            root.findViewById<ImageButton>(R.id.btn_next_tree).setOnClickListener {
                if (currentIndex < treeIds.size - 1) { currentIndex++; loadTree(currentIndex) }
            }
        }

        val ivPreview = root.findViewById<android.widget.ImageView>(R.id.iv_selection_preview)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!isSimplePreview) {
                    launch {
                        var lastPhraseSize = 0
                        viewModel.phraseList.collect { phrase ->
                            if (!isDraggingPhrase) {
                                phraseAdapter?.submitList(phrase)
                                if (phrase.size > lastPhraseSize) rvPhrase?.smoothScrollToPosition(phrase.size - 1)
                            }
                            lastPhraseSize = phrase.size
                        }
                    }
                    
                    // PULSE DU TIMER (Mise à jour visuelle du premier picto si timer actif)
                    launch {
                        viewModel.currentTimeFlow.collect {
                            val firstCard = viewModel.phraseList.value.firstOrNull()
                            if (firstCard?.timeConfig?.mode == org.libera.pictotree.data.model.TimeMode.TIMER && firstCard.timeConfig.endTimeMillis > 0) {
                                phraseAdapter?.notifyItemChanged(0)
                            }
                        }
                    }

                    launch {
                        viewModel.userConfig.collect { config ->
                            if (config != null) {
                                root.findViewById<View>(R.id.card_search)?.visibility = if (config.enableSearch) View.VISIBLE else View.GONE
                                root.findViewById<View>(R.id.card_speak)?.visibility = if (config.enableTTSButton) View.VISIBLE else View.GONE
                                root.findViewById<View>(R.id.card_rotate)?.visibility = if (config.enableRotationButton) View.VISIBLE else View.GONE
                                root.findViewById<View>(R.id.btn_back_to_nav)?.visibility = if (config.enableViewChangeButton) View.VISIBLE else View.GONE
                            }
                        }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        injectCaaStyle(state.colorCode)
                        if (!isSimplePreview) {
                            state.previewNode?.let { node ->
                                ivPreview.visibility = View.VISIBLE
                                loadPreviewImage(node.imageUrl, ivPreview)
                                applyCaaColorToPreview(state.colorCode, ivPreview)
                            } ?: run { ivPreview.visibility = View.GONE }
                        }
                    }
                }
            }
        }

        val bridge = object {
            @JavascriptInterface
            fun onNodeSelected(prefixedNodeId: String, imageUrl: String?) {
                if (!isSimplePreview) {
                    viewModel.selectNodeWithoutNavigatingById(prefixedNodeId)
                }
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (currentIndex != -1) loadTree(currentIndex)
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val ctx = appContext ?: return null
                val dao = imageDao ?: AppDatabase.getDatabase(ctx, username).imageDao().also { imageDao = it }
                return WebViewImageInterceptor.intercept(ctx, username, dao, request?.url, strictOffline = true)
            }
        }

        webView.loadUrl("file:///android_asset/tree_viewer.html")

        return root
    }

    private fun loadTree(index: Int) {
        if (treeIds.isEmpty() || index !in treeIds.indices) return
        val treeId = treeIds[index]
        val orientation = "NORTH"
        
        val ctx = context ?: return
        val database = AppDatabase.getDatabase(ctx, username)
        
        if (!isSimplePreview) {
            viewModel.updateCurrentTreeContext(treeId)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            database.treeDao().getTreeById(treeId)?.let { entity ->
                withContext(Dispatchers.Main) {
                    val previewNode = if (!isSimplePreview) viewModel.uiState.value.previewNode else null
                    val highlightId = if (previewNode != null && TreeNode.parseTreeId(previewNode.id) == treeId) {
                        previewNode.id
                    } else ""
                    
                    val safeJson = android.util.Base64.encodeToString(entity.jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                    webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', '$highlightId', false, $treeId, '$orientation');", null)
                    
                    if (!isSimplePreview) {
                        injectCaaStyle(viewModel.uiState.value.colorCode)
                    }
                }
            }
        }
    }

    private fun showClearPhraseConfirmation() {
        val ctx = context ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Effacer le bandeau ?")
            .setMessage("Voulez-vous vraiment vider toute la phrase ?")
            .setPositiveButton("Oui") { _, _ -> viewModel.clearPhrase() }
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

    private fun loadPreviewImage(url: String?, imageView: android.widget.ImageView) {
        val ctx = appContext ?: return
        if (url.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.ic_launcher_foreground)
            return
        }
        
        val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
        val finalSource = FileUtils.getFinalImageSource(url, ctx, username, hostUrl)
        
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

    override fun onDestroyView() {
        super.onDestroyView()
        if (::ttsManager.isInitialized) {
            ttsManager.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::ttsManager.isInitialized) {
            ttsManager.shutdown()
        }
    }
}
