package org.libera.pictotree.ui.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Composant visuel pour le Time Timer et les Jalons.
 * Dessine un camembert rouge qui se vide ou une bordure bleue.
 */
class TimeTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val piePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935") // Rouge Time Timer
        style = Paint.Style.FILL
    }
    
    private val jalonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3") // Bleu Jalon
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }
    
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        style = Paint.Style.FILL
    }

    private var progress: Float = 0f // 1.0 = plein, 0.0 = vide
    private var isJalonMode: Boolean = false

    /**
     * Définit le mode d'affichage.
     */
    fun setMode(mode: org.libera.pictotree.data.model.TimeMode) {
        isJalonMode = (mode == org.libera.pictotree.data.model.TimeMode.JALON)
        if (mode == org.libera.pictotree.data.model.TimeMode.NONE) {
            visibility = GONE
        } else {
            visibility = VISIBLE
        }
        invalidate()
    }

    /**
     * Met à jour le niveau du camembert rouge.
     * @param remainingMs Temps restant en ms
     * @param totalMs Durée totale initiale en ms
     */
    fun updateProgress(remainingMs: Long, totalMs: Long) {
        if (isJalonMode) return
        this.progress = if (totalMs > 0) (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 6f
        
        if (isJalonMode) {
            // Mode JALON : Cercle bleu épais
            canvas.drawCircle(centerX, centerY, radius, jalonPaint)
            return
        }

        // Mode TIMER : Camembert rouge
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        
        // 1. Fond gris
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        
        // 2. Arc rouge (Le temps restant)
        // Départ à midi (-90°)
        // Le sweepAngle diminue vers 0 pour vider le camembert
        val sweepAngle = progress * 360f
        canvas.drawArc(rect, -90f, sweepAngle, true, piePaint)
    }
}
