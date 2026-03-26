package org.libera.pictotree.ui.editprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.data.SessionManager

class EditProfileFragment : Fragment() {

    private lateinit var viewModel: EditProfileViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editProfileName = view.findViewById<TextInputEditText>(R.id.editProfileName)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewProfileTrees)
        val fabAddTree = view.findViewById<FloatingActionButton>(R.id.fabAddTree)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBarSync)

        val sessionManagerLocal = SessionManager(requireContext())
        val username = sessionManagerLocal.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        val factory = EditProfileViewModelFactory(
            requireActivity().application,
            database.profileDao(),
            database.treeDao(),
            database.imageDao(),
            RetrofitClient.treeApiService
        )
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[EditProfileViewModel::class.java]

        val sessionManager = SessionManager(requireContext())
        val profileId = arguments?.getLong("profileId")?.toInt() ?: -1

        if (profileId != -1) {
            viewModel.loadProfile(profileId)
        }

        val adapter = ProfileTreeAdapter(
            onTreeDelete = { _ ->
                // viewModel.deleteTree(tree)
            },
            onOrderChanged = { _ ->
                // viewModel.updateDisplayOrder(newTrees)
            },
            onViewTree = { tree ->
                val intent = android.content.Intent(requireContext(), org.libera.pictotree.ui.visualizer.TreeVisualizerActivity::class.java)
                intent.putExtra("TREE_ID", tree.id)
                startActivity(intent)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // ================= DRAG & DROP ENGINE =================
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.moveItem(from, to) 
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Le drag est fini (le doigt est laché), on sauvegarde la structure visuelle en base de données.
                adapter.dispatchUpdates()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // ================= FAB DIALOG LAUNCHER =================
        fabAddTree.setOnClickListener {
            val token = sessionManager.getToken()
            if (token != null) {
                Toast.makeText(requireContext(), "Ouverture catalogue distant...", Toast.LENGTH_SHORT).show()
                viewModel.openTreeSelection(token)
            } else {
                Toast.makeText(requireContext(), "Erreur: Non authentifié", Toast.LENGTH_SHORT).show()
            }
        }

        // ================= VIEWMODEL OBSERVER =================
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                launch {
                    viewModel.showTreeSelectionEvent.collect {
                        val dialog = TreeSelectionDialogFragment(
                            remoteTreesFlow = viewModel.remoteTrees,
                            onSearchRequested = { query, isPublic ->
                                val token = sessionManager.getToken()
                                if (token != null) {
                                    viewModel.searchTrees(token, query, isPublic)
                                }
                            },
                            onTreeSelected = { selectedTree ->
                                val token = sessionManager.getToken()
                                val username = sessionManager.getUsername()
                                if (token != null && username != null && profileId != -1) {
                                    viewModel.synchronizeAndImportTree(
                                        treeId = selectedTree.id,
                                        profileId = profileId,
                                        authToken = token,
                                        username = username
                                    )
                                }
                            }
                        )
                        dialog.show(childFragmentManager, "TreeSelection")
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is EditProfileUiState.Loading -> progressBar.visibility = View.VISIBLE
                            is EditProfileUiState.Success -> {
                                progressBar.visibility = View.GONE
                                editProfileName.setText(state.profile.name)
                                adapter.submitList(state.trees)
                            }
                            is EditProfileUiState.Error -> {
                                progressBar.visibility = View.GONE
                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }
}
