package org.libera.pictotree.ui.editprofile

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.repository.ProfileRepository
import org.libera.pictotree.data.repository.UserConfigRepository
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.ui.explorer.TreeGlobalMapDialog
import java.io.File

class EditProfileFragment : Fragment() {

    private lateinit var viewModel: EditProfileViewModel
    private lateinit var adapter: ProfileTreeAdapter
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var editProfileName: TextInputEditText
    private lateinit var ivAvatarPreview: ImageView
    private lateinit var btnSearchAvatar: View
    private lateinit var btnOpenOptions: View
    private lateinit var fabAddTree: View
    private lateinit var progressBarSync: ProgressBar
    
    private var profileId: Int = -1
    private var currentSelectedAvatarUrl: String? = null
    private var isSaving = false
    private var hasInitializedUI = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        profileId = arguments?.getInt("profileId") ?: -1
        
        setupUIReferences(view)
        setupViewModel()
        setupAdapter()
        setupListeners(view)
        setupObservers()

        if (profileId != -1) {
            viewModel.loadProfile(profileId)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndGoBack()
            }
        })

        view.findViewById<View>(R.id.btnBackToDashboard).setOnClickListener {
            saveAndGoBack()
        }
    }

    private fun setupUIReferences(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewProfileTrees)
        editProfileName = view.findViewById(R.id.editProfileName)
        ivAvatarPreview = view.findViewById(R.id.ivAvatarPreview)
        btnSearchAvatar = view.findViewById(R.id.btnSearchAvatar)
        btnOpenOptions = view.findViewById(R.id.btnOpenOptions)
        fabAddTree = view.findViewById(R.id.fabAddTree)
        progressBarSync = view.findViewById(R.id.progressBarSync)
    }

    private fun setupViewModel() {
        val sessionManager = SessionManager(requireContext())
        val username = sessionManager.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        
        val profileRepository = ProfileRepository(
            requireContext(),
            database.profileDao(),
            database.treeDao(),
            database.imageDao(),
            username
        )
        
        val factory = EditProfileViewModelFactory(
            requireActivity().application,
            profileRepository,
            database.profileDao(),
            database.treeDao(),
            database.imageDao(),
            RetrofitClient.treeApiService
        )
        viewModel = ViewModelProvider(this, factory)[EditProfileViewModel::class.java]
    }

    private fun setupAdapter() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun isLongPressDragEnabled(): Boolean {
                return false
            }
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                adapter.dispatchUpdates()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        adapter = ProfileTreeAdapter(
            onTreeDelete = { tree -> viewModel.deleteTreeFromProfile(profileId, tree.id) },
            onOrderChanged = { newList -> viewModel.updateTreesOrder(profileId, newList) },
            onViewTree = { tree ->
                // RESTAURATION DE LA VUE SIMPLE TREANT.JS (DIALOGUE)
                val username = SessionManager(requireContext()).getUsername() ?: "default"
                val dialog = TreeGlobalMapDialog.newInstance(
                    intArrayOf(tree.id),
                    tree.id,
                    username,
                    "",
                    isSimplePreview = true // Nouveau mode
                )
                dialog.show(childFragmentManager, "TreePreview")
            },
            onColorClick = { tree, currentColor -> showColorPickerDialog(tree, currentColor) },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) },
            onRepairTree = { tree -> 
                val username = SessionManager(requireContext()).getUsername() ?: "default"
                viewModel.repairTree(tree.id, username)
                Toast.makeText(requireContext(), "Réparation de l'arbre en cours...", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupListeners(view: View) {
        btnOpenOptions.setOnClickListener {
            if (profileId != -1) {
                val dialog = ProfileOptionsDialogFragment.newInstance(profileId)
                dialog.show(childFragmentManager, "ProfileOptions")
            }
        }

        fabAddTree.setOnClickListener {
            // Déclencher une recherche vide immédiate pour charger tous les arbres
            viewModel.searchTrees("")
            
            val dialog = TreeSelectionDialogFragment(
                remoteTreesFlow = viewModel.remoteTrees,
                onSearchRequested = { query -> viewModel.searchTrees(query) },
                onLoadMoreRequested = { viewModel.loadMoreTrees() },
                onTreeSelected = { treeMetadata ->
                    val username = SessionManager(requireContext()).getUsername() ?: "default"
                    viewModel.synchronizeAndImportTree(treeMetadata.id, profileId, username)
                    Toast.makeText(requireContext(), "Importation de l'arbre...", Toast.LENGTH_SHORT).show()
                }
            )
            dialog.show(childFragmentManager, "TreeSelection")
        }

        btnSearchAvatar.setOnClickListener {
            val dialog = org.libera.pictotree.ui.common.PictoSearchDialog()
            dialog.onPictoSelected = { result ->
                val displayUrl = result.thumbnailUrl ?: result.imageUrl ?: ""
                val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
                
                var finalUrl = if (displayUrl.startsWith("http") || displayUrl.startsWith("file") || displayUrl.startsWith("color:")) {
                    displayUrl
                } else if (displayUrl.isNotBlank()) {
                    "${hostUrl.removeSuffix("/")}/${displayUrl.removePrefix("/")}"
                } else {
                    ""
                }
                
                if (finalUrl.isNotBlank()) {
                    finalUrl = org.libera.pictotree.utils.FileUtils.normalizeUrl(finalUrl, hostUrl)
                    currentSelectedAvatarUrl = finalUrl
                    ivAvatarPreview.clearColorFilter()
                    val imageLoader = org.libera.pictotree.network.RetrofitClient.getImageLoader(requireContext())
                    ivAvatarPreview.load(finalUrl, imageLoader) {
                        crossfade(true)
                        placeholder(R.drawable.ic_launcher_foreground)
                        error(R.drawable.ic_launcher_foreground)
                    }
                }
            }
            dialog.show(childFragmentManager, "SearchAvatar")
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is EditProfileUiState.Success -> {
                                if (!hasInitializedUI) {
                                    editProfileName.setText(state.profile.name)
                                    currentSelectedAvatarUrl = state.profile.remoteAvatarUrl
                                    val avatar = state.profile.avatarUrl
                                    if (!avatar.isNullOrEmpty()) {
                                        if (avatar.startsWith("color:")) {
                                            val colorStr = avatar.substringAfter("color:")
                                            try {
                                                ivAvatarPreview.setImageResource(android.R.drawable.presence_online)
                                                ivAvatarPreview.setColorFilter(android.graphics.Color.parseColor(colorStr))
                                            } catch (e: Exception) {
                                                ivAvatarPreview.setImageResource(R.drawable.ic_launcher_foreground)
                                                ivAvatarPreview.clearColorFilter()
                                            }
                                        } else {
                                            ivAvatarPreview.clearColorFilter()
                                            val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
                                            val normalizedAvatar = org.libera.pictotree.utils.FileUtils.normalizeUrl(avatar, hostUrl)
                                            val imageLoader = org.libera.pictotree.network.RetrofitClient.getImageLoader(requireContext())
                                            ivAvatarPreview.load(normalizedAvatar, imageLoader) {
                                                crossfade(true)
                                                placeholder(R.drawable.ic_launcher_foreground)
                                                error(R.drawable.ic_launcher_foreground)
                                            }
                                        }
                                    } else {
                                        ivAvatarPreview.setImageResource(R.drawable.ic_launcher_foreground)
                                        ivAvatarPreview.clearColorFilter()
                                    }
                                    hasInitializedUI = true
                                }
                                adapter.submitList(state.trees)
                                adapter.isOnlineMode = SessionManager(requireContext()).isOnline()
                            }
                            is EditProfileUiState.Error -> {
                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            }
                            else -> {}
                        }
                    }
                }
                
                launch {
                    viewModel.syncResultEvent.collect { result ->
                        if (result.errors > 0) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Importation incomplète")
                                .setMessage("Il manque ${result.errors} image(s) sur un total de ${result.total}. Voulez-vous réessayer ?")
                                .setPositiveButton("Réessayer") { _, _ -> }
                                .setNegativeButton("Plus tard", null)
                                .show()
                        } else if (result.total > 0) {
                            Toast.makeText(requireContext(), "Synchronisation réussie (${result.total} images)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    viewModel.saveCompletedEvent.collect {
                        isSaving = false
                        progressBarSync.visibility = View.GONE
                        if (isAdded) {
                            NavHostFragment.findNavController(this@EditProfileFragment).popBackStack()
                        }
                    }
                }
            }
        }
    }

    private fun showColorPickerDialog(tree: org.libera.pictotree.data.database.entity.TreeEntity, currentColor: String) {
        val colors = arrayOf("#000000", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#808080")
        val colorNames = arrayOf("Noir", "Rouge", "Vert", "Bleu", "Jaune", "Magenta", "Cyan", "Gris")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Couleur CAA")
            .setItems(colorNames) { _, which ->
                viewModel.updateTreeColor(profileId, tree.id, colors[which])
            }
            .show()
    }

    private fun saveAndGoBack() {
        if (isSaving) return
        val newName = editProfileName.text?.toString()?.trim() ?: ""
        if (newName.isNotEmpty() && profileId != -1) {
            isSaving = true
            progressBarSync.visibility = View.VISIBLE
            viewModel.saveProfile(profileId, newName, currentSelectedAvatarUrl)
        } else {
            NavHostFragment.findNavController(this).popBackStack()
        }
    }
}
