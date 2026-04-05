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

class DashboardFragment : Fragment() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var adapter: ProfileAdapter

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Logic
        val sessionManager = org.libera.pictotree.data.SessionManager(requireContext())
        val username = sessionManager.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        
        val repository = ProfileRepository(database.profileDao())
        val factory = DashboardViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
        
        // Initialiser le Mode Admin depuis l'authentification réseau
        val isAdminInitial = arguments?.getBoolean("isAdmin", false) ?: false
        if (isAdminInitial) {
            viewModel.setAdminMode(true)
        }

        // Setup UI
        val rvProfiles = view.findViewById<RecyclerView>(R.id.rvProfiles)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvEmptyState = view.findViewById<TextView>(R.id.tvEmptyState)
        val fabAddProfile = view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddProfile)
        val ivAdminStatus = view.findViewById<ImageView>(R.id.ivAdminStatus)

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

                        // Toggle UI Elements
                        if (isAdmin) {
                            fabAddProfile.visibility = View.VISIBLE
                            ivAdminStatus.setImageResource(
                                    android.R.drawable.ic_partial_secure
                            ) // unlocked
                        } else {
                            fabAddProfile.visibility = View.GONE
                            ivAdminStatus.setImageResource(android.R.drawable.ic_secure) // locked
                        }
                    }
                }
            }
        }

        // Setup Listeners
        fabAddProfile.setOnClickListener { showCreateProfileDialog() }

        // Listener sur le cadenas pour le Mode Admin
        ivAdminStatus.setOnClickListener { 
            if (isAdminInitial) {
                viewModel.setAdminMode(!viewModel.isAdminMode.value) 
            } else {
                Toast.makeText(requireContext(), "Mode édition impossible en hors-ligne. Veuillez vous connecter au réseau.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateProfileDialog() {
        val dialogView =
                LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_profile, null)
        val tilProfileName = dialogView.findViewById<TextInputLayout>(R.id.tilProfileName)
        val etProfileName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)

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
                    viewModel.addProfile(name)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }
}
