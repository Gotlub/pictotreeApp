package org.libera.pictotree.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

    // Suivi de la dernière orientation globale appliquée pour éviter le "snap-back"
    // On l'initialise avec une valeur qui sera écrasée au premier émission du Flow
    private var lastAppliedGlobalOrientation: String? = null
    
    // Flag pour savoir si on vient de restaurer l'état après rotation
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

        adapter = ProfileAdapter(
                onProfileClick = { profile -> viewModel.playProfile(profile.id) },
                onEditClick = { profile ->
                    val bundle = Bundle().apply { putInt("profileId", profile.id) }
                    findNavController().navigate(R.id.action_dashboardFragment_to_editProfileFragment, bundle)
                }
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
                
                launch {
                    viewModel.userConfig.collect { config ->
                        if (config != null) {
                            cardRotate.visibility = if (config.enableRotationButton) View.VISIBLE else View.GONE
                            
                            // LOGIQUE ANTI-SNAPBACK AMÉLIORÉE
                            val globalSetting = config.defaultOrientation
                            
                            // On applique le réglage global SI :
                            // 1. C'est la toute première fois qu'on charge (lastAppliedGlobalOrientation == null) ET qu'on n'est pas après une rotation (isRestoredFromRotation == false)
                            // 2. OU si le réglage global dans la BDD a physiquement changé depuis la dernière fois qu'on l'a appliqué
                            if ((lastAppliedGlobalOrientation == null && !isRestoredFromRotation) || 
                                (lastAppliedGlobalOrientation != null && lastAppliedGlobalOrientation != globalSetting)) {
                                
                                lastAppliedGlobalOrientation = globalSetting
                                val orientationInt = if (globalSetting == "LANDSCAPE") {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                } else {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                                sessionManager.setPreferredOrientation(username, orientationInt)
                                (requireActivity() as? MainActivity)?.applyUserOrientation()
                            }
                            
                            // Si on vient de restaurer après rotation, on marque lastAppliedGlobalOrientation 
                            // pour ne pas écraser l'override manuel au prochain changement de config mineur
                            if (lastAppliedGlobalOrientation == null && isRestoredFromRotation) {
                                lastAppliedGlobalOrientation = globalSetting
                            }
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
            sessionManager.clearSession()
            requireContext().getSharedPreferences("pictotree_session", android.content.Context.MODE_PRIVATE).edit().remove("USERNAME").apply()
            findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
        }

        ivAdminStatus.setOnClickListener { 
            if (viewModel.isAdminMode.value) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Verrouiller l'édition ?")
                    .setMessage("Le mode administrateur sera désactivé. Vous devrez vous reconnecter pour modifier les profils.")
                    .setPositiveButton("Verrouiller") { _, _ ->
                        viewModel.setAdminMode(false)
                        Toast.makeText(requireContext(), "Édition verrouillée", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annuler", null)
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
        tvUser.text = "Utilisateur : $username"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Déverrouiller l'édition")
            .setView(dialogView)
            .setPositiveButton("Déverrouiller") { _, _ ->
                val password = etPassword.text?.toString() ?: ""
                viewLifecycleOwner.lifecycleScope.launch {
                    progressBar.visibility = View.VISIBLE
                    val result = viewModel.tryUnlock(password)
                    progressBar.visibility = View.GONE
                    
                    if (result.isSuccess) {
                        Toast.makeText(requireContext(), "Accès autorisé", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Échec : ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
