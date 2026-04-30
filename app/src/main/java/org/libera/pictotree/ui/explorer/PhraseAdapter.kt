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

class PhraseAdapter(private val layoutId: Int = R.layout.item_phrase_picto) : ListAdapter<TreeNode, PhraseAdapter.PhraseViewHolder>(PhraseDiffCallback()) {

    private var highlightedPosition: Int = -1

    /**
     * Met à jour la position de l'item à illuminer (TTS progress).
     */
    fun highlightPosition(position: Int) {
        val oldPosition = highlightedPosition
        highlightedPosition = position
        if (oldPosition != -1) notifyItemChanged(oldPosition)
        if (highlightedPosition != -1) notifyItemChanged(highlightedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return PhraseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhraseViewHolder, position: Int) {
        val node = getItem(position)
        holder.bind(node, position == highlightedPosition)
    }

    class PhraseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPicto: ImageView = itemView.findViewById(R.id.iv_picto)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)
        private val card: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(node: TreeNode, isHighlighted: Boolean) {
            tvLabel.text = node.label
            
            // Gestion de l'illumination au rythme de la lecture TTS
            if (isHighlighted) {
                card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.highlight_bg))
                card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, R.color.highlight_stroke)
                card.strokeWidth = 6
            } else {
                card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.context, android.R.color.white))
                card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, com.google.android.material.R.color.material_dynamic_neutral90) // Original default from XML
                card.strokeWidth = 2
            }

            if (node.imageUrl.isNotEmpty()) {
                ivPicto.load(node.imageUrl) {
                    crossfade(true)
                    error(R.drawable.ic_launcher_foreground)
                }
            } else {
                ivPicto.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
    }

    class PhraseDiffCallback : DiffUtil.ItemCallback<TreeNode>() {
        override fun areItemsTheSame(oldItem: TreeNode, newItem: TreeNode): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TreeNode, newItem: TreeNode): Boolean {
            // Dans ce MVP on compare juste le hash via Kotlin Data Class
            return oldItem == newItem
        }
    }
}
