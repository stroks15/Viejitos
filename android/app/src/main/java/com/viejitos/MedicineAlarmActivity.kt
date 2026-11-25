package com.viejitos

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MedicineAlarmActivity : Activity() {
  private var ringtone: Ringtone? = null
  private var tts: TextToSpeech? = null
  private val id get() = intent.getStringExtra("reminder_id") ?: "medicine"
  private val name get() = intent.getStringExtra("medicine_name") ?: "tu medicina"
  private val dose get() = intent.getStringExtra("medicine_dose") ?: ""
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(28,32,28,32); setBackgroundColor(Color.rgb(7,19,36)) }
    root.addView(TextView(this).apply { text="¡ES HORA!"; textSize=28f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER })
    val photo = intent.getStringExtra("medicine_photo")
    if (!photo.isNullOrBlank()) { try { root.addView(ImageView(this).apply { setImageURI(android.net.Uri.parse(photo)); scaleType=ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, dp(150))) } catch (_: Exception) {} }
    root.addView(TextView(this).apply { text=name; textSize=38f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setPadding(0,28,0,12) })
    root.addView(TextView(this).apply { text=dose; textSize=27f; setTextColor(Color.rgb(205,230,245)); gravity=Gravity.CENTER; setPadding(0,0,0,36) })
    root.addView(button("✓  YA LA TOMÉ", Color.rgb(0,168,107)) { registerTaken() })
    root.addView(button("POSPONER 10 MINUTOS", Color.rgb(49,93,134)) { snooze() })
    setContentView(root)
    try { ringtone=RingtoneManager.getRingtone(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)); ringtone?.play() } catch (_: Exception) {}
    tts=TextToSpeech(this) { status -> if(status==TextToSpeech.SUCCESS){ tts?.language=Locale("es","MX"); tts?.speak("Hora de tomar $name, $dose",TextToSpeech.QUEUE_FLUSH,null,"feros-medicine") } }
  }
  private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
  private fun button(label:String,color:Int,action:()->Unit)=Button(this).apply {
    text=label; textSize=21f; setTextColor(Color.WHITE); isAllCaps=false; typeface=Typeface.DEFAULT_BOLD
    background=GradientDrawable().apply { cornerRadius=48f; setColor(color) }
    layoutParams=LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=14; bottomMargin=14 }
    setOnClickListener { action() }
  }
  private fun stopAlarm(){ try{ringtone?.stop();tts?.stop()}catch(_:Exception){} }
  private fun registerTaken(){
    stopAlarm()
    val prefs=getSharedPreferences("feros_medicine",Context.MODE_PRIVATE)
    val old=prefs.getString("history","[]") ?: "[]"
    val arr=try{org.json.JSONArray(old)}catch(_:Exception){org.json.JSONArray()}
    arr.put(org.json.JSONObject().put("id",System.currentTimeMillis().toString()).put("reminderId",id).put("name",name).put("dose",dose).put("at",System.currentTimeMillis()))
    prefs.edit().putString("history",arr.toString()).apply()
    tts?.speak("Listo, registrado",TextToSpeech.QUEUE_FLUSH,null,"feros-recorded")
    Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 1400)
  }
  private fun snooze(){
    stopAlarm()
    val at=System.currentTimeMillis()+10*60*1000L
    MedicineAlarmReceiver.scheduleSnooze(this,id,name,dose,at,intent.getStringExtra("medicine_photo"),intent.getStringExtra("days_json") ?: "[]")
    finish()
  }
  override fun onDestroy(){stopAlarm();try{tts?.shutdown()}catch(_:Exception){};super.onDestroy()}
}