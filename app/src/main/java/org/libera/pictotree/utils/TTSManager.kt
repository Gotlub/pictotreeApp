package org.libera.pictotree.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Gestionnaire de synthèse vocale (TTS) pour l'application Pictotree.
 * Supporte les listes d'attente (QUEUE_ADD) pour l'illumination séquentielle.
 */
class TTSManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingLanguage: String? = null
    
    // Callbacks pour l'illumination UI
    private var onStartListener: ((String) -> Unit)? = null
    private var onDoneListener: ((String) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupProgressListener()
            isInitialized = true
            
            // Appliquer la langue en attente ou la langue par défaut
            val langToSet = pendingLanguage ?: Locale.getDefault().language
            val result = tts?.setLanguage(Locale(langToSet))
            
            android.util.Log.d("TTSManager", "TTS Initialized. Setting language to $langToSet. Result: $result")
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                android.util.Log.e("TTSManager", "Language $langToSet is not supported or missing data.")
            }
        } else {
            android.util.Log.e("TTSManager", "TTS Initialization failed with status: $status")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { onStartListener?.invoke(it) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { onDoneListener?.invoke(it) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { onErrorListener?.invoke(it) }
            }
        })
    }

    /**
     * Définit la langue du moteur TTS.
     * @param languageCode Code ISO (ex: "fr", "en")
     */
    fun setLanguage(languageCode: String) {
        pendingLanguage = languageCode
        if (isInitialized) {
            val locale = Locale(languageCode)
            val result = tts?.setLanguage(locale)
            android.util.Log.d("TTSManager", "Language updated to $languageCode. Result: $result")
        } else {
            android.util.Log.d("TTSManager", "Language $languageCode stored as pending (TTS not ready)")
        }
    }

    /**
     * Lit un texte en le mettant dans la file d'attente.
     * @param text Texte à lire
     * @param utteranceId ID unique pour le suivi (ex: index dans la liste de phrase)
     * @param flush Si vrai, vide la file d'attente avant de parler
     */
    fun speak(text: String, utteranceId: String? = null, flush: Boolean = false) {
        if (isInitialized && text.isNotBlank()) {
            val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, mode, null, utteranceId ?: "TTS_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun setListeners(
        onStart: ((String) -> Unit)? = null,
        onDone: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        this.onStartListener = onStart
        this.onDoneListener = onDone
        this.onErrorListener = onError
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
