package com.viejitos

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Pantalla nativa inmediata; no depende del puente de React Native. */
class CallActivity : Activity() {
  companion object {
    const val EXTRA_NAME = "contact_name"
    const val EXTRA_NUMBER = "phone_number"
    const val EXTRA_PHOTO = "photo_uri"
    const val EXTRA_STATE = "call_state"
    @Volatile var current: CallActivity? = null
  }

  private lateinit var titleView: TextView
  private lateinit var nameView: TextView
  private lateinit var avatar: ImageView
  private lateinit var actions: LinearLayout
  private var speakerEnabled = false
  private var microphoneMuted = false
  private var tts: TextToSpeech? = null
  private val handler = Handler(Looper.getMainLooper())
  private var disconnectRunnable: Runnable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    current = this
    if (android.os.Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    buildUi()
    val state = if (CallManager.currentState != Call.STATE_DISCONNECTED) CallManager.currentState else intent.getIntExtra(EXTRA_STATE, CallManager.currentState)
    renderCallState(state)
    announceIncomingIfEnabled(state)
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    buildUi()
    val state = if (CallManager.currentState != Call.STATE_DISCONNECTED) CallManager.currentState else intent.getIntExtra(EXTRA_STATE, CallManager.currentState)
    renderCallState(state)
    announceIncomingIfEnabled(state)
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  private fun buildUi() {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(dp(24), dp(28), dp(24), dp(28))
      setBackgroundColor(Color.rgb(12, 18, 25))
    }
    titleView = TextView(this).apply {
      text = "Te están llamando"
      setTextColor(Color.WHITE)
      textSize = 23f
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
    }
    avatar = ImageView(this).apply {
      scaleType = ImageView.ScaleType.CENTER_CROP
      background = circle(Color.rgb(45, 75, 95))
      clipToOutline = true
    }
    val avatarParams = LinearLayout.LayoutParams(dp(180), dp(180)).apply {
      gravity = Gravity.CENTER
      topMargin = dp(34)
      bottomMargin = dp(24)
    }
    val photo = intent.getStringExtra(EXTRA_PHOTO)
    if (!photo.isNullOrBlank()) {
      try { avatar.setImageURI(Uri.parse(photo)) } catch (_: Exception) { avatar.setImageResource(android.R.drawable.ic_menu_call) }
    } else {
      avatar.setImageResource(android.R.drawable.ic_menu_call)
    }
    nameView = TextView(this).apply {
      text = intent.getStringExtra(EXTRA_NAME).takeUnless { it.isNullOrBlank() }
        ?: intent.getStringExtra(EXTRA_NUMBER).orEmpty()
      setTextColor(Color.WHITE)
      textSize = 34f
      typeface = Typeface.create("sans-serif", Typeface.BOLD)
      gravity = Gravity.CENTER
      maxLines = 3
    }
    actions = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
    }
    root.addView(titleView, LinearLayout.LayoutParams(-1, dp(48)))
    root.addView(avatar, avatarParams)
    root.addView(nameView, LinearLayout.LayoutParams(-1, dp(110)))
    root.addView(actions, LinearLayout.LayoutParams(-1, dp(280)))
    setContentView(root)
  }

  private fun circle(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
  }

  private fun pill(color: Int, label: String, click: () -> Unit): TextView {
    return TextView(this).apply {
      text = label
      setTextColor(Color.WHITE)
      textSize = 25f
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
      background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(48).toFloat()
        setColor(color)
      }
      isFocusable = true
      contentDescription = label
      setOnClickListener { click() }
      layoutParams = LinearLayout.LayoutParams(-1, dp(76)).apply {
        topMargin = dp(9)
        bottomMargin = dp(9)
      }
    }
  }

  fun renderCallState(state: Int) {
    if (!::actions.isInitialized) return
    actions.removeAllViews()
    when (state) {
      Call.STATE_RINGING -> {
        titleView.text = "Te están llamando"
        actions.addView(pill(Color.rgb(0, 168, 107), "☎  Contestar") { CallManager.answer() })
        actions.addView(pill(Color.rgb(229, 57, 53), "✕  Colgar") { CallManager.disconnect(); finishAfterDisconnect() })
      }
      Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING -> {
        titleView.text = "En llamada"
        actions.addView(pill(if (speakerEnabled) Color.rgb(0, 125, 105) else Color.rgb(45, 85, 115),
          if (speakerEnabled) "🔊  Altavoz activado" else "🔈  Activar altavoz") {
          speakerEnabled = !speakerEnabled
          CallManager.setSpeaker(speakerEnabled)
          renderCallState(CallManager.currentState)
        })
        actions.addView(pill(if (microphoneMuted) Color.rgb(150, 95, 35) else Color.rgb(45, 85, 115),
          if (microphoneMuted) "🎙  Activar micrófono" else "🎙  Silenciar micrófono") {
          microphoneMuted = !microphoneMuted
          CallManager.setMuted(microphoneMuted)
          renderCallState(CallManager.currentState)
        })
        actions.addView(pill(Color.rgb(229, 57, 53), "✕  Colgar") { CallManager.disconnect(); finishAfterDisconnect() })
      }
      else -> {
        titleView.text = "Llamada finalizada"
        handler.postDelayed({ if (!isFinishing) finish() }, 500)
      }
    }
  }

  fun finishAfterDisconnect() {
    if (isFinishing) return
    disconnectRunnable?.let(handler::removeCallbacks)
    disconnectRunnable = Runnable { if (!isFinishing) finish() }
    handler.postDelayed(disconnectRunnable!!, 650)
  }

  private fun announceIncomingIfEnabled(state: Int) {
    if (state != Call.STATE_RINGING) return
    val enabled = getSharedPreferences("feros_settings", Context.MODE_PRIVATE)
      .getBoolean("call_voice_enabled", true)
    if (!enabled) return
    val contact = nameView.text?.toString().orEmpty()
    tts = TextToSpeech(this) { status ->
      if (status == TextToSpeech.SUCCESS) {
        tts?.language = Locale("es", "MX")
        tts?.setSpeechRate(1.05f)
        tts?.speak("Llamada entrante de $contact", TextToSpeech.QUEUE_FLUSH, null, "feros-incoming-call")
      }
    }
  }

  override fun onDestroy() {
    if (current === this) current = null
    try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
    super.onDestroy()
  }
}
