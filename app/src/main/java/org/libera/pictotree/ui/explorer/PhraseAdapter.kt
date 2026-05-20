package org.libera.pictotree.ui.explorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.libera.pictotree.R
import org.libera.pictotree.data.model.TimeMode
import org.libera.pictotree.ui.common.TimeTimerView
import android.os.SystemClock

class PhraseAdapter(
    private val username: String,
    val layoutId: Int = R.layout.item_phrase_picto,
    private val allowNetwork: Boolean = false,
    private val onItemClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<PhraseAdapter.PhraseViewHolder>() {

    private val items = mutableListOf<PhraseCard>()
    private var highlightedPosition: Int = -1

    init {
        setHasStableIds(true)
    }

    fun submitList(newList: List<PhraseCard>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<PhraseCard> = items

    override fun getItemId(position: Int): Long {
        return items[position].node.id.hashCode().toLong()
    }

    override fun getItemCount(): Int = items.size

    fun highlightPosition(position: Int) {
        val oldPosition = highlightedPosition
        highlightedPosition = position
        if (oldPosition != -1 && oldPosition < itemCount) notifyItemChanged(oldPosition)
        if (highlightedPosition != -1 && highlightedPosition < itemCount) notifyItemChanged(highlightedPosition)
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
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
        private val onItemClick: ((Int) -> Unit)? = null
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivPicto: ImageView = itemView.findViewById(R.id.iv_picto)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)
        private val card: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView
        
        // Nullable pour supporter les anciens layouts ou les erreurs de merge
        private val timerView: TimeTimerView? = itemView.findViewById(R.id.time_timer_view)

        fun bind(phraseCard: PhraseCard, isHighlighted: Boolean) {
            val node = phraseCard.node
            val timeConfig = phraseCard.timeConfig
            
            tvLabel.text = node.label
            
            // 1. GESTION DU HIGHLIGHT ET BORDURE (JALON)
            if (phraseCard.isSelectedForConfig) {
                card.strokeColor = itemView.context.getColor(R.color.highlight_stroke)
                card.strokeWidth = 8
            } else if (timeConfig.mode == TimeMode.JALON) {
                card.strokeColor = android.graphics.Color.parseColor("#2196F3")
                card.strokeWidth = 10
            } else if (isHighlighted) {
                card.setCardBackgroundColor(itemView.context.getColor(R.color.highlight_bg))
                card.strokeColor = itemView.context.getColor(R.color.highlight_stroke)
                card.strokeWidth = 6
            } else {
                card.setCardBackgroundColor(itemView.context.getColor(android.R.color.white))
                card.strokeColor = android.graphics.Color.parseColor("#DDDDDD")
                card.strokeWidth = 2
            }

            // 2. GESTION DU TIME TIMER
            timerView?.let { tv ->
                tv.setMode(timeConfig.mode)
                if (timeConfig.mode == TimeMode.TIMER && timeConfig.endTimeMillis > 0) {
                    val remaining = timeConfig.endTimeMillis - SystemClock.elapsedRealtime()
                    val total = timeConfig.durationMinutes * 60 * 1000L
                    tv.updateProgress(remaining, total)
                }
            }

            // 3. CHARGEMENT IMAGE
            if (node.imageUrl.isNotEmpty()) {
                val cleanUrl = org.libera.pictotree.utils.FileUtils.getCleanUrl(node.imageUrl)
                val fileName = org.libera.pictotree.utils.FileUtils.getLocalFileNameFromUrl(cleanUrl)
                val localFile = java.io.File(itemView.context.filesDir, "$username/images/$fileName")
                var finalSource: Any = if (localFile.exists()) localFile else node.imageUrl
                
                if (finalSource is String && !finalSource.startsWith("http") && !finalSource.startsWith("file")) {
                    val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
                    finalSource = "${hostUrl.removeSuffix("/")}/${finalSource.removePrefix("/")}"
                }

                ivPicto.load(finalSource) {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_foreground)
                    error(R.drawable.ic_launcher_foreground)
                    if (finalSource is String && (finalSource.contains("/api/v1/mobile/") || finalSource.contains("/pictograms/"))) {
                        val token = org.libera.pictotree.data.SessionManager(itemView.context).getToken()
                        if (!token.isNullOrEmpty()) addHeader("Authorization", "Bearer $token")
                    }
                    diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    if (!allowNetwork) networkCachePolicy(coil.request.CachePolicy.DISABLED)
                }
            } else {
                ivPicto.setImageResource(R.drawable.ic_launcher_foreground)
            }

            itemView.setOnClickListener { onItemClick?.invoke(bindingAdapterPosition) }
        }
    }
}
