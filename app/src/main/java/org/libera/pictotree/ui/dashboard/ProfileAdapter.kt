package org.libera.pictotree.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import org.libera.pictotree.R
import org.libera.pictotree.data.database.entity.Profile
import java.util.Collections

class ProfileAdapter(
    private val onProfileClick: (Profile) -> Unit,
    private val onEditClick: (Profile) -> Unit,
    private val onOrderChanged: (List<Profile>) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    private val profiles = mutableListOf<Profile>()
    private var isMovingItem = false

    private val differ = androidx.recyclerview.widget.AsyncListDiffer(
        object : androidx.recyclerview.widget.ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                if (!isMovingItem) notifyItemRangeInserted(position, count)
            }
            override fun onRemoved(position: Int, count: Int) {
                if (!isMovingItem) notifyItemRangeRemoved(position, count)
            }
            override fun onMoved(fromPosition: Int, toPosition: Int) {
                if (!isMovingItem) notifyItemMoved(fromPosition, toPosition)
            }
            override fun onChanged(position: Int, count: Int, payload: Any?) {
                if (!isMovingItem) notifyItemRangeChanged(position, count, payload)
            }
        },
        androidx.recyclerview.widget.AsyncDifferConfig.Builder(ProfileItemCallback()).build()
    )

    var isAdminMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submitList(newProfiles: List<Profile>) {
        isMovingItem = false
        differ.submitList(newProfiles) {
            profiles.clear()
            profiles.addAll(newProfiles)
        }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in profiles.indices || toPosition !in profiles.indices) return
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(profiles, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(profiles, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun dispatchUpdates() {
        isMovingItem = true
        differ.submitList(profiles.toList()) {
            isMovingItem = false
            onOrderChanged(profiles.toList())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(profiles[position])
    }

    override fun getItemCount(): Int = profiles.size

    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvProfileName)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditProfile)
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        private val btnDrag: ImageView = itemView.findViewById(R.id.btnDrag)

        init {
            btnDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && isAdminMode) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }
        }

        fun bind(profile: Profile) {
            tvName.text = profile.name

            // Chargement de l'avatar avec Coil ou Couleur
            val avatar = profile.avatarUrl
            if (!avatar.isNullOrEmpty()) {
                if (avatar.startsWith("color:")) {
                    val colorStr = avatar.substringAfter("color:")
                    try {
                        ivAvatar.setImageResource(android.R.drawable.presence_online)
                        ivAvatar.setColorFilter(android.graphics.Color.parseColor(colorStr))
                    } catch (e: Exception) {
                        ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                        ivAvatar.clearColorFilter()
                    }
                } else {
                    ivAvatar.clearColorFilter()
                    val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
                    val normalizedAvatar = org.libera.pictotree.utils.FileUtils.normalizeUrl(avatar, hostUrl)
                    val imageLoader = org.libera.pictotree.network.RetrofitClient.getImageLoader(ivAvatar.context)
                    ivAvatar.load(normalizedAvatar, imageLoader) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_myplaces)
                        error(android.R.drawable.ic_menu_myplaces)
                        transformations(CircleCropTransformation())
                    }
                }
            } else {
                ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                ivAvatar.clearColorFilter()
            }

            // Activer ou désactiver le bouton d'édition et de drag selon le mode
            btnEdit.visibility = if (isAdminMode) View.VISIBLE else View.GONE
            btnDrag.visibility = if (isAdminMode) View.VISIBLE else View.GONE

            // Clic sur l'entièreté de la carte (View 4)
            itemView.setOnClickListener { onProfileClick(profile) }
            
            // Clic sur le bouton d'édition (View 3)
            btnEdit.setOnClickListener { onEditClick(profile) }
        }
    }
}

class ProfileItemCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Profile>() {
    override fun areItemsTheSame(oldItem: Profile, newItem: Profile): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Profile, newItem: Profile): Boolean {
        return oldItem == newItem
    }
}
