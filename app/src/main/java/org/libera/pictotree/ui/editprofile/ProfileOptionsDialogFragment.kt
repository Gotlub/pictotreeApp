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

        val btnDeleteProfile = view.findViewById<MaterialButton>(R.id.btnDeleteProfile)
        val btnSyncProfile = view.findViewById<MaterialButton>(R.id.btnSyncProfile)
        val btnClose = view.findViewById<android.widget.ImageButton>(R.id.btnCloseOptions)

        btnClose.setOnClickListener { dismiss() }

        btnDeleteProfile.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.supprimer_le_profil))
                .setMessage(getString(R.string.toutes_les_donn_es_locales_de_ce_profil_seront_perdues_cette_action_est_irr_versible))
                .setPositiveButton(getString(R.string.supprimer)) { _, _ ->
                    viewModel.deleteFullProfile(profileId) {
                        dismiss()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
                .setNegativeButton(getString(R.string.annuler), null)
                .setIcon(android.R.drawable.ic_menu_delete)
                .show()
        }

        btnSyncProfile.setOnClickListener {
            Toast.makeText(requireContext(),
                getString(R.string.synchronisation_cloud_bient_t_disponible), Toast.LENGTH_SHORT).show()
        }
    }
}
