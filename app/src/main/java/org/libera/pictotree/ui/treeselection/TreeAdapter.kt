package org.libera.pictotree.ui.treeselection

import android.graphics.Color
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
import org.libera.pictotree.data.database.dao.TreeWithColor
import org.libera.pictotree.data.database.entity.TreeEntity
import java.io.File

class TreeAdapter(
    private val username: String,
    private val hostUrl: String,
    private val onTreeClick: (TreeEntity) -> Unit
) : ListAdapter<TreeWithColor, TreeAdapter.TreeViewHolder>(TreeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tree_card, parent, false)
        return TreeViewHolder(view, username, hostUrl, onTreeClick)
    }

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TreeViewHolder(
        itemView: View,
        private val username: String,
        private val hostUrl: String,
        private val onTreeClick: (TreeEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivTreeRoot: ImageView = itemView.findViewById(R.id.ivTreeRoot)
        private val tvTreeName: TextView = itemView.findViewById(R.id.tvTreeName)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.cardTree)

        fun bind(item: TreeWithColor) {
            val tree = item.tree
            tvTreeName.text = tree.name
            
            // Appliquer la couleur Fitzgerald (CAA)
            try {
                val color = if (item.colorCode.isNotEmpty()) Color.parseColor(item.colorCode) else Color.BLACK
                cardView.strokeColor = color
            } catch (e: Exception) {
                cardView.strokeColor = Color.BLACK
            }

            val url = tree.rootUrl ?: ""
            var finalSource: Any = url

            // 1. Chercher d'abord en local via le hash de l'URL
            if (url.isNotEmpty()) {
                val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(url)
                val localFile = java.io.File(itemView.context.filesDir, "$username/images/$fileName")
                if (localFile.exists()) {
                    finalSource = localFile
                } else if (!url.startsWith("http") && !url.startsWith("file")) {
                    // Si c'est un chemin relatif et pas de fichier local, on normalise pour Coil (fallback)
                    val normPath = url.replace("^/+".toRegex(), "").replace("^(pictograms/|images/)".toRegex(), "")
                    finalSource = "$hostUrl/api/v1/mobile/pictograms/$normPath"
                }
            }

            if (url.isNotEmpty()) {
                ivTreeRoot.load(finalSource) {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_background)
                    error(R.drawable.ic_launcher_background)
                    diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                }
            } else {
                ivTreeRoot.setImageResource(R.drawable.ic_launcher_background)
            }

            itemView.setOnClickListener { onTreeClick(tree) }
        }
    }

    class TreeDiffCallback : DiffUtil.ItemCallback<TreeWithColor>() {
        override fun areItemsTheSame(oldItem: TreeWithColor, newItem: TreeWithColor): Boolean {
            return oldItem.tree.id == newItem.tree.id
        }

        override fun areContentsTheSame(oldItem: TreeWithColor, newItem: TreeWithColor): Boolean {
            return oldItem == newItem
        }
    }
}
