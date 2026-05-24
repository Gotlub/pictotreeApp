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

    var isAdminMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submitList(newProfiles: List<Profile>) {
        val diffCallback = ProfileDiffCallback(profiles, newProfiles)
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        profiles.clear()
        profiles.addAll(newProfiles)
        diffResult.dispatchUpdatesTo(this)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
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
        onOrderChanged(profiles)
        notifyDataSetChanged()
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

        private val dragRunnable = java.lang.Runnable { onStartDrag(this) }

        init {
            itemView.setOnTouchListener { v, event ->
                if (!isAdminMode) return@setOnTouchListener false
                val handler = v.handler
                if (handler != null) {
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> handler.postDelayed(dragRunnable, 250)
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(dragRunnable)
                    }
                }
                false
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

            // Activer ou désactiver le bouton d'édition selon le mode
            btnEdit.visibility = if (isAdminMode) View.VISIBLE else View.GONE

            // Clic sur l'entièreté de la carte (View 4)
            itemView.setOnClickListener { onProfileClick(profile) }
            
            // Clic sur le bouton d'édition (View 3)
            btnEdit.setOnClickListener { onEditClick(profile) }
        }
    }
}

class ProfileDiffCallback(
    private val oldList: List<Profile>,
    private val newList: List<Profile>
) : androidx.recyclerview.widget.DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
