package org.libera.pictotree.ui.explorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.libera.pictotree.R

/**
 * Adaptateur polyvalent pour afficher des TreeNode dans les différentes zones (Frères, Enfants, Breadcrumbs).
 */
class NodeAdapter(
    private val username: String, // Nouveau : Indispensable pour le mapping local
    private val layoutId: Int = R.layout.item_phrase_picto,
    private val allowNetwork: Boolean = false,
    private val onNodeClick: (TreeNode) -> Unit
) : ListAdapter<TreeNode, NodeAdapter.NodeViewHolder>(NodeDiffCallback()) {

    private var selectedPosition: Int = -1
    private var colorCode: String = "#000000"

    fun setSelectedPosition(position: Int) {
        val old = selectedPosition
        selectedPosition = position
        if (old != -1) notifyItemChanged(old)
        if (selectedPosition != -1) notifyItemChanged(selectedPosition)
    }

    fun setColorCode(color: String) {
        colorCode = color
        if (selectedPosition != -1) notifyItemChanged(selectedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return NodeViewHolder(view, username, allowNetwork, onNodeClick)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition, colorCode)
    }

    class NodeViewHolder(
        itemView: View, 
        private val username: String,
        private val allowNetwork: Boolean,
        private val onNodeClick: (TreeNode) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivPicto: ImageView = itemView.findViewById(R.id.iv_picto)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)
        private val ivHasChildren: ImageView? = itemView.findViewById(R.id.iv_has_children)
        private val card: com.google.android.material.card.MaterialCardView = itemView as? com.google.android.material.card.MaterialCardView ?: itemView.findViewById(R.id.card_node)

        fun bind(node: TreeNode, isSelected: Boolean, colorCode: String) {
            tvLabel.text = node.label
            
            if (node.id == "MORE_CHILDREN") {
                ivPicto.setImageResource(android.R.drawable.ic_menu_more)
                ivPicto.scaleType = ImageView.ScaleType.CENTER_INSIDE
                ivHasChildren?.visibility = View.GONE
            } else {
                if (node.imageUrl.isNotEmpty()) {
                    // MAPPING LOCAL MANUEL (Priorité absolue)
                    val cleanUrl = org.libera.pictotree.utils.FileUtils.getCleanUrl(node.imageUrl)
                    val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(cleanUrl)
                    val localFile = java.io.File(itemView.context.filesDir, "$username/images/$fileName")
                    
                    var finalSource: Any = if (localFile.exists()) localFile else node.imageUrl
                    
                    // Normalisation si c'est un chemin relatif (fallback)
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

                        // GESTION DU MODE OFFLINE STRICT
                        diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        if (!allowNetwork) {
                            networkCachePolicy(coil.request.CachePolicy.DISABLED)
                        }
                    }
                } else {
                    ivPicto.setImageResource(R.drawable.ic_launcher_foreground)
                }
                ivHasChildren?.visibility = if (node.children.isNotEmpty()) View.VISIBLE else View.GONE
            }
            
            if (isSelected) {
                card.strokeWidth = 6
                try {
                    card.strokeColor = android.graphics.Color.parseColor(colorCode)
                } catch (e: Exception) {
                    card.strokeColor = android.graphics.Color.BLACK
                }
            } else {
                card.strokeWidth = 2
                card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, com.google.android.material.R.color.material_dynamic_neutral90)
            }

            itemView.setOnClickListener { onNodeClick(node) }
        }
    }

    class NodeDiffCallback : DiffUtil.ItemCallback<TreeNode>() {
        override fun areItemsTheSame(oldItem: TreeNode, newItem: TreeNode): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TreeNode, newItem: TreeNode): Boolean = oldItem == newItem
    }
}
