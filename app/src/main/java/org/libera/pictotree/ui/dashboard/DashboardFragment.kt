package org.libera.pictotree.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.repository.ProfileRepository

class DashboardFragment : Fragment() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var adapter: ProfileAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Logic
        val database = AppDatabase.getDatabase(requireContext())
        val repository = ProfileRepository(database.profileDao())
        val factory = DashboardViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]

        // Setup UI
        val rvProfiles = view.findViewById<RecyclerView>(R.id.rvProfiles)
        val fabAddProfile = view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddProfile)
        val ivAdminStatus = view.findViewById<ImageView>(R.id.ivAdminStatus)

        adapter = ProfileAdapter(
            onProfileClick = { profile ->
                Toast.makeText(context, "Opening View 4 for ${profile.name}", Toast.LENGTH_SHORT).show()
                // TODO: Naviguer vers le fragment View 4 (Parcours)
            },
            onEditClick = { profile ->
                Toast.makeText(context, "Editing ${profile.name} (View 3)", Toast.LENGTH_SHORT).show()
                // TODO: Naviguer vers le fragment View 3 (Gestion)
            }
        )
        rvProfiles.layoutManager = LinearLayoutManager(requireContext())
        rvProfiles.adapter = adapter

        // Setup Observers
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                // Observer for Profiles List
                launch {
                    viewModel.profiles.collect { profiles ->
                        adapter.submitList(profiles)
                    }
                }

                // Observer for Safe/Admin Mode State
                launch {
                    viewModel.isAdminMode.collect { isAdmin ->
                        adapter.isAdminMode = isAdmin
                        
                        // Toggle UI Elements
                        if (isAdmin) {
                            fabAddProfile.visibility = View.VISIBLE
                            //ivAdminStatus.setImageResource(android.R.drawable.ic_menu_unlock) // unlocked
                        } else {
                            fabAddProfile.visibility = View.GONE
                            ivAdminStatus.setImageResource(android.R.drawable.ic_secure) // locked
                        }
                    }
                }
            }
        }

        // Setup Listeners
        fabAddProfile.setOnClickListener {
            // TODO: Afficher une dialog pour ajouter un profil
            viewModel.addProfile("Nouveau Patient ${adapter.itemCount + 1}")
        }
        
        // Debug pour le développeur (clic sur le cadenas pour basculer le mode)
        ivAdminStatus.setOnClickListener {
            viewModel.setAdminMode(!viewModel.isAdminMode.value)
        }
    }
}
