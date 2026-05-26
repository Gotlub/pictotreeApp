package org.libera.pictotree.ui.explorer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.utils.TTSManager
import org.libera.pictotree.utils.FileUtils
import java.io.File

class TreeExplorerFragment : Fragment() {

    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager

    private lateinit var breadcrumbAdapter: NodeAdapter
    private lateinit var siblingAdapter: NodeAdapter
    private lateinit var childrenAdapter: NodeAdapter
    private lateinit var phraseAdapter: PhraseAdapter

    private lateinit var rvBreadcrumbs: RecyclerView
    private lateinit var rvSiblings: RecyclerView
    private lateinit var rvChildren: RecyclerView
    private lateinit var rvPhrase: RecyclerView
    
    private var scrollTopIndicator: View? = null
    private var scrollBottomIndicator: View? = null
    
    private lateinit var containerParent: com.google.android.material.card.MaterialCardView
    private lateinit var ivParent: ImageView
    private lateinit var tvParentLabel: TextView
    
    private lateinit var ivSelectedLarge: ImageView
    private lateinit var btnAddToPhrase: Button
    private lateinit var ivArrowToChildren: ImageView
    private lateinit var ivArrowToSiblings: ImageView
    
    private lateinit var cardSearch: View
    private lateinit var cardSpeak: View
    private lateinit var cardRotate: View
    private lateinit var cardEye: View
    
    private var isDraggingPhrase = false
    private var ignoreScrollEvents = false 
    private var userIsSwiping = false 

    private var siblingsGradLeft: View? = null
    private var siblingsGradRight: View? = null
    private var siblingsArrowLeft: View? = null
    private var siblingsArrowRight: View? = null
    
    private var childrenGradLeft: View? = null
    private var childrenGradRight: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tree_explorer, container, false)
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as? org.libera.pictotree.MainActivity)?.applyUserOrientation()
    }

    override fun onStop() {
        super.onStop()
        activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        (requireActivity() as? org.libera.pictotree.MainActivity)?.restoreSystemOrientation()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        ignoreScrollEvents = true
        viewLifecycleOwner.lifecycleScope.launch {
            delay(800)
            ignoreScrollEvents = false
        }

        setupUIReferences(view)
        setupAdapters()
        setupViewModelLogic(savedInstanceState == null)
        setupListeners(view)
        setupObservers()
    }

    private fun setupUIReferences(view: View) {
        rvBreadcrumbs = view.findViewById(R.id.rv_breadcrumbs)
        rvSiblings = view.findViewById(R.id.rv_siblings)
        rvChildren = view.findViewById(R.id.rv_children_preview)
        rvPhrase = view.findViewById(R.id.rv_phrase)
        
        scrollTopIndicator = view.findViewById(R.id.breadcrumb_scroll_top)
        scrollBottomIndicator = view.findViewById(R.id.breadcrumb_scroll_bottom)
        
        containerParent = view.findViewById(R.id.container_parent)
        ivParent = view.findViewById(R.id.iv_parent)
        tvParentLabel = view.findViewById(R.id.tv_parent_label)
        
        ivSelectedLarge = view.findViewById(R.id.iv_selected_large)
        btnAddToPhrase = view.findViewById(R.id.btn_add_to_phrase)
        ivArrowToChildren = view.findViewById(R.id.iv_arrow_to_children)
        ivArrowToSiblings = view.findViewById(R.id.iv_arrow_to_siblings)
        
        cardSearch = view.findViewById(R.id.card_search)
        cardSpeak = view.findViewById(R.id.card_speak)
        cardRotate = view.findViewById(R.id.card_rotate)
        cardEye = view.findViewById(R.id.card_eye)
        
        siblingsGradLeft = view.findViewById(R.id.siblings_gradient_left)
        siblingsGradRight = view.findViewById(R.id.siblings_gradient_right)
        siblingsArrowLeft = view.findViewById(R.id.iv_siblings_arrow_left)
        siblingsArrowRight = view.findViewById(R.id.iv_siblings_arrow_right)
        
        childrenGradLeft = view.findViewById(R.id.children_gradient_left)
        childrenGradRight = view.findViewById(R.id.children_gradient_right)
    }

    private fun setupAdapters() {
        val username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default"

        breadcrumbAdapter = NodeAdapter(username, R.layout.item_breadcrumb) { node -> viewModel.focusOnNode(node) }
        rvBreadcrumbs.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        rvBreadcrumbs.adapter = breadcrumbAdapter
        
        breadcrumbAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                rvBreadcrumbs.scrollToPosition(breadcrumbAdapter.itemCount - 1)
            }
        })

        rvBreadcrumbs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateVerticalScrollIndicators(rvBreadcrumbs, scrollTopIndicator, scrollBottomIndicator)
            }
        })

        siblingAdapter = NodeAdapter(username, R.layout.item_sibling_node) { node ->
            val position = siblingAdapter.currentList.indexOf(node)
            if (position != -1) {
                siblingAdapter.setSelectedPosition(position)
                viewModel.updateFocusWithinSiblings(node)
            }
        }
        rvSiblings.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvSiblings.adapter = siblingAdapter
        rvSiblings.clipToPadding = false
        val density = resources.displayMetrics.density
        rvSiblings.setPadding((16 * density).toInt(), 0, (120 * density).toInt(), 0)
        
        rvSiblings.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) userIsSwiping = true
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (userIsSwiping && !ignoreScrollEvents) updateFocusFromFirstVisible()
                    userIsSwiping = false
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                positionChildrenArrow() 
                updateHorizontalScrollIndicators(rvSiblings, siblingsGradLeft, siblingsArrowLeft, siblingsGradRight, siblingsArrowRight)
            }
        })

        childrenAdapter = NodeAdapter(username, R.layout.item_sibling_node) { node ->
            val isParentRoot = node.parent != null && node.parent?.parent == null
            if (node.children.isNotEmpty() || isParentRoot) {
                viewModel.focusOnNode(node)
            } else {
                val position = childrenAdapter.currentList.indexOf(node)
                if (position != -1) {
                    childrenAdapter.setSelectedPosition(position)
                }
                viewModel.selectNodeWithoutNavigating(node)
            }
        }
        rvChildren.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvChildren.adapter = childrenAdapter
        rvChildren.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateHorizontalScrollIndicators(rvChildren, childrenGradLeft, null, childrenGradRight, null)
            }
        })

        phraseAdapter = PhraseAdapter(username, onItemClick = { position -> 
            val cardList = phraseAdapter.getCurrentList()
            if (position in cardList.indices) {
                ttsManager.speak(cardList[position].node.label)
            }
        }).apply {
            timerColor = org.libera.pictotree.data.SessionManager(requireContext()).getTimerColor()
        }
        rvPhrase.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvPhrase.adapter = phraseAdapter
        (rvPhrase.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false

        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT, androidx.recyclerview.widget.ItemTouchHelper.UP
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = phraseAdapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition).run { true }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { if (direction == androidx.recyclerview.widget.ItemTouchHelper.UP) viewModel.removeItemFromPhrase(viewHolder.bindingAdapterPosition) }
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) { isDraggingPhrase = true; viewHolder?.itemView?.apply { alpha = 0.8f; scaleX = 1.05f; scaleY = 1.05f } }
            }
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder); isDraggingPhrase = false; viewHolder.itemView.apply { alpha = 1.0f; scaleX = 1.0f; scaleY = 1.0f }
                viewModel.updatePhraseListSilently(phraseAdapter.getCurrentList().toList())
            }
        })
        itemTouchHelper.attachToRecyclerView(rvPhrase)
    }

    private fun positionChildrenArrow() {
        if (!::ivArrowToChildren.isInitialized || !::rvSiblings.isInitialized) return
        val state = viewModel.uiState.value
        val targetNode = state.navigationNode ?: return
        
        val position = siblingAdapter.currentList.indexOfFirst { it.id == targetNode.id }
        if (position != -1) {
            val layoutManager = rvSiblings.layoutManager as LinearLayoutManager
            val itemView = layoutManager.findViewByPosition(position)
            if (itemView != null) {
                // Rendre visible UNIQUEMENT s'il y a des enfants
                ivArrowToChildren.visibility = if (targetNode.children.isNotEmpty()) View.VISIBLE else View.INVISIBLE
                
                val viewCenter = (itemView.left + itemView.right) / 2
                val rvCenter = rvSiblings.width / 2
                ivArrowToChildren.translationX = (viewCenter - rvCenter).toFloat()
            } else {
                ivArrowToChildren.visibility = View.INVISIBLE
            }
        } else {
            ivArrowToChildren.visibility = View.INVISIBLE
        }
    }

    private fun updateHorizontalScrollIndicators(rv: RecyclerView, gradL: View?, arrowL: View?, gradR: View?, arrowR: View?) {
        val canLeft = rv.canScrollHorizontally(-1)
        val canRight = rv.canScrollHorizontally(1)
        gradL?.visibility = if (canLeft) View.VISIBLE else View.INVISIBLE
        arrowL?.visibility = if (canLeft) View.VISIBLE else View.INVISIBLE
        gradR?.visibility = if (canRight) View.VISIBLE else View.INVISIBLE
        arrowR?.visibility = if (canRight) View.VISIBLE else View.INVISIBLE
    }

    private fun updateVerticalScrollIndicators(rv: RecyclerView, gradTop: View?, gradBottom: View?) {
        val canUp = rv.canScrollVertically(-1)
        val canDown = rv.canScrollVertically(1)
        gradTop?.visibility = if (canUp) View.VISIBLE else View.INVISIBLE
        gradBottom?.visibility = if (canDown) View.VISIBLE else View.INVISIBLE
    }

    private fun updateFocusFromFirstVisible() {
        if (ignoreScrollEvents) return
        val layoutManager = rvSiblings.layoutManager as LinearLayoutManager
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position != RecyclerView.NO_POSITION && position < siblingAdapter.currentList.size) {
            val navTargetNode = siblingAdapter.currentList[position]
            viewModel.updateFocusWithinSiblings(navTargetNode)
        }
    }

    private fun setupViewModelLogic(isFreshEntry: Boolean) {
        val username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        val userConfigRepository = org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao())
        ttsManager = TTSManager(requireContext())
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = TreeExplorerViewModel(requireActivity().application, database.treeDao(), database.profileDao(), database.imageDao(), userConfigRepository, org.libera.pictotree.network.RetrofitClient.SERVER_URL, username) as T
        }
        viewModel = ViewModelProvider(requireActivity(), factory)[TreeExplorerViewModel::class.java]
        val targetTreeId = arguments?.getInt("treeId", -1) ?: -1
        val profileId = arguments?.getInt("profileId", -1) ?: -1
        if (isFreshEntry || viewModel.getCurrentTreeId() == -1) {
            if (profileId != -1) { viewLifecycleOwner.lifecycleScope.launch { val trees = database.profileDao().getTreesForProfileOrdered(profileId); viewModel.setProfileTreeContext(profileId, trees.map { it.id }); if (targetTreeId != -1) viewModel.loadTree(targetTreeId) } }
            else if (targetTreeId != -1) viewModel.loadTree(targetTreeId)
        }
    }

    private fun setupListeners(view: View) {
        cardEye.setOnClickListener {
            val dialog = TreeGlobalMapDialog.newInstance(viewModel.getProfileTreeIds(), viewModel.getCurrentTreeId(), org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default", viewModel.uiState.value.previewNode?.id ?: "")
            dialog.show(childFragmentManager, "TreeGlobalMapDialog")
        }
        cardSearch.setOnClickListener {
            val dialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            dialog.onPictoSelected = { result -> viewModel.addToPhrase(TreeNode("search_${result.id}_recherche", result.name ?: "", result.imageUrl ?: "", emptyList())) }
            dialog.show(childFragmentManager, "PictoSearch")
        }
        cardSpeak.setOnClickListener {
            val phrase = viewModel.phraseList.value
            if (phrase.isNotEmpty()) { ttsManager.stop(); phrase.forEachIndexed { index, card -> ttsManager.speak(card.node.label, index.toString()) } }
        }
        view.findViewById<View>(R.id.card_back_to_trees)?.setOnClickListener { findNavController().popBackStack() }
        cardRotate.setOnClickListener { (requireActivity() as? org.libera.pictotree.MainActivity)?.toggleOrientation() }
        
        btnAddToPhrase.setOnClickListener { viewModel.addToPhrase() }
        view.findViewById<View>(R.id.btn_fullscreen_phrase)?.setOnClickListener { findNavController().navigate(R.id.action_treeExplorerFragment_to_phraseFullscreenFragment) }
        view.findViewById<View>(R.id.btn_clear_phrase)?.setOnClickListener { showClearPhraseConfirmation() }
        containerParent.setOnClickListener { viewModel.uiState.value.parent?.let { viewModel.focusOnNode(it) } }
    }

    private fun showClearPhraseConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setTitle("Effacer le bandeau ?").setMessage("Voulez-vous vraiment vider toute la phrase ?")
            .setPositiveButton("Oui") { _, _ -> viewModel.clearPhrase() }.setNegativeButton("Non", null).setIcon(android.R.drawable.ic_menu_delete).show()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userConfig.collect { config ->
                        if (config != null) {
                            cardSearch.visibility = if (config.enableSearch) View.VISIBLE else View.GONE
                            cardSpeak.visibility = if (config.enableTTSButton) View.VISIBLE else View.GONE
                            cardRotate.visibility = if (config.enableRotationButton) View.VISIBLE else View.GONE
                            cardEye.visibility = if (config.enableViewChangeButton) View.VISIBLE else View.GONE
                            
                            // Restaurer le réglage de langue
                            ttsManager.setLanguage(config.locale)
                        }
                    }
                }
                
                launch {
                    viewModel.currentTimeFlow.collect { elapsed ->
                        val firstCard = viewModel.phraseList.value.firstOrNull()
                        if (firstCard?.timeConfig?.mode == org.libera.pictotree.data.model.TimeMode.TIMER && firstCard.timeConfig.endTimeMillis > 0) {
                            phraseAdapter.notifyItemChanged(0)
                            requireActivity().window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            requireActivity().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }

                launch { viewModel.uiState.collect { updateUI(it) } }
                
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
            }
        }
        
        // Restaurer les écouteurs de TTS pour le surlignage des mots
        ttsManager.setListeners(
            onStart = { id -> 
                id.toIntOrNull()?.let { idx -> 
                    requireActivity().runOnUiThread { 
                        phraseAdapter.highlightPosition(idx)
                        rvPhrase.smoothScrollToPosition(idx) 
                    } 
                } 
            }, 
            onDone = { id -> 
                if (id.toIntOrNull() == phraseAdapter.itemCount - 1) {
                    requireActivity().runOnUiThread { 
                        phraseAdapter.highlightPosition(-1) 
                    } 
                }
            }
        )
    }

    private fun updateUI(state: HierarchicalUiState) {
        if (state.isLoading) return
        siblingAdapter.setColorCode(state.colorCode); childrenAdapter.setColorCode(state.colorCode)
        try { containerParent.strokeColor = Color.parseColor(state.colorCode); containerParent.strokeWidth = (3 * resources.displayMetrics.density).toInt() } catch (e: Exception) { containerParent.strokeColor = Color.BLACK }
        
        state.parent?.let { parent ->
            containerParent.visibility = View.VISIBLE; tvParentLabel.text = parent.label
            ivParent.load(getFinalUrl(parent.imageUrl)) { placeholder(R.drawable.ic_launcher_background); error(R.drawable.ic_launcher_background) }
        } ?: run { containerParent.visibility = View.INVISIBLE }
        
        breadcrumbAdapter.submitList(state.breadcrumbs)
        siblingAdapter.submitList(state.siblings) {
            val navPos = state.siblings.indexOfFirst { it.id == state.navigationNode?.id }
            if (navPos != -1) { 
                ignoreScrollEvents = true
                rvSiblings.post { 
                    rvSiblings.scrollToPosition(navPos)
                    rvSiblings.postDelayed({ 
                        ignoreScrollEvents = false 
                        positionChildrenArrow() // Recalculer après défilement
                    }, 200) 
                }
            } else {
                positionChildrenArrow()
            }
        }
        
        state.previewNode?.let { node -> ivSelectedLarge.load(getFinalUrl(node.imageUrl)) }
        childrenAdapter.submitList(state.children) {
            val previewPos = state.children.indexOfFirst { it.id == state.previewNode?.id }
            childrenAdapter.setSelectedPosition(previewPos)
        }
    }

    private fun getFinalUrl(rawUrl: String): Any {
        val username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default"
        val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
        return FileUtils.getFinalImageSource(rawUrl, requireContext(), username, hostUrl)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::ttsManager.isInitialized) ttsManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::ttsManager.isInitialized) ttsManager.shutdown()
    }
}
