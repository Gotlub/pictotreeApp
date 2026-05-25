package org.libera.pictotree.ui.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import androidx.core.content.ContextCompat
import org.libera.pictotree.R

/**
 * Composant visuel pour le Time Timer.
 * Dessine un cadran transparent au-dessus du pictogramme avec :
 * - Des numéros de 0 à 55 dans le sens anti-horaire, lisibles grâce à un ombrage blanc.
 * - Un arc de cercle coloré (rouge ou vert) anti-horaire pointant directement vers le temps restant.
 */
class TimeTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val sessionManager = org.libera.pictotree.data.SessionManager(context)

    private val piePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timer_dial_text)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        // Ombrage blanc pour assurer la lisibilité sur tous les pictogrammes (clairs ou sombres)
        setShadowLayer(6f, 0f, 0f, Color.WHITE)
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timer_dial_outline)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        setShadowLayer(4f, 0f, 0f, Color.WHITE)
    }

    private val arcStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val rect = RectF()
    private val fontMetrics = Paint.FontMetrics()

    private var remainingMinutes: Float = 0f // Temps restant en minutes
    private var translucentColor = ContextCompat.getColor(context, R.color.timer_translucent_red)
    private var solidColor = ContextCompat.getColor(context, R.color.timer_standard_red)

    init {
        updateTimerColors()
    }

    private fun updateTimerColors() {
        val timerColorStr = sessionManager.getTimerColor()
        val isGreen = timerColorStr.equals("green", ignoreCase = true)
        translucentColor = ContextCompat.getColor(
            context,
            if (isGreen) R.color.timer_translucent_green else R.color.timer_translucent_red
        )
        solidColor = ContextCompat.getColor(
            context,
            if (isGreen) R.color.timer_standard_green else R.color.timer_standard_red
        )
        arcStrokePaint.color = solidColor
    }

    /**
     * Définit le mode d'affichage. Affiche le timer uniquement en mode TIMER.
     */
    fun setMode(mode: org.libera.pictotree.data.model.TimeMode) {
        if (mode == org.libera.pictotree.data.model.TimeMode.TIMER) {
            updateTimerColors()
            visibility = VISIBLE
        } else {
            visibility = GONE
        }
        invalidate()
    }

    /**
     * Met à jour le niveau du camembert.
     * @param remainingMs Temps restant en ms
     * @param totalMs Durée totale initiale en ms
     */
    fun updateProgress(remainingMs: Long, totalMs: Long) {
        val remaining = remainingMs.coerceAtLeast(0L)
        this.remainingMinutes = remaining.toFloat() / (60f * 1000f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val minDim = min(width, height)
        val outerRadius = minDim / 2f
        
        // Rayon du cadran de l'horloge et rayon pour placer le texte
        val dialRadius = outerRadius - 16f
        val textRadius = dialRadius - 22f
        
        // 1. Tracé du contour du cadran (fin et discret)
        canvas.drawCircle(centerX, centerY, dialRadius, outlinePaint)
        
        // 3. Tracé de l'arc restant (sens anti-horaire, donc sweepAngle négatif)
        // Départ à -90 degrés (midi).
        piePaint.color = translucentColor
        rect.set(centerX - dialRadius, centerY - dialRadius, centerX + dialRadius, centerY + dialRadius)
        val sweepAngle = -(remainingMinutes / 60f) * 360f
        
        canvas.drawArc(rect, -90f, sweepAngle, true, piePaint)
        
        // Tracé de la ligne de découpe solide au bout de l'arc pour un rendu aiguisé
        canvas.drawArc(rect, -90f, sweepAngle, false, arcStrokePaint)
        
        // 4. Tracé des numéros de 0 à 55 (par pas de 5) disposés en sens anti-horaire
        val minTextSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
        val maxTextSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            22f,
            resources.displayMetrics
        )
        textPaint.textSize = (minDim * 0.095f).coerceIn(minTextSize, maxTextSize)
        
        textPaint.getFontMetrics(fontMetrics)
        val yOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
        
        for (minute in 0..55 step 5) {
            // Formule mathématique anti-horaire : départ à midi (-90°)
            val angle = -90f - (minute / 60f) * 360f
            val rad = Math.toRadians(angle.toDouble())
            val numX = centerX + textRadius * Math.cos(rad)
            val numY = centerY + textRadius * Math.sin(rad)
            
            canvas.drawText(minute.toString(), numX.toFloat(), (numY - yOffset).toFloat(), textPaint)
        }
    }
}
