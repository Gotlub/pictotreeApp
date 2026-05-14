package org.libera.pictotree.ui.treeselection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.repository.ProfileRepository
import org.libera.pictotree.data.repository.UserConfigRepository
import org.libera.pictotree.utils.TTSManager
import org.libera.pictotree.ui.explorer.PhraseAdapter
import org.libera.pictotree.ui.explorer.TreeExplorerViewModel

class TreeSelectionFragment : Fragment() {

    private lateinit var viewModel: TreeSelectionViewModel
    private lateinit var explorerViewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager
    private lateinit var adapter: TreeAdapter
    private lateinit var phraseAdapter: PhraseAdapter

    private lateinit var rvTrees: RecyclerView
    private lateinit var rvPhrase: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnFullscreenPhrase: View

    private var isDraggingPhrase = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tree_selection, container, false)
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as? org.libera.pictotree.MainActivity)?.applyUserOrientation()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profileId = arguments?.getInt("profileId", -1) ?: -1
        val username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        
        val profileRepository = ProfileRepository(
            requireContext(),
            database.profileDao(),
            database.treeDao(),
            database.imageDao(),
            username
        )
        
        ttsManager = TTSManager(requireContext())

        val factory = TreeSelectionViewModelFactory(profileRepository, profileId)
        viewModel = ViewModelProvider(this, factory)[TreeSelectionViewModel::class.java]

        val userConfigRepository = UserConfigRepository(database.userConfigDao())
        val explorerFactory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
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
        explorerViewModel = ViewModelProvider(requireActivity(), explorerFactory)[TreeExplorerViewModel::class.java]
        
        explorerViewModel.resetSelection()

        setupUI(view)
        setupObservers()
    }

    private fun setupUI(view: View) {
        val username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default"
        rvTrees = view.findViewById(R.id.rvTrees)
        rvPhrase = view.findViewById(R.id.rv_phrase)
        progressBar = view.findViewById(R.id.progressBar)
        btnFullscreenPhrase = view.findViewById(R.id.btn_fullscreen_phrase)

        val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
        adapter = TreeAdapter(username, hostUrl, allowNetwork = false) { tree ->
            // LIRE LES PRÉFÉRENCES DU PROFIL
            viewLifecycleOwner.lifecycleScope.launch {
                val profileId = arguments?.getInt("profileId", -1) ?: -1
                val database = AppDatabase.getDatabase(requireContext(), username)
                val profile = database.profileDao().getProfileById(profileId)
                val settings = profile?.settingsJson?.let {
                    try { com.google.gson.Gson().fromJson(it, org.libera.pictotree.data.model.ProfileSettings::class.java) }
                    catch (e: Exception) { org.libera.pictotree.data.model.ProfileSettings() }
                } ?: org.libera.pictotree.data.model.ProfileSettings()

                val bundle = Bundle().apply {
                    putInt("treeId", tree.id)
                    putInt("profileId", profileId)
                    putString("username", username)
                }

                if (settings.startupView == "MAP") {
                    // OUVRIR DIRECTEMENT LA CARTE GLOBALE (Désormais injectée via childFragmentManager pour cohabiter)
                    val treeIds = explorerViewModel.getProfileTreeIds()
                    val dialog = org.libera.pictotree.ui.explorer.TreeGlobalMapDialog.newInstance(
                        treeIds, tree.id, username, ""
                    )
                    dialog.show(childFragmentManager, "TreeGlobalMapDialog")
                } else {
                    findNavController().navigate(R.id.action_treeSelectionFragment_to_treeExplorerFragment, bundle)
                }
            }
        }
        val spanCount = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 6 else 2
        rvTrees.layoutManager = GridLayoutManager(requireContext(), spanCount)
        rvTrees.adapter = adapter

        phraseAdapter = PhraseAdapter(
            username = username,
            onItemClick = { node -> ttsManager.speak(node.label) }
        )
        rvPhrase.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvPhrase.adapter = phraseAdapter

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
                    explorerViewModel.removeItemFromPhrase(viewHolder.bindingAdapterPosition)
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
                explorerViewModel.updatePhraseListSilently(phraseAdapter.getCurrentList().toList())
            }
        })
        itemTouchHelper.attachToRecyclerView(rvPhrase)

        view.findViewById<View>(R.id.card_speak)?.setOnClickListener {
            val phrase = explorerViewModel.phraseList.value
            if (phrase.isNotEmpty()) {
                ttsManager.stop()
                phrase.forEachIndexed { index, node -> ttsManager.speak(node.label, index.toString()) }
            }
        }
        view.findViewById<View>(R.id.card_rotate)?.setOnClickListener {
            (requireActivity() as? org.libera.pictotree.MainActivity)?.toggleOrientation()
        }
        view.findViewById<View>(R.id.btn_fullscreen_phrase).setOnClickListener {
            findNavController().navigate(R.id.phraseFullscreenFragment)
        }
        view.findViewById<View>(R.id.btn_clear_phrase)?.setOnClickListener {
            showClearPhraseConfirmation()
        }
    }

    private fun showClearPhraseConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Effacer le bandeau ?")
            .setMessage("Voulez-vous vraiment vider toute la phrase ?")
            .setPositiveButton("Oui") { _, _ -> explorerViewModel.clearPhrase() }
            .setNegativeButton("Non", null)
            .setIcon(android.R.drawable.ic_menu_delete)
            .show()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    val currentProfileId = arguments?.getInt("profileId", -1) ?: -1
                    val currentUsername = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default"
                    val profile = AppDatabase.getDatabase(requireContext(), currentUsername).profileDao().getProfileById(currentProfileId)
                    val settings = profile?.settingsJson?.let {
                        try { com.google.gson.Gson().fromJson(it, org.libera.pictotree.data.model.ProfileSettings::class.java) }
                        catch (e: Exception) { org.libera.pictotree.data.model.ProfileSettings() }
                    } ?: org.libera.pictotree.data.model.ProfileSettings()

                    view?.findViewById<View>(R.id.card_search)?.visibility = 
                        if (settings.enableSearch) View.VISIBLE else View.GONE
                }

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is TreeSelectionUiState.Loading -> progressBar.visibility = View.VISIBLE
                            is TreeSelectionUiState.Success -> {
                                val currentProfileId = arguments?.getInt("profileId", -1) ?: -1
                                progressBar.visibility = View.GONE
                                adapter.submitList(state.trees)
                                explorerViewModel.setProfileTreeContext(currentProfileId, state.trees.map { it.tree.id })
                            }
                            is TreeSelectionUiState.Error -> {
                                progressBar.visibility = View.GONE
                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            }
                            else -> {} // Handle potential empty state or others
                        }
                    }
                }

                launch {
                    var lastPhraseSize = 0
                    explorerViewModel.phraseList.collect { phrase ->
                        if (!isDraggingPhrase) {
                            phraseAdapter.submitList(phrase)
                            if (phrase.size > lastPhraseSize) rvPhrase.smoothScrollToPosition(phrase.size - 1)
                        }
                        lastPhraseSize = phrase.size
                    }
                }

                launch {
                    explorerViewModel.userConfig.collect { config ->
                        config?.let { ttsManager.setLanguage(it.locale) }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::ttsManager.isInitialized) ttsManager.shutdown()
    }
}
