package org.libera.pictotree.ui.treeselection

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
import org.libera.pictotree.data.database.entity.TreeEntity
import java.io.File

class TreeAdapter(private val onTreeClick: (TreeEntity) -> Unit) :
    ListAdapter<TreeEntity, TreeAdapter.TreeViewHolder>(TreeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tree_card, parent, false)
        return TreeViewHolder(view, onTreeClick)
    }

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TreeViewHolder(
        itemView: View,
        private val onTreeClick: (TreeEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivTreeRoot: ImageView = itemView.findViewById(R.id.ivTreeRoot)
        private val tvTreeName: TextView = itemView.findViewById(R.id.tvTreeName)

        fun bind(tree: TreeEntity) {
            tvTreeName.text = tree.name
            
            // On utilise Coil pour charger l'image
            if (!tree.rootUrl.isNullOrEmpty()) {
                val file = File(tree.rootUrl)
                if (file.exists()) {
                    ivTreeRoot.load(file) {
                        placeholder(R.drawable.ic_launcher_background)
                        error(R.drawable.ic_launcher_background)
                    }
                } else {
                    ivTreeRoot.load(tree.rootUrl) {
                        placeholder(R.drawable.ic_launcher_background)
                        error(R.drawable.ic_launcher_background)
                    }
                }
            } else {
                ivTreeRoot.setImageResource(R.drawable.ic_launcher_background)
            }

            itemView.setOnClickListener { onTreeClick(tree) }
        }
    }

    class TreeDiffCallback : DiffUtil.ItemCallback<TreeEntity>() {
        override fun areItemsTheSame(oldItem: TreeEntity, newItem: TreeEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TreeEntity, newItem: TreeEntity): Boolean {
            return oldItem == newItem
        }
    }
}
