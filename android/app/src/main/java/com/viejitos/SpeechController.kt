package com.viejitos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Motor TTS compartido y precalentado antes de que React Native monte la interfaz. */
object SpeechController {
  @Volatile private var engine: TextToSpeech? = null
  @Volatile private var ready = false
  private val initializing = AtomicBoolean(false)
  private val main = Handler(Looper.getMainLooper())

  fun initialize(context: Context) {
    if (engine != null || !initializing.compareAndSet(false, true)) return
    main.post {
      try {
        engine = TextToSpeech(context.applicationContext) { status ->
          val tts = engine
          if (status == TextToSpeech.SUCCESS && tts != null) {
            val mx = Locale("es", "MX")
            val es = Locale("es", "ES")
            if (tts.setLanguage(mx) < TextToSpeech.LANG_AVAILABLE) tts.setLanguage(es)
            tts.setSpeechRate(1.15f)
            ready = true
          }
          initializing.set(false)
        }
      } catch (_: Exception) {
        initializing.set(false)
      }
    }
  }

  fun speak(text: String, onFinished: (() -> Unit)? = null): Boolean {
    val tts = engine ?: return false
    if (!ready) return false
    return try {
      if (onFinished != null) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {}
          override fun onDone(utteranceId: String?) { main.post { onFinished() } }
          @Deprecated("Deprecated in Android")
          override fun onError(utteranceId: String?) { main.post { onFinished() } }
          override fun onError(utteranceId: String?, errorCode: Int) { main.post { onFinished() } }
        })
      }
      tts.speak(text.take(240), TextToSpeech.QUEUE_FLUSH, null, "feros-${System.nanoTime()}") == TextToSpeech.SUCCESS
    } catch (_: Exception) { false }
  }
}
