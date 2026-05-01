package org.libera.pictotree.ui.treeselection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.repository.ProfileRepository
import org.libera.pictotree.ui.explorer.PhraseAdapter
import org.libera.pictotree.ui.explorer.TreeExplorerViewModel
import org.libera.pictotree.ui.explorer.TreeNode
import org.libera.pictotree.utils.TTSManager

class TreeSelectionFragment : Fragment() {

    private lateinit var viewModel: TreeSelectionViewModel
    private lateinit var explorerViewModel: TreeExplorerViewModel
    private lateinit var adapter: TreeAdapter
    private lateinit var phraseAdapter: PhraseAdapter
    private lateinit var ttsManager: TTSManager

    private lateinit var rvTrees: RecyclerView
    private lateinit var rvPhrase: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabSearch: FloatingActionButton
    private lateinit var fabSpeak: FloatingActionButton
    private lateinit var btnFullscreenPhrase: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tree_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profileId = arguments?.getInt("profileId", -1) ?: -1
        val username = org.libera.pictotree.data.SessionManager(requireContext()).getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        val profileRepository = ProfileRepository(database.profileDao())
        
        // TTS
        ttsManager = TTSManager(requireContext())

        // ViewModel pour la sélection d'arbres
        val factory = TreeSelectionViewModelFactory(profileRepository, profileId)
        viewModel = ViewModelProvider(this, factory)[TreeSelectionViewModel::class.java]

        // ViewModel partagé pour la phrase et le panier (on réutilise celui de l'explorer)
        val userConfigRepository = org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao())
        val explorerFactory = object : ViewModelProvider.Factory {
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
        explorerViewModel = ViewModelProvider(requireActivity(), explorerFactory)[TreeExplorerViewModel::class.java]

        setupUI(view)
        setupObservers()
    }

    private fun setupUI(view: View) {
        rvTrees = view.findViewById(R.id.rvTrees)
        rvPhrase = view.findViewById(R.id.rv_phrase)
        progressBar = view.findViewById(R.id.progressBar)
        fabSearch = view.findViewById(R.id.fab_search)
        fabSpeak = view.findViewById(R.id.fab_speak)
        btnFullscreenPhrase = view.findViewById(R.id.btn_fullscreen_phrase)

        // Adapter pour les arbres
        adapter = TreeAdapter { tree ->
            val bundle = Bundle().apply {
                putInt("treeId", tree.id)
                putInt("profileId", arguments?.getInt("profileId", -1) ?: -1)
            }
            findNavController().navigate(R.id.action_treeSelectionFragment_to_treeExplorerFragment, bundle)
        }
        rvTrees.layoutManager = GridLayoutManager(requireContext(), 2)
        rvTrees.adapter = adapter

        // Adapter pour la phrase
        phraseAdapter = PhraseAdapter(
            onItemClick = { node -> ttsManager.speak(node.label) },
            onItemLongClick = { index -> explorerViewModel.removeItemFromPhrase(index) }
        )
        rvPhrase.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvPhrase.adapter = phraseAdapter

        fabSearch.setOnClickListener {
            val dialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            dialog.onPictoSelected = { result ->
                val searchNode = TreeNode(
                    id = "search_${result.id}_recherche",
                    label = result.name,
                    imageUrl = result.imageUrl,
                    children = emptyList()
                )
                explorerViewModel.addToPhrase(searchNode)
            }
            dialog.show(childFragmentManager, "PictoSearchDialog")
        }

        fabSpeak.setOnClickListener {
            val phrase = explorerViewModel.phraseList.value.joinToString(" ") { it.label }
            if (phrase.isNotEmpty()) {
                ttsManager.speak(phrase)
            }
        }

        btnFullscreenPhrase.setOnClickListener {
            findNavController().navigate(R.id.phraseFullscreenFragment)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is TreeSelectionUiState.Loading -> progressBar.visibility = View.VISIBLE
                            is TreeSelectionUiState.Success -> {
                                progressBar.visibility = View.GONE
                                adapter.submitList(state.trees)
                            }
                            is TreeSelectionUiState.Empty -> {
                                progressBar.visibility = View.GONE
                                // Optionnel: Afficher un message vide
                            }
                            is TreeSelectionUiState.Error -> {
                                progressBar.visibility = View.GONE
                                // Optionnel: Afficher une erreur
                            }
                        }
                    }
                }

                launch {
                    explorerViewModel.phraseList.collect { phrase ->
                        phraseAdapter.submitList(phrase)
                        if (phrase.isNotEmpty()) {
                            rvPhrase.smoothScrollToPosition(phrase.size - 1)
                        }
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
}
