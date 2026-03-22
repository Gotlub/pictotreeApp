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

class EditProfileFragment : Fragment() {

    // NB: L'instance du ViewModel sera idéalement injectée via Hilt/Koin ou ViewModelProvider 
    // private val viewModel: EditProfileViewModel by viewModels()

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

        val adapter = ProfileTreeAdapter(
            onTreeDelete = { tree ->
                // viewModel.deleteTree(tree)
            },
            onOrderChanged = { newTrees ->
                // viewModel.updateDisplayOrder(newTrees)
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
            Toast.makeText(requireContext(), "Fetch Network API...", Toast.LENGTH_SHORT).show()
            // viewModel.fetchAvailableTrees() => déclenche un affichage de TreeSelectionDialogFragment
        }

        // ================= VIEWMODEL OBSERVER =================
        /* 
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        */
    }
}
