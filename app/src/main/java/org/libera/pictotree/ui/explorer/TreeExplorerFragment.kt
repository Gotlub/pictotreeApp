package org.libera.pictotree.ui.explorer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.database.entity.UserConfig
import kotlin.math.abs

import org.libera.pictotree.utils.TTSManager

class TreeExplorerFragment : Fragment() {

    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var gestureDetector: GestureDetector
    private lateinit var ttsManager: TTSManager

    // Layout References
    private lateinit var containerCenter: View
    private lateinit var ivCenter: ImageView
    private lateinit var ivTop: ImageView
    private lateinit var ivBottom: ImageView
    private lateinit var ivLeft: ImageView
    private lateinit var ivRight: ImageView
    private lateinit var tvCenterLabel: TextView
    
    // HUD Arrows
    private lateinit var arrowTop: ImageView
    private lateinit var arrowBottom: ImageView
    private lateinit var arrowLeft: ImageView
    private lateinit var arrowRight: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_tree_explorer, container, false)
        
        containerCenter = root.findViewById(R.id.container_center)
        ivCenter = root.findViewById(R.id.iv_center)
        ivTop = root.findViewById(R.id.iv_top)
        ivBottom = root.findViewById(R.id.iv_bottom)
        ivLeft = root.findViewById(R.id.iv_left)
        ivRight = root.findViewById(R.id.iv_right)
        tvCenterLabel = root.findViewById(R.id.tv_center_label)
        
        arrowTop = root.findViewById(R.id.arrow_top)
        arrowBottom = root.findViewById(R.id.arrow_bottom)
        arrowLeft = root.findViewById(R.id.arrow_left)
        arrowRight = root.findViewById(R.id.arrow_right)
        
        val fabEye = root.findViewById<View>(R.id.fab_eye)
        val fabSearch = root.findViewById<View>(R.id.fab_search)
        val fabSpeak = root.findViewById<View>(R.id.fab_speak)
        val rvPhrase = root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_phrase)
        val btnFullscreen = root.findViewById<View>(R.id.btn_fullscreen_phrase)

        btnFullscreen.setOnClickListener {
            findNavController().navigate(R.id.action_treeExplorerFragment_to_phraseFullscreenFragment)
        }

        // Configuration PhraseAdapter
        val phraseAdapter = PhraseAdapter()
        rvPhrase.adapter = phraseAdapter
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        rvPhrase.layoutManager = layoutManager

        // Drag & Drop / Swipe to Delete
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT,
            androidx.recyclerview.widget.ItemTouchHelper.UP
        ) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean {
                viewModel.moveItemInPhrase(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                if (direction == androidx.recyclerview.widget.ItemTouchHelper.UP) {
                    viewModel.removeItemFromPhrase(viewHolder.bindingAdapterPosition)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(rvPhrase)

        // Configuration ViewModel / Database context
        val username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "dummy"
        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val profileDao = database.profileDao()
        val userConfigRepository = org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao())

        // Configuration TTS
        ttsManager = TTSManager(requireContext())

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TreeExplorerViewModel(
                    requireActivity().application, 
                    treeDao, 
                    userConfigRepository,
                    org.libera.pictotree.network.RetrofitClient.SERVER_URL, 
                    username
                ) as T
            }
        }
        viewModel = ViewModelProvider(requireActivity(), factory)[TreeExplorerViewModel::class.java]
        
        // Observer la langue depuis le ViewModel (Cache réactif)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.userConfig.collect { config ->
                    android.util.Log.d("PictoTreeNav", "UserConfig changed: locale=${config?.locale}")
                    config?.let { ttsManager.setLanguage(it.locale) }
                }
            }
        }
        
        val targetTreeId = arguments?.getInt("treeId", -1) ?: -1
        val profileId = arguments?.getInt("profileId", -1) ?: -1

        if (profileId != -1) {
            viewLifecycleOwner.lifecycleScope.launch {
                val trees = profileDao.getTreesForProfileOrdered(profileId)
                viewModel.setProfileTreeContext(trees.map { it.id })
                // Important : On charge l'arbre APRÈS avoir défini le contexte du profil
                // pour que le calcul des "voisins" (prev/next tree) fonctionne dès l'ouverture.
                if (targetTreeId != -1) {
                    viewModel.loadTree(targetTreeId)
                }
            }
        } else {
            // Si pas de profil, charger directement
            if (targetTreeId != -1) {
                viewModel.loadTree(targetTreeId)
            }
        }
        
        // Moteur Tactile Hybride (Gestes & Clics)
        setupGestureDetection(root)
        setupTapToNavigate()
        
        // Observation des États UI (Moteur & Recycler)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUI(state)
                    }
                }
                launch {
                    viewModel.phraseList.collect { phrase ->
                        phraseAdapter.submitList(phrase)
                        if (phrase.isNotEmpty()) {
                            rvPhrase.smoothScrollToPosition(phrase.size - 1)
                        }
                    }
                }
            }
        }
        
        // Binding de la Vue Globale
        fabEye.setOnClickListener {
            val centerNodeId = viewModel.uiState.value.center?.id ?: ""
            val profileTreeIds = viewModel.getProfileTreeIds()
            val currentTreeId = viewModel.getCurrentTreeId()

            android.util.Log.d("PictoTreeNav", "VIEW_CHANGE: Opening Global Map. From Tree: $currentTreeId, Node: $centerNodeId")

            val dialog = TreeGlobalMapDialog.newInstance(
                treeIds = profileTreeIds,
                currentTreeId = currentTreeId,
                username = username,
                selectedNodeId = centerNodeId
            )
            dialog.show(childFragmentManager, "TreeGlobalMapDialog")
        }
        
        // Binding de la Recherche
        fabSearch.setOnClickListener {
            val dialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            dialog.onPictoSelected = { result ->
                // Création d'un TreeNode virtuel pour le panier
                // Format ID: search_resultID_recherche (pour respecter treeId_nodeId_path)
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
        
        // Binding TTS
        ttsManager.setListeners(
            onStart = { utteranceId ->
                // L'ID correspond à l'index dans la liste de phrase
                val index = utteranceId.toIntOrNull() ?: -1
                requireActivity().runOnUiThread {
                    if (index != -1) {
                        phraseAdapter.highlightPosition(index)
                        rvPhrase.smoothScrollToPosition(index)
                    }
                }
            },
            onDone = { utteranceId ->
                // Si c'est le dernier élément, on éteint l'illumination
                val index = utteranceId.toIntOrNull() ?: -1
                val totalItems = phraseAdapter.itemCount
                if (index == totalItems - 1) {
                    requireActivity().runOnUiThread {
                        phraseAdapter.highlightPosition(-1)
                    }
                }
            }
        )

        fabSpeak.setOnClickListener {
            val phrase = viewModel.phraseList.value
            if (phrase.isEmpty()) return@setOnClickListener

            // On vide la file et on lance la lecture séquentielle
            ttsManager.stop()
            phrase.forEachIndexed { index, node ->
                ttsManager.speak(node.label, index.toString())
            }
        }

        fabSpeak.setOnLongClickListener {
            viewModel.clearPhrase()
            ttsManager.stop()
            phraseAdapter.highlightPosition(-1)
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

    private fun setupTapToNavigate() {
        containerCenter.setOnClickListener { viewModel.addToPhrase() }
        ivTop.setOnClickListener { triggerNavigation(Direction.TOP) }
        ivBottom.setOnClickListener { triggerNavigation(Direction.BOTTOM) }
        ivLeft.setOnClickListener { triggerNavigation(Direction.LEFT) }
        ivRight.setOnClickListener { triggerNavigation(Direction.RIGHT) }
    }

    private fun setupGestureDetection(root: View) {
        val spatialEngine = root.findViewById<View>(R.id.layout_spatial_engine)
        
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > 100 && abs(velocityX) > 100) {
                        if (diffX > 0) {
                            // Swipe Right -> Aller vers le noeud Sibling Gauche (La grille glisse vers la droite)
                            triggerNavigation(Direction.LEFT)
                        } else {
                            // Swipe Left -> Aller vers le Sibling Droite
                            triggerNavigation(Direction.RIGHT)
                        }
                        return true
                    }
                } else {
                    if (abs(diffY) > 100 && abs(velocityY) > 100) {
                        if (diffY > 0) {
                            // Swipe Bas -> Envoi dans le panier sans bouger 
                            // (Suivant specs UX_UI modifier: "Tap/Swipe Bas depuis le centre = Basket")
                            viewModel.addToPhrase()
                        } else {
                            // Swipe Haut -> Grille monte -> On Va au Child (Bottom)
                            triggerNavigation(Direction.BOTTOM)
                        }
                        return true
                    }
                }
                return false
            }
        })
        
        // On n'intercepte que si le touch n'est pas consommé par une vue enfant (ex: nos boutons de clic)
        spatialEngine.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Ne pas retourner 'true' aveuglément pour laisser les clics enfants passer
            false
        }
    }

    enum class Direction { TOP, BOTTOM, LEFT, RIGHT }

    private fun triggerNavigation(direction: Direction) {
        val state = viewModel.uiState.value
        
        // Navigation inter-arbres ?
        if (direction == Direction.LEFT && state.prevTreeId != null) {
            viewModel.loadTree(state.prevTreeId)
            return
        }
        if (direction == Direction.RIGHT && state.nextTreeId != null) {
            viewModel.loadTree(state.nextTreeId)
            return
        }

        val targetNode = when (direction) {
            Direction.TOP -> state.top
            Direction.BOTTOM -> state.bottom
            Direction.LEFT -> state.left
            Direction.RIGHT -> state.right
        }

        if (targetNode == null) {
            return
        }

        // Animation fluide native Android pour remplacer le décalage 400px hasardeux
        val rootView = requireView().findViewById<ViewGroup>(R.id.layout_spatial_engine)
        androidx.transition.TransitionManager.beginDelayedTransition(rootView, androidx.transition.ChangeBounds().setDuration(200))
        
        // Le ViewModel gère la mise à jour des états qui sera répercutée dans updateUI() avec animation
        viewModel.focusOnNode(targetNode)
    }

    
    private fun updateUI(state: SpatialUiState) {
        if (state.isLoading) return

        if (state.error != null) {
            tvCenterLabel.text = state.error
            ivCenter.setImageResource(android.R.drawable.ic_dialog_alert)
            ivTop.visibility = View.INVISIBLE
            ivBottom.visibility = View.INVISIBLE
            ivLeft.visibility = View.INVISIBLE
            ivRight.visibility = View.INVISIBLE
            arrowTop.visibility = View.INVISIBLE
            arrowBottom.visibility = View.INVISIBLE
            arrowLeft.visibility = View.INVISIBLE
            arrowRight.visibility = View.INVISIBLE
            return
        }

        // Center
        tvCenterLabel.text = state.center?.label ?: ""
        state.center?.imageUrl?.takeIf { it.isNotEmpty() }?.let { ivCenter.load(it) } ?: ivCenter.setImageResource(R.drawable.ic_launcher_foreground)

        // Top
        ivTop.visibility = if (state.top != null) View.VISIBLE else View.INVISIBLE
        arrowTop.visibility = if (state.top != null) View.VISIBLE else View.INVISIBLE
        state.top?.imageUrl?.takeIf { it.isNotEmpty() }?.let { ivTop.load(it) } ?: ivTop.setImageResource(R.drawable.ic_launcher_foreground)

        // Bottom
        ivBottom.visibility = if (state.bottom != null) View.VISIBLE else View.INVISIBLE
        arrowBottom.visibility = if (state.bottom != null) View.VISIBLE else View.INVISIBLE
        state.bottom?.imageUrl?.takeIf { it.isNotEmpty() }?.let { ivBottom.load(it) } ?: ivBottom.setImageResource(R.drawable.ic_launcher_foreground)

        // Left
        ivLeft.visibility = if (state.left != null) View.VISIBLE else View.INVISIBLE
        arrowLeft.visibility = if (state.left != null) View.VISIBLE else View.INVISIBLE
        state.left?.imageUrl?.takeIf { it.isNotEmpty() }?.let { ivLeft.load(it) } ?: ivLeft.setImageResource(R.drawable.ic_launcher_foreground)

        // Right
        ivRight.visibility = if (state.right != null) View.VISIBLE else View.INVISIBLE
        arrowRight.visibility = if (state.right != null) View.VISIBLE else View.INVISIBLE
        state.right?.imageUrl?.takeIf { it.isNotEmpty() }?.let { ivRight.load(it) } ?: ivRight.setImageResource(R.drawable.ic_launcher_foreground)
    }
}
