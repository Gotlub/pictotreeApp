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

class DashboardFragment : Fragment() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var adapter: ProfileAdapter
    
    private lateinit var rvProfiles: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var layoutAdminActions: View
    private lateinit var btnCreateProfile: MaterialButton
    private lateinit var btnImportProfile: MaterialButton
    private lateinit var ivAdminStatus: ImageView
    private lateinit var ivLogout: ImageView
    private lateinit var cardUserSettings: View
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var btnSetPin: View

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup UI References
        rvProfiles = view.findViewById(R.id.rvProfiles)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        layoutAdminActions = view.findViewById(R.id.layout_admin_actions)
        btnCreateProfile = view.findViewById(R.id.btnCreateProfile)
        btnImportProfile = view.findViewById(R.id.btnImportProfile)
        ivAdminStatus = view.findViewById(R.id.ivAdminStatus)
        ivLogout = view.findViewById(R.id.ivLogout)
        cardUserSettings = view.findViewById(R.id.cardUserSettings)
        tvCurrentLanguage = view.findViewById(R.id.tvCurrentLanguage)
        btnSetPin = view.findViewById(R.id.btnSetPin)

        // Setup Logic
        val sessionManager = SessionManager(requireContext())
        val isOnline = sessionManager.isOnline()
        val username = sessionManager.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        
        val profileRepository = ProfileRepository(database.profileDao())
        val userConfigRepository = UserConfigRepository(database.userConfigDao())
        val treeDao = database.treeDao()
        val imageDao = database.imageDao()
        val treeApiService = org.libera.pictotree.network.RetrofitClient.treeApiService

        val factory = DashboardViewModelFactory(
            requireActivity().application, 
            profileRepository, 
            userConfigRepository,
            treeDao,
            imageDao,
            treeApiService
        )
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
        
        if (isOnline) {
            viewModel.setAdminMode(true)
        }

        adapter = ProfileAdapter(
                onProfileClick = { profile -> viewModel.playProfile(profile.id) },
                onEditClick = { profile ->
                    val bundle = Bundle().apply { putLong("profileId", profile.id.toLong()) }
                    findNavController().navigate(R.id.action_dashboardFragment_to_editProfileFragment, bundle)
                }
        )
        rvProfiles.layoutManager = LinearLayoutManager(requireContext())
        rvProfiles.adapter = adapter

        // Setup Observers
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.navigateToProfileEvent.collect { profileId ->
                        val bundle = Bundle().apply { putLong("profileId", profileId) }
                        findNavController().navigate(R.id.action_dashboardFragment_to_editProfileFragment, bundle)
                    }
                }

                launch {
                    viewModel.playProfileEvent.collect { profileId ->
                        val bundle = Bundle().apply { 
                            putInt("profileId", profileId)
                        }
                        findNavController().navigate(R.id.action_dashboardFragment_to_treeSelectionFragment, bundle)
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is DashboardUiState.Loading -> {
                                progressBar.visibility = View.VISIBLE
                                tvEmptyState.visibility = View.GONE
                                rvProfiles.visibility = View.GONE
                            }
                            is DashboardUiState.Empty -> {
                                progressBar.visibility = View.GONE
                                tvEmptyState.visibility = View.VISIBLE
                                rvProfiles.visibility = View.GONE
                            }
                            is DashboardUiState.Success -> {
                                progressBar.visibility = View.GONE
                                tvEmptyState.visibility = View.GONE
                                rvProfiles.visibility = View.VISIBLE
                                adapter.submitList(state.profiles)
                            }
                        }
                    }
                }

                launch {
                    viewModel.isAdminMode.collect { isAdmin ->
                        adapter.isAdminMode = isAdmin
                        layoutAdminActions.visibility = if (isAdmin && isOnline) View.VISIBLE else View.GONE
                        if (isAdmin) {
                            cardUserSettings.visibility = View.VISIBLE
                            ivAdminStatus.setImageResource(android.R.drawable.ic_partial_secure)
                        } else {
                            cardUserSettings.visibility = View.GONE
                            ivAdminStatus.setImageResource(android.R.drawable.ic_secure)
                        }
                    }
                }

                launch {
                    viewModel.userConfig.collect { config ->
                        config?.let { tvCurrentLanguage.text = it.locale.uppercase() }
                    }
                }

                launch {
                    viewModel.isImporting.collect { importing ->
                        progressBar.visibility = if (importing) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        // Setup Listeners
        btnCreateProfile.setOnClickListener { viewModel.createQuickProfile() }
        btnImportProfile.setOnClickListener { 
            viewModel.fetchRemoteProfiles()
            showImportProfileDialog()
        }
        
        tvCurrentLanguage.setOnClickListener { showLanguageDialog() }
        btnSetPin.setOnClickListener { showSetPinDialog() }
        
        ivLogout.setOnClickListener {
            sessionManager.clearSession()
            findNavController().navigate(R.id.action_dashboardFragment_to_loginFragment)
        }

        ivAdminStatus.setOnClickListener { 
            if (isOnline) {
                Toast.makeText(requireContext(), getString(R.string.dashboard_admin_online_toast), Toast.LENGTH_SHORT).show()
            } else {
                if (viewModel.isAdminMode.value) {
                    viewModel.setAdminMode(false)
                } else if (viewModel.userConfig.value?.offlineSettingsPin != null) {
                    showUnlockPinDialog()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.dashboard_offline_pin_security_toast), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showImportProfileDialog() {
        val dialog = ImportProfileDialogFragment(
            remoteProfilesFlow = viewModel.remoteProfiles,
            onImportClick = { remoteProfile ->
                viewModel.importRemoteProfile(remoteProfile)
            }
        )
        dialog.show(childFragmentManager, "ImportProfileDialog")
    }

    private fun showUnlockPinDialog() {
        val input = TextInputEditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val container = TextInputLayout(requireContext())
        container.setPadding(40, 0, 40, 0)
        container.addView(input)
        container.hint = getString(R.string.dashboard_pin_btn)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_unlock_title)
            .setMessage(R.string.dialog_unlock_message)
            .setView(container)
            .setPositiveButton(R.string.dialog_unlock_validate) { _, _ ->
                val pin = input.text?.toString()
                if (viewModel.verifyPin(pin ?: "")) {
                    viewModel.setAdminMode(true)
                    Toast.makeText(requireContext(), getString(R.string.dashboard_unlocked_toast), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.dashboard_wrong_pin_toast), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_create_profile_btn_cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("Français", "English", "Español", "Deutsch", "Italiano", "Nederlands", "Polski")
        val codes = arrayOf("fr", "en", "es", "de", "it", "nl", "pl")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_lang_title)
            .setItems(languages) { _, which -> viewModel.setLanguage(codes[which]) }
            .show()
    }

    private fun showSetPinDialog() {
        val input = TextInputEditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val container = TextInputLayout(requireContext())
        container.setPadding(40, 0, 40, 0)
        container.addView(input)
        container.hint = getString(R.string.dialog_pin_hint)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_pin_title)
            .setMessage(R.string.dialog_pin_message)
            .setView(container)
            .setPositiveButton(R.string.dialog_pin_save) { _, _ ->
                val pin = input.text?.toString()
                if (pin?.length == 4) {
                    viewModel.setPin(pin)
                    Toast.makeText(requireContext(), getString(R.string.dialog_pin_saved_toast), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.dialog_pin_error_length), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_create_profile_btn_cancel, null)
            .show()
    }
}
