package org.libera.pictotree.ui.explorer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.utils.TTSManager

class TreeExplorerFragment : Fragment() {

    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager

    // Adapters
    private lateinit var breadcrumbAdapter: NodeAdapter
    private lateinit var siblingAdapter: NodeAdapter
    private lateinit var childrenAdapter: NodeAdapter
    private lateinit var phraseAdapter: PhraseAdapter

    // UI References
    private lateinit var rvBreadcrumbs: RecyclerView
    private lateinit var rvSiblings: RecyclerView
    private lateinit var rvChildren: RecyclerView
    private lateinit var rvPhrase: RecyclerView
    
    private lateinit var containerParent: View
    private lateinit var ivParent: ImageView
    private lateinit var tvParentLabel: TextView
    
    private lateinit var ivSelectedLarge: ImageView
    private lateinit var btnAddToPhrase: Button
    private lateinit var fabSpeak: View
    private lateinit var layoutGhostFrames: View
    
    private lateinit var scrollTopIndicator: View
    private lateinit var scrollBottomIndicator: View

    private val snapHelper = LinearSnapHelper()

    // Décoration pour l'effet de superposition (overlap)
    private inner class OverlapDecoration(private val overlapPx: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position > 0) {
                outRect.left = -overlapPx
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tree_explorer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUIReferences(view)
        setupAdapters()
        setupViewModel()
        setupListeners(view)
        setupObservers()
    }

    private fun setupUIReferences(view: View) {
        rvBreadcrumbs = view.findViewById(R.id.rv_breadcrumbs)
        rvSiblings = view.findViewById(R.id.rv_siblings)
        rvChildren = view.findViewById(R.id.rv_children_preview)
        rvPhrase = view.findViewById(R.id.rv_phrase)
        
        containerParent = view.findViewById(R.id.container_parent)
        ivParent = view.findViewById(R.id.iv_parent)
        tvParentLabel = view.findViewById(R.id.tv_parent_label)
        
        ivSelectedLarge = view.findViewById(R.id.iv_selected_large)
        btnAddToPhrase = view.findViewById(R.id.btn_add_to_phrase)
        fabSpeak = view.findViewById(R.id.fab_speak)
        layoutGhostFrames = view.findViewById(R.id.layout_ghost_frames)
        scrollTopIndicator = view.findViewById(R.id.breadcrumb_scroll_top)
        scrollBottomIndicator = view.findViewById(R.id.breadcrumb_scroll_bottom)
    }

    private fun setupAdapters() {
        // Breadcrumbs (Miniatures verticales à gauche)
        breadcrumbAdapter = NodeAdapter(R.layout.item_breadcrumb) { node ->
            viewModel.focusOnNode(node)
        }
        rvBreadcrumbs.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        rvBreadcrumbs.adapter = breadcrumbAdapter
        
        // Indicateurs de scroll pour les breadcrumbs
        rvBreadcrumbs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                scrollTopIndicator.visibility = if (recyclerView.canScrollVertically(-1)) View.VISIBLE else View.INVISIBLE
                scrollBottomIndicator.visibility = if (recyclerView.canScrollVertically(1)) View.VISIBLE else View.INVISIBLE
            }
        })

        // Siblings (Middle Zone)
        siblingAdapter = NodeAdapter(R.layout.item_sibling_node) { node ->
            val position = siblingAdapter.currentList.indexOf(node)
            if (position != -1) {
                val layoutManager = rvSiblings.layoutManager as LinearLayoutManager
                val view = layoutManager.findViewByPosition(position)
                if (view != null) {
                    val snapDistance = snapHelper.calculateDistanceToFinalSnap(layoutManager, view)
                    if (snapDistance != null) {
                        rvSiblings.smoothScrollBy(snapDistance[0], snapDistance[1])
                    }
                } else {
                    rvSiblings.smoothScrollToPosition(position)
                }
                
                siblingAdapter.setSelectedPosition(position)
                viewModel.updateFocusWithinSiblings(node)
            }
        }
        rvSiblings.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvSiblings.adapter = siblingAdapter
        
        // Ajout de padding pour permettre aux éléments des bords de se centrer
        rvSiblings.clipToPadding = false
        val screenWidth = resources.displayMetrics.widthPixels
        val itemWidth = resources.getDimensionPixelSize(R.dimen.sibling_item_width)
        val padding = (screenWidth - itemWidth) / 2
        rvSiblings.setPadding(padding, 0, padding, 0)

        snapHelper.attachToRecyclerView(rvSiblings)
        
        rvSiblings.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateFocusFromSnap()
                }
            }
        })

        // Children Preview (Bottom Right) - BANDEAU AVEC SUPERPOSITION UNIFORMISÉE (Gabarit 140dp)
        childrenAdapter = NodeAdapter(R.layout.item_child_preview) { node ->
            viewModel.focusOnNode(node)
        }
        rvChildren.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvChildren.adapter = childrenAdapter
        
        // Appliquer l'effet de superposition (60dp d'overlap pour des cartes de 140dp de large mais moins hautes)
        val overlapPx = (60 * resources.displayMetrics.density).toInt()
        rvChildren.addItemDecoration(OverlapDecoration(overlapPx))

        // Phrase Band (Bottom)
        phraseAdapter = PhraseAdapter(
            onItemClick = { node -> ttsManager.speak(node.label) },
            onItemLongClick = { index -> viewModel.removeItemFromPhrase(index) }
        )
        rvPhrase.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvPhrase.adapter = phraseAdapter
    }

    private fun updateFocusFromSnap() {
        val centerView = snapHelper.findSnapView(rvSiblings.layoutManager)
        centerView?.let {
            val position = rvSiblings.getChildAdapterPosition(it)
            if (position != RecyclerView.NO_POSITION && position < siblingAdapter.currentList.size) {
                val focusedNode = siblingAdapter.currentList[position]
                siblingAdapter.setSelectedPosition(position)
                viewModel.updateFocusWithinSiblings(focusedNode)
            }
        }
    }

    private fun setupViewModel() {
        val username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        val userConfigRepository = org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao())
        
        ttsManager = TTSManager(requireContext())

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TreeExplorerViewModel(
                    requireActivity().application, 
                    database.treeDao(), 
                    userConfigRepository,
                    org.libera.pictotree.network.RetrofitClient.SERVER_URL, 
                    username
                ) as T
            }
        }
        viewModel = ViewModelProvider(requireActivity(), factory)[TreeExplorerViewModel::class.java]
        
        val targetTreeId = arguments?.getInt("treeId", -1) ?: -1
        val profileId = arguments?.getInt("profileId", -1) ?: -1

        if (profileId != -1) {
            viewLifecycleOwner.lifecycleScope.launch {
                val trees = database.profileDao().getTreesForProfileOrdered(profileId)
                viewModel.setProfileTreeContext(trees.map { it.id })
                if (targetTreeId != -1) viewModel.loadTree(targetTreeId)
            }
        } else if (targetTreeId != -1) {
            viewModel.loadTree(targetTreeId)
        }
    }

    private fun setupListeners(view: View) {
        view.findViewById<View>(R.id.card_eye).setOnClickListener {
            // Vue Globale (Treant)
            val centerNodeId = viewModel.uiState.value.focusedNode?.id ?: ""
            val profileTreeIds = viewModel.getProfileTreeIds()
            val currentTreeId = viewModel.getCurrentTreeId()
            val dialog = TreeGlobalMapDialog.newInstance(
                treeIds = profileTreeIds,
                currentTreeId = currentTreeId,
                username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default",
                selectedNodeId = centerNodeId
            )
            dialog.show(childFragmentManager, "TreeGlobalMapDialog")
        }

        view.findViewById<View>(R.id.card_search).setOnClickListener {
            val dialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            dialog.onPictoSelected = { result ->
                val searchNode = TreeNode(
                    id = "search_${result.id}_recherche",
                    label = result.name,
                    imageUrl = result.imageUrl,
                    children = emptyList()
                )
                viewModel.addToPhrase(searchNode)
            }
            dialog.show(childFragmentManager, "PictoSearch")
        }

        view.findViewById<View>(R.id.card_back_to_trees).setOnClickListener {
            findNavController().popBackStack()
        }

        btnAddToPhrase.setOnClickListener {
            viewModel.addToPhrase()
        }

        view.findViewById<View>(R.id.btn_fullscreen_phrase).setOnClickListener {
            findNavController().navigate(R.id.action_treeExplorerFragment_to_phraseFullscreenFragment)
        }

        fabSpeak.setOnClickListener {
            val phrase = viewModel.phraseList.value
            if (phrase.isEmpty()) return@setOnClickListener
            ttsManager.stop()
            phrase.forEachIndexed { index, node ->
                ttsManager.speak(node.label, index.toString())
            }
        }
        
        containerParent.setOnClickListener {
            viewModel.uiState.value.parent?.let { viewModel.focusOnNode(it) }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUI(state)
                        // Forcer la mise à jour des indicateurs après le chargement des données
                        rvBreadcrumbs.post {
                            scrollTopIndicator.visibility = if (rvBreadcrumbs.canScrollVertically(-1)) View.VISIBLE else View.INVISIBLE
                            scrollBottomIndicator.visibility = if (rvBreadcrumbs.canScrollVertically(1)) View.VISIBLE else View.INVISIBLE
                        }
                    }
                }
                launch {
                    viewModel.phraseList.collect { phrase ->
                        phraseAdapter.submitList(phrase)
                        layoutGhostFrames.visibility = if (phrase.isEmpty()) View.VISIBLE else View.GONE
                        if (phrase.isNotEmpty()) rvPhrase.smoothScrollToPosition(phrase.size - 1)
                    }
                }
                launch {
                    viewModel.userConfig.collect { config ->
                        config?.let { ttsManager.setLanguage(it.locale) }
                    }
                }
            }
        }

        ttsManager.setListeners(
            onStart = { utteranceId ->
                val index = utteranceId.toIntOrNull() ?: -1
                requireActivity().runOnUiThread {
                    if (index != -1) {
                        phraseAdapter.highlightPosition(index)
                        rvPhrase.smoothScrollToPosition(index)
                    }
                }
            },
            onDone = { utteranceId ->
                val index = utteranceId.toIntOrNull() ?: -1
                if (index == phraseAdapter.itemCount - 1) {
                    requireActivity().runOnUiThread { phraseAdapter.highlightPosition(-1) }
                }
            }
        )
    }

    private fun updateUI(state: HierarchicalUiState) {
        if (state.isLoading) return

        // Top Bar: Parent
        if (state.parent != null) {
            containerParent.visibility = View.VISIBLE
            tvParentLabel.text = state.parent.label
            ivParent.load(state.parent.imageUrl) { placeholder(R.drawable.ic_launcher_foreground) }
        } else {
            containerParent.visibility = View.INVISIBLE
        }

        // Top Bar: Breadcrumbs
        breadcrumbAdapter.submitList(state.breadcrumbs)

        // Middle Zone: Siblings
        val oldSiblings = siblingAdapter.currentList
        siblingAdapter.submitList(state.siblings) {
            // Une fois la liste soumise, on centre le focusNode
            val position = state.siblings.indexOfFirst { it.id == state.focusedNode?.id }
            if (position != -1) {
                if (oldSiblings != state.siblings) {
                    // Si la liste a changé (navigation haut/bas), on scrolle direct
                    rvSiblings.scrollToPosition(position)
                }
                siblingAdapter.setSelectedPosition(position)
            }
        }

        // Bottom Zone: Large Focus
        state.focusedNode?.let { node ->
            ivSelectedLarge.load(node.imageUrl) { placeholder(R.drawable.ic_launcher_foreground) }
        }

        // Bottom Zone: Children (Full bandeau superposé uniformisé)
        childrenAdapter.submitList(state.children)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::ttsManager.isInitialized) ttsManager.shutdown()
    }
}
