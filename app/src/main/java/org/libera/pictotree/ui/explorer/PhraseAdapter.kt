package org.libera.pictotree.ui.explorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.libera.pictotree.R

class PhraseAdapter(
    private val username: String, // Nouveau : Indispensable pour le mapping local
    val layoutId: Int = R.layout.item_phrase_picto,
    private val allowNetwork: Boolean = false,
    private val onItemClick: ((TreeNode) -> Unit)? = null
) : RecyclerView.Adapter<PhraseAdapter.PhraseViewHolder>() {

    private val items = mutableListOf<TreeNode>()
    private var highlightedPosition: Int = -1

    init {
        setHasStableIds(true)
    }

    fun submitList(newList: List<TreeNode>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<TreeNode> = items

    override fun getItemId(position: Int): Long {
        return items[position].id.hashCode().toLong()
    }

    override fun getItemCount(): Int = items.size

    fun highlightPosition(position: Int) {
        val oldPosition = highlightedPosition
        highlightedPosition = position
        if (oldPosition != -1) notifyItemChanged(oldPosition)
        if (highlightedPosition != -1) notifyItemChanged(highlightedPosition)
    }

    /**
     * Déplacement manuel ultra-stable pour le Drag & Drop.
     * Gère les mouvements gauche/droite sans duplication ni saut.
     */
    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        
        val item = items.removeAt(from)
        items.add(to, item)
        
        // Notify horizontal move explicitly
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return PhraseViewHolder(view, username, allowNetwork, onItemClick)
    }

    override fun onBindViewHolder(holder: PhraseViewHolder, position: Int) {
        holder.bind(items[position], position == highlightedPosition)
    }

    class PhraseViewHolder(
        itemView: View,
        private val username: String,
        private val allowNetwork: Boolean,
        private val onItemClick: ((TreeNode) -> Unit)? = null
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivPicto: ImageView = itemView.findViewById(R.id.iv_picto)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)
        private val card: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(node: TreeNode, isHighlighted: Boolean) {
            tvLabel.text = node.label
            
            if (isHighlighted) {
                card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.highlight_bg))
                card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, R.color.highlight_stroke)
                card.strokeWidth = 6
            } else {
                card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.context, android.R.color.white))
                card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, com.google.android.material.R.color.material_dynamic_neutral90)
                card.strokeWidth = 2
            }

            if (node.imageUrl.isNotEmpty()) {
                // MAPPING LOCAL MANUEL
                val cleanUrl = org.libera.pictotree.utils.FileUtils.getCleanUrl(node.imageUrl)
                val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(cleanUrl)
                val localFile = java.io.File(itemView.context.filesDir, "$username/images/$fileName")
                
                var finalSource: Any = if (localFile.exists()) localFile else node.imageUrl
                
                // Normalisation chemin relatif (fallback)
                if (finalSource is String && !finalSource.startsWith("http") && !finalSource.startsWith("file")) {
                    val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
                    finalSource = "${hostUrl.removeSuffix("/")}/${finalSource.removePrefix("/")}"
                }

                ivPicto.load(finalSource) {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_foreground)
                    error(R.drawable.ic_launcher_foreground)

                    // Injecter le token si on doit aller sur le réseau
                    if (finalSource is String && (finalSource.contains("/api/v1/mobile/") || finalSource.contains("/pictograms/"))) {
                        val sessionManager = org.libera.pictotree.data.SessionManager(itemView.context)
                        val token = sessionManager.getToken()
                        if (!token.isNullOrEmpty()) {
                            addHeader("Authorization", "Bearer $token")
                        }
                    }

                    diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    if (!allowNetwork) {
                        networkCachePolicy(coil.request.CachePolicy.DISABLED)
                    }
                }
            } else {
                ivPicto.setImageResource(R.drawable.ic_launcher_foreground)
            }

            itemView.setOnClickListener { onItemClick?.invoke(node) }
        }
    }
}
