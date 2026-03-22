package org.libera.pictotree.ui.editprofile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.libera.pictotree.R
import org.libera.pictotree.network.dto.TreeMetadataDTO

class RemoteTreeAdapter(
    private val onTreeImport: (TreeMetadataDTO) -> Unit
) : RecyclerView.Adapter<RemoteTreeAdapter.RemoteTreeViewHolder>() {

    private val trees = mutableListOf<TreeMetadataDTO>()

    fun submitList(newTrees: List<TreeMetadataDTO>) {
        trees.clear()
        trees.addAll(newTrees)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemoteTreeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_remote_tree, parent, false)
        return RemoteTreeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RemoteTreeViewHolder, position: Int) {
        holder.bind(trees[position])
    }

    override fun getItemCount() = trees.size

    inner class RemoteTreeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.textRemoteTreeName)
        private val ownerText: TextView = itemView.findViewById(R.id.textRemoteTreeOwner)
        private val importButton: Button = itemView.findViewById(R.id.buttonImport)

        fun bind(tree: TreeMetadataDTO) {
            nameText.text = tree.name
            val visibility = if (tree.isPublic) "Public" else "Private"
            ownerText.text = "Owner: ${tree.owner} ($visibility)"
            
            importButton.setOnClickListener {
                onTreeImport(tree)
            }
        }
    }
}
