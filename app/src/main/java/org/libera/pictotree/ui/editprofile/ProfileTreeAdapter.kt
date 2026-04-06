package org.libera.pictotree.ui.editprofile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.libera.pictotree.R
import org.libera.pictotree.data.database.entity.TreeEntity
import java.io.File
import java.util.Collections

class ProfileTreeAdapter(
    private val onTreeDelete: (TreeEntity) -> Unit,
    private val onOrderChanged: (List<TreeEntity>) -> Unit,
    private val onViewTree: (TreeEntity) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ProfileTreeAdapter.TreeViewHolder>() {

    private val trees = mutableListOf<ProfileTreeUiModel>()
    
    var isOnlineMode: Boolean = true
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submitList(newTrees: List<ProfileTreeUiModel>) {
        trees.clear()
        trees.addAll(newTrees)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(trees, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(trees, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun dispatchUpdates() {
        onOrderChanged(trees.map { it.tree })
        notifyDataSetChanged() 
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_tree, parent, false)
        return TreeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        holder.bind(trees[position])
    }

    override fun getItemCount(): Int = trees.size

    inner class TreeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val treeName: TextView = itemView.findViewById(R.id.textTreeName)
        private val textOrderNumber: TextView = itemView.findViewById(R.id.textOrderNumber)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDeleteTree)
        private val viewButton: ImageButton = itemView.findViewById(R.id.buttonViewTree)
        private val imageIcon: ImageView = itemView.findViewById(R.id.imageTreeIcon)

        private val dragRunnable = java.lang.Runnable { onStartDrag(this) }

        init {
            itemView.setOnTouchListener { v, event ->
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

        fun bind(model: ProfileTreeUiModel) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                textOrderNumber.text = "#${position + 1}"
            }
            treeName.text = model.tree.name
            
            // Sécurité : masquer la suppression si hors-ligne
            deleteButton.visibility = if (isOnlineMode) View.VISIBLE else View.GONE
            
            deleteButton.setOnClickListener {
                onTreeDelete(model.tree)
            }
            viewButton.setOnClickListener {
                onViewTree(model.tree)
            }
            if (model.localThumbnailPath != null) {
                imageIcon.load(File(model.localThumbnailPath)) {
                    crossfade(true)
                }
            } else {
                imageIcon.setImageResource(R.mipmap.ic_launcher)
            }
        }
    }
}
