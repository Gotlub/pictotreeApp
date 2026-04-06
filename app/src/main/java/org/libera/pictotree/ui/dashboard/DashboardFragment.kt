package org.libera.pictotree.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
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
    private lateinit var fabAddProfile: ExtendedFloatingActionButton
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
        fabAddProfile = view.findViewById(R.id.fabAddProfile)
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
        
        val factory = DashboardViewModelFactory(profileRepository, userConfigRepository)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
        
        // Sécurité : Si on est en ligne, on est ADMIN par défaut (non verrouillable)
        if (isOnline) {
            viewModel.setAdminMode(true)
        }

        adapter =
                ProfileAdapter(
                        onProfileClick = { profile ->
                            viewModel.playProfile(profile.id)
                        },
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
                    viewModel.playProfileEvent.collect { profileTreeData ->
                        val bundle = Bundle().apply { 
                            putInt("treeId", profileTreeData.first)
                            putInt("profileId", profileTreeData.second)
                        }
                        findNavController().navigate(R.id.action_dashboardFragment_to_treeExplorerFragment, bundle)
                    }
                }

                // Observer for UI State
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

                // Observer for Safe/Admin Mode State
                launch {
                    viewModel.isAdminMode.collect { isAdmin ->
                        adapter.isAdminMode = isAdmin

                        // Restriction : Le bouton "New Profile" n'est accessible qu'en ONLINE + ADMIN
                        fabAddProfile.visibility = if (isAdmin && isOnline) View.VISIBLE else View.GONE

                        if (isAdmin) {
                            cardUserSettings.visibility = View.VISIBLE
                            ivAdminStatus.setImageResource(android.R.drawable.ic_partial_secure)
                        } else {
                            cardUserSettings.visibility = View.GONE
                            ivAdminStatus.setImageResource(android.R.drawable.ic_secure)
                        }
                    }
                }

                // Observer for User Config (Language)
                launch {
                    viewModel.userConfig.collect { config ->
                        config?.let {
                            tvCurrentLanguage.text = it.locale.uppercase()
                        }
                    }
                }
            }
        }

        // Setup Listeners
        fabAddProfile.setOnClickListener { showCreateProfileDialog() }
        tvCurrentLanguage.setOnClickListener { showLanguageDialog() }
        btnSetPin.setOnClickListener { showSetPinDialog() }
        
        ivLogout.setOnClickListener {
            sessionManager.clearSession()
            findNavController().navigate(R.id.action_dashboardFragment_to_loginFragment)
        }

        // Listener sur le cadenas pour le Mode Admin
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
            .setItems(languages) { _, which ->
                viewModel.setLanguage(codes[which])
            }
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

    private fun showCreateProfileDialog() {
        val dialogView =
                LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_profile, null)
        val tilProfileName = dialogView.findViewById<TextInputLayout>(R.id.tilProfileName)
        val etProfileName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)
        
        var selectedAvatarUrl: String? = null
        val avatars = listOf(
            dialogView.findViewById<ImageView>(R.id.avatar_blue) to "#2196F3",
            dialogView.findViewById<ImageView>(R.id.avatar_pink) to "#E91E63",
            dialogView.findViewById<ImageView>(R.id.avatar_green) to "#4CAF50",
            dialogView.findViewById<ImageView>(R.id.avatar_orange) to "#FF9800"
        )

        avatars.forEach { (view, color) ->
            view.setOnClickListener {
                avatars.forEach { 
                    it.first.alpha = 0.4f
                    it.first.setBackgroundResource(0)
                }
                view.alpha = 1.0f
                view.setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame)
                selectedAvatarUrl = "color:$color"
            }
            view.alpha = 0.4f
        }

        val dialog =
                MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_create_profile_title)
                        .setView(dialogView)
                        .setPositiveButton(R.string.dialog_create_profile_btn_add, null)
                        .setNegativeButton(R.string.dialog_create_profile_btn_cancel) { d, _ ->
                            d.dismiss()
                        }
                        .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val name = etProfileName.text?.toString()?.trim()
                if (name.isNullOrEmpty()) {
                    tilProfileName.error = getString(R.string.dialog_create_profile_name_error)
                } else {
                    tilProfileName.error = null
                    viewModel.addProfile(name, selectedAvatarUrl)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }
}
