package org.libera.pictotree.ui.editprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import coil.load
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.data.SessionManager

class EditProfileFragment : Fragment() {

    private lateinit var viewModel: EditProfileViewModel
    private var currentSelectedAvatarUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editProfileName = view.findViewById<TextInputEditText>(R.id.editProfileName)
        val ivAvatarPreview = view.findViewById<ImageView>(R.id.ivAvatarPreview)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewProfileTrees)
        val fabAddTree = view.findViewById<FloatingActionButton>(R.id.fabAddTree)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBarSync)
        val btnSearchAvatar = view.findViewById<android.view.View>(R.id.btnSearchAvatar)

        val sessionManager = SessionManager(requireContext())
        val isOnline = sessionManager.isOnline()
        val username = sessionManager.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)

        // Function to load and display avatar
        fun loadAvatar(url: String?) {
            when {
                url.isNullOrEmpty() -> {
                    ivAvatarPreview.setImageResource(android.R.drawable.ic_menu_myplaces)
                    ivAvatarPreview.colorFilter = null
                }
                url.startsWith("color:") -> {
                    val colorHex = url.removePrefix("color:")
                    ivAvatarPreview.setImageResource(android.R.drawable.presence_online)
                    try {
                        ivAvatarPreview.setColorFilter(android.graphics.Color.parseColor(colorHex))
                    } catch (e: Exception) {
                        ivAvatarPreview.colorFilter = null
                    }
                }
                url.startsWith("file://") -> {
                    ivAvatarPreview.colorFilter = null
                    ivAvatarPreview.load(java.io.File(url.removePrefix("file://"))) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_myplaces)
                    }
                }
                else -> {
                    ivAvatarPreview.colorFilter = null
                    ivAvatarPreview.load(url) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_myplaces)
                    }
                }
            }
        }
        
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

        // ================= AUTO-SAVE ON EXIT =================
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val newName = editProfileName.text?.toString()?.trim() ?: ""
                if (newName.isNotEmpty() && profileId != -1) {
                    viewModel.updateProfile(profileId, newName, currentSelectedAvatarUrl)
                }
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        // ================= AVATAR SELECTOR =================
        btnSearchAvatar.setOnClickListener {
            val dialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            dialog.onPictoSelected = { searchResult ->
                currentSelectedAvatarUrl = searchResult.imageUrl
                loadAvatar(currentSelectedAvatarUrl)
            }
            dialog.show(parentFragmentManager, "AvatarSearch")
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
            onColorClick = { tree, currentColor ->
                showColorPicker(profileId, tree.id, currentColor)
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
                                if (editProfileName.text.isNullOrEmpty()) {
                                    editProfileName.setText(state.profile.name)
                                }
                                currentSelectedAvatarUrl = state.profile.avatarUrl
                                loadAvatar(currentSelectedAvatarUrl)
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

    private fun showColorPicker(profileId: Int, treeId: Int, currentColor: String) {
        val caaColors = linkedMapOf(
            "Black (Default)" to "#000000",
            "Yellow (People)" to "#FFD54F",
            "Green (Verbs)" to "#81C784",
            "Orange (Noms)" to "#FFB74D",
            "Blue (Adjectives)" to "#64B5F6",
            "Pink (Social)" to "#F06292"
        )
        
        val names = caaColors.keys.toTypedArray()
        val codes = caaColors.values.toTypedArray()
        var selectedIdx = codes.indexOf(currentColor)
        if (selectedIdx == -1) selectedIdx = 0

        val adapter = object : android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.select_dialog_item, names) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                val colorCode = codes[position]
                
                // Add colored circle icon
                val size = (24 * context.resources.displayMetrics.density).toInt()
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(colorCode))
                    setSize(size, size)
                }
                view.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
                view.compoundDrawablePadding = (16 * context.resources.displayMetrics.density).toInt()
                return view
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Fitzgerald Key (CAA)")
            .setAdapter(adapter) { dialog, which ->
                viewModel.updateTreeColor(profileId, treeId, codes[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
