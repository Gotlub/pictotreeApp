package org.libera.pictotree.ui.editprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import org.libera.pictotree.R

class ProfileOptionsDialogFragment : DialogFragment() {

    private lateinit var viewModel: EditProfileViewModel
    private var profileId: Int = -1

    companion object {
        fun newInstance(profileId: Int): ProfileOptionsDialogFragment {
            val frag = ProfileOptionsDialogFragment()
            val args = Bundle()
            args.putInt("profileId", profileId)
            frag.arguments = args
            return frag
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
    }

    override fun onStart() {
        super.onStart()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_profile_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        profileId = arguments?.getInt("profileId") ?: -1
        viewModel = ViewModelProvider(requireParentFragment())[EditProfileViewModel::class.java]

        val switchEnableSearch = view.findViewById<MaterialSwitch>(R.id.switchEnableSearch)
        val btnDeleteProfile = view.findViewById<MaterialButton>(R.id.btnDeleteProfile)
        val btnSyncProfile = view.findViewById<MaterialButton>(R.id.btnSyncProfile)
        val btnClose = view.findViewById<android.widget.ImageButton>(R.id.btnCloseOptions)

        btnClose.setOnClickListener { dismiss() }

        // Sync with ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    switchEnableSearch.isChecked = settings.enableSearch
                }
            }
        }

        // Listeners
        switchEnableSearch.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            if (current.enableSearch != isChecked) {
                viewModel.updateSettings(current.copy(enableSearch = isChecked), profileId)
            }
        }

        btnDeleteProfile.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Supprimer le Profil ?")
                .setMessage("Toutes les données locales de ce profil seront perdues. Cette action est irréversible.")
                .setPositiveButton("Supprimer") { _, _ ->
                    viewModel.deleteFullProfile(profileId) {
                        dismiss()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
                .setNegativeButton("Annuler", null)
                .setIcon(android.R.drawable.ic_menu_delete)
                .show()
        }

        btnSyncProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Synchronisation Cloud bientôt disponible", Toast.LENGTH_SHORT).show()
        }
    }
}
