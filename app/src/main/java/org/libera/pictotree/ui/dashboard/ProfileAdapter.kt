package org.libera.pictotree.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.libera.pictotree.R
import org.libera.pictotree.data.database.entity.Profile

class ProfileAdapter(
    private val onProfileClick: (Profile) -> Unit,
    private val onEditClick: (Profile) -> Unit
) : ListAdapter<Profile, ProfileAdapter.ProfileViewHolder>(ProfileDiffCallback()) {

    var isAdminMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvProfileName)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditProfile)

        fun bind(profile: Profile) {
            tvName.text = profile.name

            // Activer ou désactiver le bouton d'édition selon le mode
            btnEdit.visibility = if (isAdminMode) View.VISIBLE else View.GONE

            // Clic sur l'entièreté de la carte (View 4)
            itemView.setOnClickListener { onProfileClick(profile) }
            
            // Clic sur le bouton d'édition (View 3)
            btnEdit.setOnClickListener { onEditClick(profile) }
        }
    }

    class ProfileDiffCallback : DiffUtil.ItemCallback<Profile>() {
        override fun areItemsTheSame(oldItem: Profile, newItem: Profile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Profile, newItem: Profile): Boolean {
            return oldItem == newItem
        }
    }
}
