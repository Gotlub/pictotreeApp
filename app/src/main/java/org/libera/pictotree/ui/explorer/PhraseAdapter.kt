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

class PhraseAdapter : ListAdapter<TreeNode, PhraseAdapter.PhraseViewHolder>(PhraseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_phrase_picto, parent, false)
        return PhraseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhraseViewHolder, position: Int) {
        val node = getItem(position)
        holder.bind(node)
    }

    class PhraseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPicto: ImageView = itemView.findViewById(R.id.iv_picto)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)

        fun bind(node: TreeNode) {
            tvLabel.text = node.label
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
