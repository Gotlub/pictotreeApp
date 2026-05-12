package org.libera.pictotree.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.network.dto.ProfileDTO

class ImportProfileDialogFragment(
    private val remoteProfilesFlow: StateFlow<List<ProfileDTO>>,
    private val onImportClick: (ProfileDTO) -> Unit
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_import_profile, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv: RecyclerView = view.findViewById(R.id.rvRemoteProfiles)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBarRemoteProfiles)

        val adapter = RemoteProfileAdapter { profile ->
            onImportClick(profile)
            dismiss()
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteProfilesFlow.collect { profiles ->
                    adapter.submitList(profiles)
                    progressBar.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    class RemoteProfileAdapter(private val onImport: (ProfileDTO) -> Unit) :
        ListAdapter<ProfileDTO, RemoteProfileAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_remote_profile, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position), onImport)
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val ivAvatar: ImageView = view.findViewById(R.id.ivRemoteAvatar)
            private val tvName: TextView = view.findViewById(R.id.tvRemoteProfileName)
            private val btnImport: View = view.findViewById(R.id.btnDoImport)

            fun bind(profile: ProfileDTO, onImport: (ProfileDTO) -> Unit) {
                tvName.text = profile.name
                
                if (!profile.remoteAvatarUrl.isNullOrEmpty()) {
                    ivAvatar.load(profile.remoteAvatarUrl) {
                        placeholder(R.drawable.ic_launcher_foreground)
                        error(R.drawable.ic_launcher_foreground)
                    }
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
                }

                btnImport.setOnClickListener { onImport(profile) }
            }
        }

        class DiffCallback : DiffUtil.ItemCallback<ProfileDTO>() {
            override fun areItemsTheSame(oldItem: ProfileDTO, newItem: ProfileDTO) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ProfileDTO, newItem: ProfileDTO) = oldItem == newItem
        }
    }
}
