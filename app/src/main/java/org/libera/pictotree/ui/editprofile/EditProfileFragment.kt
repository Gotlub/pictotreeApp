package org.libera.pictotree.ui.editprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        val btnSaveProfile = view.findViewById<android.widget.Button>(R.id.btnSaveProfile)
        val btnSearchAvatar = view.findViewById<android.view.View>(R.id.btnSearchAvatar)

        val sessionManager = SessionManager(requireContext())
        val isOnline = sessionManager.isOnline()
        val username = sessionManager.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        
        val factory = EditProfileViewModelFactory(
            requireActivity().application,
            database.profileDao(),
            database.treeDao(),
            database.imageDao(),
            RetrofitClient.treeApiService
        )
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[EditProfileViewModel::class.java]

        val profileId = arguments?.getLong("profileId")?.toInt() ?: -1

        if (profileId != -1) {
            viewModel.loadProfile(profileId)
        }

        // ================= AVATAR SELECTOR =================
        var currentSelectedAvatarUrl: String? = null
        val avatars = listOf(
            view.findViewById<ImageView>(R.id.edit_avatar_blue) to "#2196F3",
            view.findViewById<ImageView>(R.id.edit_avatar_pink) to "#E91E63",
            view.findViewById<ImageView>(R.id.edit_avatar_green) to "#4CAF50",
            view.findViewById<ImageView>(R.id.edit_avatar_orange) to "#FF9800"
        )

        fun updateAvatarSelectionUI(selectedUrl: String?) {
            avatars.forEach { (iv, color) ->
                val isSelected = selectedUrl == "color:$color"
                iv.alpha = if (isSelected) 1.0f else 0.4f
                iv.setBackgroundResource(if (isSelected) android.R.drawable.editbox_dropdown_light_frame else 0)
            }
        }

        avatars.forEach { (iv, color) ->
            iv.setOnClickListener {
                currentSelectedAvatarUrl = "color:$color"
                updateAvatarSelectionUI(currentSelectedAvatarUrl)
            }
        }

        btnSearchAvatar.setOnClickListener {
            val dialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            dialog.onPictoSelected = { searchResult ->
                currentSelectedAvatarUrl = searchResult.imageUrl
                updateAvatarSelectionUI(currentSelectedAvatarUrl)
            }
            dialog.show(parentFragmentManager, "AvatarSearch")
        }

        btnSaveProfile.setOnClickListener {
            val newName = editProfileName.text?.toString()?.trim() ?: ""
            if (newName.isNotEmpty() && profileId != -1) {
                viewModel.updateProfile(profileId, newName, currentSelectedAvatarUrl)
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
            }
        }

        // ================= ADAPTER SETUP =================
        var itemTouchHelper: ItemTouchHelper? = null

        val adapter = ProfileTreeAdapter(
            onTreeDelete = { tree ->
                viewModel.deleteTree(tree)
            },
            onOrderChanged = { newTrees ->
                if (profileId != -1) {
                    viewModel.updateTreesOrder(profileId, newTrees)
                }
            },
            onViewTree = { tree ->
                val intent = android.content.Intent(requireContext(), org.libera.pictotree.ui.visualizer.TreeVisualizerActivity::class.java)
                intent.putExtra("TREE_ID", tree.id)
                startActivity(intent)
            },
            onStartDrag = { viewHolder: RecyclerView.ViewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }
        )
        
        adapter.isOnlineMode = isOnline
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // ================= DRAG & DROP ENGINE =================
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

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
                adapter.dispatchUpdates()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // ================= FAB DIALOG LAUNCHER =================
        fabAddTree.visibility = if (isOnline) View.VISIBLE else View.GONE
        fabAddTree.setOnClickListener {
            viewModel.openTreeSelection()
        }

        // ================= VIEWMODEL OBSERVER =================
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                launch {
                    viewModel.showTreeSelectionEvent.collect {
                        val dialog = TreeSelectionDialogFragment(
                            remoteTreesFlow = viewModel.remoteTrees,
                            onSearchRequested = { query, isPublic ->
                                viewModel.searchTrees(query, isPublic)
                            },
                            onLoadMoreRequested = {
                                viewModel.loadMoreTrees()
                            },
                            onTreeSelected = { selectedTree ->
                                val username = sessionManager.getUsername()
                                if (username != null && profileId != -1) {
                                    viewModel.synchronizeAndImportTree(
                                        treeId = selectedTree.id,
                                        profileId = profileId,
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
                                currentSelectedAvatarUrl = state.profile.avatarUrl
                                updateAvatarSelectionUI(currentSelectedAvatarUrl)
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
