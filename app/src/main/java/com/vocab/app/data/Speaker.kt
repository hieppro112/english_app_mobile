package com.vocab.app.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Thin wrapper around Android text-to-speech (US English). */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(0.9f)
            ready = true
            pending?.let { speak(it) }
            pending = null
        }
    }

    fun speak(text: String) {
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun shutdown() = tts.shutdown()
}
