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
    private val layoutId: Int = R.layout.item_phrase_picto,
    private val onNodeClick: (TreeNode) -> Unit
) : ListAdapter<TreeNode, NodeAdapter.NodeViewHolder>(NodeDiffCallback()) {

    private var selectedPosition: Int = -1

    fun setSelectedPosition(position: Int) {
        val old = selectedPosition
        selectedPosition = position
        if (old != -1) notifyItemChanged(old)
        if (selectedPosition != -1) notifyItemChanged(selectedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return NodeViewHolder(view, onNodeClick)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    class NodeViewHolder(itemView: View, private val onNodeClick: (TreeNode) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val ivPicto: ImageView = itemView.findViewById(R.id.iv_picto)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)
        private val ivHasChildren: ImageView? = itemView.findViewById(R.id.iv_has_children)
        private val card: com.google.android.material.card.MaterialCardView = itemView as? com.google.android.material.card.MaterialCardView ?: itemView.findViewById(R.id.card_node)

        fun bind(node: TreeNode, isSelected: Boolean) {
            tvLabel.text = node.label
            
            if (node.id == "MORE_CHILDREN") {
                ivPicto.setImageResource(android.R.drawable.ic_menu_more)
                ivPicto.scaleType = ImageView.ScaleType.CENTER_INSIDE
                ivHasChildren?.visibility = View.GONE
            } else {
                if (node.imageUrl.isNotEmpty()) {
                    ivPicto.load(node.imageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_launcher_foreground)
                        error(R.drawable.ic_launcher_foreground)
                    }
                } else {
                    ivPicto.setImageResource(R.drawable.ic_launcher_foreground)
                }
                ivHasChildren?.visibility = if (node.children.isNotEmpty()) View.VISIBLE else View.GONE
            }
            
            if (isSelected) {
                card.strokeWidth = 6
                card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, R.color.highlight_stroke)
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
