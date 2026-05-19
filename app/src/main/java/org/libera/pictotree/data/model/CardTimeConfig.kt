package org.libera.pictotree.data.model

import com.google.gson.annotations.SerializedName

enum class TimeMode { NONE, TIMER, JALON }

/**
 * Configuration temporelle pour une carte du bandeau.
 */
data class CardTimeConfig(
    @SerializedName("mode") val mode: TimeMode = TimeMode.NONE,
    @SerializedName("duration_minutes") val durationMinutes: Int = 0,
    @SerializedName("play_sound") val playSoundAtEnd: Boolean = true,
    @SerializedName("auto_remove") val autoRemove: Boolean = false,
    
    // État d'exécution (non persisté sur le serveur, géré localement par Session)
    var startTimeMillis: Long = 0L,
    var endTimeMillis: Long = 0L
)
