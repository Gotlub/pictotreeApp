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
    var isClockModeActive: Boolean = false
    var isTimerActivated: Boolean = false
    var selectedConfigIndex: Int? = null
        set(value) {
            val old = field
            field = value
            if (old != null && old < itemCount) notifyItemChanged(old)
            if (value != null && value < itemCount) notifyItemChanged(value)
        }

    init {
        setHasStableIds(true)
    }

    fun submitList(newList: List<PhraseCard>) {
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(
            PhraseCardDiffCallback(items.toList(), newList.toList())
        )
        items.clear()
        items.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
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
        items[position].isSelectedForConfig = (position == selectedConfigIndex)
        holder.bind(items[position], position == highlightedPosition, position == 0, isClockModeActive, isTimerActivated)
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

        fun bind(phraseCard: PhraseCard, isHighlighted: Boolean, isActiveCard: Boolean, isClockModeActive: Boolean, isTimerActivated: Boolean) {
            val node = phraseCard.node
            val timeConfig = phraseCard.timeConfig
            
            tvLabel.text = node.label
            card.setCardBackgroundColor(itemView.context.getColor(android.R.color.white))
            
            // Récupération de la couleur personnalisée du Timer
            val timerColorStr = org.libera.pictotree.data.SessionManager(itemView.context).getTimerColor()
            val isGreenTimer = timerColorStr.equals("green", ignoreCase = true)
            
            // Couleurs standards/solides
            val context = itemView.context
            val standardTimerColor = context.getColor(if (isGreenTimer) R.color.timer_standard_green else R.color.timer_standard_red)
            val standardJalonColor = context.getColor(R.color.jalon_standard_blue)
            
            // Couleurs pastels pour la configuration
            val pastelTimerColor = context.getColor(if (isGreenTimer) R.color.timer_pastel_green else R.color.timer_pastel_red)
            val pastelJalonColor = context.getColor(R.color.jalon_pastel_blue)
            
            // Déterminer si on utilise les pastels
            val usePastel = isClockModeActive
            
            val timerColor = if (usePastel) pastelTimerColor else standardTimerColor
            val jalonColor = if (usePastel) pastelJalonColor else standardJalonColor

            val showColoredBorders = isClockModeActive || isTimerActivated

            // 1. GESTION DU VISUEL, DE LA BORDURE ET DU ZOOM
            if (showColoredBorders) {
                if (isActiveCard) {
                    itemView.scaleX = 1.05f
                    itemView.scaleY = 1.05f
                    card.strokeWidth = 12
                    card.strokeColor = if (timeConfig.mode == TimeMode.TIMER) timerColor else jalonColor
                    
                    if (timeConfig.visualPulse && timeConfig.mode == TimeMode.TIMER) {
                        val pulseState = (android.os.SystemClock.elapsedRealtime() / 1000) % 2 == 0L
                        if (pulseState) {
                            card.setCardBackgroundColor(android.graphics.Color.parseColor("#ECEFF1"))
                        } else {
                            card.setCardBackgroundColor(android.graphics.Color.WHITE)
                        }
                    } else {
                        card.setCardBackgroundColor(android.graphics.Color.WHITE)
                    }
                } else {
                    itemView.scaleX = 1.0f
                    itemView.scaleY = 1.0f
                    if (phraseCard.isSelectedForConfig) {
                        card.strokeColor = itemView.context.getColor(R.color.highlight_stroke)
                        card.strokeWidth = 8
                    } else if (timeConfig.mode == TimeMode.JALON) {
                        card.strokeColor = jalonColor
                        card.strokeWidth = 6
                    } else if (timeConfig.mode == TimeMode.TIMER) {
                        card.strokeColor = timerColor
                        card.strokeWidth = 6
                    } else {
                        card.strokeColor = android.graphics.Color.parseColor("#DDDDDD")
                        card.strokeWidth = 2
                    }
                }
            } else {
                itemView.scaleX = 1.0f
                itemView.scaleY = 1.0f
                if (phraseCard.isSelectedForConfig) {
                    card.strokeColor = itemView.context.getColor(R.color.highlight_stroke)
                    card.strokeWidth = 8
                } else if (isHighlighted) {
                    card.setCardBackgroundColor(itemView.context.getColor(R.color.highlight_bg))
                    card.strokeColor = itemView.context.getColor(R.color.highlight_stroke)
                    card.strokeWidth = 6
                } else {
                    card.strokeColor = android.graphics.Color.parseColor("#DDDDDD")
                    card.strokeWidth = 2
                }
            }

            // 2. GESTION DU TIME TIMER
            timerView?.let { tv ->
                tv.setMode(timeConfig.mode)
                if (timeConfig.mode == TimeMode.TIMER) {
                    if (timeConfig.endTimeMillis > 0) {
                        val remaining = timeConfig.endTimeMillis - SystemClock.elapsedRealtime()
                        val total = timeConfig.durationMinutes * 60 * 1000L
                        tv.updateProgress(remaining, total)
                    } else {
                        val total = timeConfig.durationMinutes * 60 * 1000L
                        tv.updateProgress(total, total)
                    }
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
                    crossfade(false)
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

class PhraseCardDiffCallback(
    private val oldList: List<PhraseCard>,
    private val newList: List<PhraseCard>
) : androidx.recyclerview.widget.DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].node.id == newList[newItemPosition].node.id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
