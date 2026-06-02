package org.libera.pictotree.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.repository.ProfileRepository
import org.libera.pictotree.data.repository.UserConfigRepository
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.MainActivity
import org.libera.pictotree.data.repository.AuthRepository
import org.libera.pictotree.network.RetrofitClient

class DashboardFragment : Fragment() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var adapter: ProfileAdapter
    
    private lateinit var tvTitle: TextView
    private lateinit var rvProfiles: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var layoutAdminActions: View
    private lateinit var btnCreateProfile: MaterialButton
    private lateinit var btnImportProfile: MaterialButton
    private lateinit var btnGlobalMenu: MaterialButton
    private lateinit var cardRotate: View
    private lateinit var ivAdminStatus: ImageView
    private lateinit var ivLogout: ImageView

    private var lastAppliedGlobalOrientation: String? = null
    private var isRestoredFromRotation = false

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        isRestoredFromRotation = savedInstanceState != null
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle = view.findViewById(R.id.tvTitle)
        rvProfiles = view.findViewById(R.id.rvProfiles)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        layoutAdminActions = view.findViewById(R.id.layout_admin_actions)
        btnCreateProfile = view.findViewById(R.id.btnCreateProfile)
        btnImportProfile = view.findViewById(R.id.btnImportProfile)
        btnGlobalMenu = view.findViewById(R.id.btnGlobalMenu)
        cardRotate = view.findViewById(R.id.card_rotate)
        ivAdminStatus = view.findViewById(R.id.ivAdminStatus)
        ivLogout = view.findViewById(R.id.ivLogout)

        val titleText = tvTitle.text.toString()
        val spannableTitle = SpannableString(titleText)
        val colors = listOf(
            R.color.brand_pink,
            R.color.brand_orange,
            R.color.brand_yellow,
            R.color.brand_green,
            R.color.brand_blue,
            R.color.brand_indigo,
            R.color.brand_red,
            R.color.brand_pink,
            R.color.brand_orange,
            R.color.white,
            R.color.brand_blue,
            R.color.brand_indigo
        )

        for (i in titleText.indices) {
            val colorRes = colors[i % colors.size]
            val color = ContextCompat.getColor(requireContext(), colorRes)
            spannableTitle.setSpan(
                ForegroundColorSpan(color),
                i,
                i + 1,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvTitle.text = spannableTitle

        val sessionManager = SessionManager(requireContext())
        val isOnline = sessionManager.isOnline()
        val username = sessionManager.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        
        val profileRepository = ProfileRepository(requireContext(), database.profileDao(), database.treeDao(), database.imageDao(), username)
        val userConfigRepository = UserConfigRepository(database.userConfigDao())
        val authRepository = AuthRepository(RetrofitClient.apiService)
        
        val factory = DashboardViewModelFactory(requireActivity().application, profileRepository, userConfigRepository, database.treeDao(), database.imageDao(), RetrofitClient.treeApiService, authRepository)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
        
        if (isOnline) viewModel.setAdminMode(true)

        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean {
                return false
            }
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (!adapter.isAdminMode) return false
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (adapter.isAdminMode) {
                    adapter.dispatchUpdates()
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(rvProfiles)

        adapter = ProfileAdapter(
            onProfileClick = { profile -> viewModel.playProfile(profile.id) },
            onEditClick = { profile ->
                val bundle = Bundle().apply { putInt("profileId", profile.id) }
                findNavController().navigate(R.id.action_dashboardFragment_to_editProfileFragment, bundle)
            },
            onOrderChanged = { newList -> viewModel.updateProfilesOrder(newList) },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )
        rvProfiles.layoutManager = LinearLayoutManager(requireContext())
        rvProfiles.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.navigateToProfileEvent.collect { profileId ->
                        val bundle = Bundle().apply { putInt("profileId", profileId) }
                        findNavController().navigate(R.id.action_dashboardFragment_to_editProfileFragment, bundle)
                    }
                }

                launch {
                    viewModel.playProfileEvent.collect { profileId ->
                        val bundle = Bundle().apply { putInt("profileId", profileId) }
                        findNavController().navigate(R.id.action_dashboardFragment_to_treeSelectionFragment, bundle)
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is DashboardUiState.Loading -> { progressBar.visibility = View.VISIBLE; tvEmptyState.visibility = View.GONE; rvProfiles.visibility = View.GONE }
                            is DashboardUiState.Empty -> { progressBar.visibility = View.GONE; tvEmptyState.visibility = View.VISIBLE; rvProfiles.visibility = View.GONE }
                            is DashboardUiState.Success -> { progressBar.visibility = View.GONE; tvEmptyState.visibility = View.GONE; rvProfiles.visibility = View.VISIBLE; adapter.submitList(state.profiles) }
                        }
                    }
                }

                launch {
                    viewModel.isAdminMode.collect { isAdmin ->
                        adapter.isAdminMode = isAdmin
                        layoutAdminActions.visibility = if (isAdmin) View.VISIBLE else View.GONE
                        btnGlobalMenu.visibility = if (isAdmin) View.VISIBLE else View.GONE
                        ivAdminStatus.setImageResource(if (isAdmin) android.R.drawable.ic_partial_secure else android.R.drawable.ic_secure)
                    }
                }

                launch { viewModel.isImporting.collect { importing -> progressBar.visibility = if (importing) View.VISIBLE else View.GONE } }
                
                // OBSERVATION DES RÉSULTATS DE SYNCHRONISATION
                launch {
                    viewModel.syncResultEvent.collect { result ->
                        if (result.errors > 0) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Importation partielle")
                                .setMessage("Le profil a été importé, mais ${result.errors} image(s) n'ont pas pu être téléchargées. Vous pourrez les réparer plus tard dans l'édition du profil.")
                                .setPositiveButton("OK", null)
                                .show()
                        } else {
                            Toast.makeText(requireContext(), "Profil importé avec succès !", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    viewModel.userConfig.collect { config ->
                        if (config != null) {
                            cardRotate.visibility = if (config.enableRotationButton) View.VISIBLE else View.GONE
                            val globalSetting = config.defaultOrientation
                            if ((lastAppliedGlobalOrientation == null && !isRestoredFromRotation) || 
                                (lastAppliedGlobalOrientation != null && lastAppliedGlobalOrientation != globalSetting)) {
                                lastAppliedGlobalOrientation = globalSetting
                                val orientationInt = if (globalSetting == "LANDSCAPE") android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                sessionManager.setPreferredOrientation(username, orientationInt)
                                (requireActivity() as? MainActivity)?.applyUserOrientation()
                            }
                            if (lastAppliedGlobalOrientation == null && isRestoredFromRotation) lastAppliedGlobalOrientation = globalSetting
                        }
                    }
                }
            }
        }

        btnCreateProfile.setOnClickListener { viewModel.createQuickProfile() }
        btnImportProfile.setOnClickListener { viewModel.fetchRemoteProfiles(); showImportProfileDialog() }
        btnGlobalMenu.setOnClickListener { showGlobalSettingsDialog() }
        cardRotate.setOnClickListener { (requireActivity() as? MainActivity)?.toggleOrientation() }
        
        ivLogout.setOnClickListener {
            sessionManager.logout()
            findNavController().navigate(R.id.action_dashboardFragment_to_loginFragment)
        }

        ivAdminStatus.setOnClickListener { 
            if (viewModel.isAdminMode.value) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_lock_admin_title)
                    .setMessage(R.string.dialog_lock_admin_message)
                    .setPositiveButton(R.string.dialog_lock_admin_positive) { _, _ ->
                        viewModel.setAdminMode(false)
                        Toast.makeText(requireContext(), getString(R.string.dialog_lock_admin_toast), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.dialog_lock_admin_negative, null)
                    .show()
            } else {
                showUnlockLoginDialog()
            }
        }
    }

    private fun showImportProfileDialog() {
        val dialog = ImportProfileDialogFragment(
            remoteProfilesFlow = viewModel.remoteProfiles,
            onImportClick = { remoteProfile -> viewModel.importRemoteProfile(remoteProfile) }
        )
        dialog.show(childFragmentManager, "ImportProfileDialog")
    }

    private fun showGlobalSettingsDialog() {
        val dialog = GlobalSettingsDialogFragment()
        dialog.show(childFragmentManager, "GlobalSettingsDialog")
    }

    private fun showUnlockLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unlock_login, null)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.et_password)
        val tvUser = dialogView.findViewById<TextView>(R.id.tv_unlock_user)
        val sessionManager = SessionManager(requireContext())
        val username = sessionManager.getUsername() ?: "default"
        tvUser.text = getString(R.string.dialog_unlock_admin_user_prefix, username)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_unlock_admin_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_unlock_admin_positive) { _, _ ->
                val password = etPassword.text?.toString() ?: ""
                viewLifecycleOwner.lifecycleScope.launch {
                    progressBar.visibility = View.VISIBLE
                    val result = viewModel.tryUnlock(password)
                    progressBar.visibility = View.GONE
                    if (result.isSuccess) {
                        Toast.makeText(requireContext(), getString(R.string.dialog_unlock_admin_toast_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.dialog_unlock_admin_toast_error, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_lock_admin_negative, null)
            .show()
    }
}
