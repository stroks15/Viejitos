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
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

/** UI nativa de llamadas con botones grandes y bloqueo anti-toques. */
class CallActivity : Activity(), SensorEventListener {
  companion object {
    const val EXTRA_NAME="contact_name"; const val EXTRA_NUMBER="phone_number"
    const val EXTRA_PHOTO="photo_uri"; const val EXTRA_STATE="call_state"
    const val EXTRA_UNKNOWN="unknown_caller"; const val EXTRA_RELATIONSHIP="contact_relationship"
    @Volatile var current: CallActivity? = null
  }
  private lateinit var titleView:TextView
  private lateinit var nameView:TextView
  private lateinit var relationshipView:TextView
  private lateinit var avatar:FrameLayout
  private lateinit var actions:LinearLayout
  private lateinit var warning:TextView
  private val handler=Handler(Looper.getMainLooper())
  private var tts:TextToSpeech?=null
  private var speaker=false
  private var muted=false
  private var touchReady=false
  private var lastAction=0L
  private var answeredAt=0L
  private var timer:Runnable?=null
  private var proximity:SensorManager?=null
  private var near=false

  override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);current=this
    if(android.os.Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true)}
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    buildUi()
    handler.postDelayed({touchReady=true;renderCallState(CallManager.currentState)},500)
    val sensorManager=getSystemService(Context.SENSOR_SERVICE) as SensorManager
    proximity=sensorManager
    sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.also{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
    val state=if(CallManager.currentState!=Call.STATE_DISCONNECTED)CallManager.currentState else intent.getIntExtra(EXTRA_STATE,CallManager.currentState)
    renderCallState(state);announceIncoming(state)
  }
  override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);touchReady=false;buildUi();handler.postDelayed({touchReady=true;renderCallState(CallManager.currentState)},500);val state=CallManager.currentState;renderCallState(state);announceIncoming(state)}
  private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
  private fun buildUi(){
    val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(24),dp(24),dp(24),dp(24));background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(7,19,36),Color.rgb(13,33,61)))}
    titleView=TextView(this).apply{text="Te están llamando";setTextColor(Color.WHITE);textSize=24f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER}
    avatar=FrameLayout(this).apply{background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(Color.rgb(49,83,111));setStroke(dp(4),Color.rgb(235,245,255))};elevation=dp(9).toFloat()}
    val photo=intent.getStringExtra(EXTRA_PHOTO)
    if(!photo.isNullOrBlank()){val image=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageURI(Uri.parse(photo));clipToOutline=true;background=GradientDrawable().apply{shape=GradientDrawable.OVAL}};avatar.addView(image,FrameLayout.LayoutParams(-1,-1))}
    else avatar.addView(TextView(this).apply{text=initials(intent.getStringExtra(EXTRA_NAME)?:intent.getStringExtra(EXTRA_NUMBER).orEmpty());textSize=58f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
    nameView=TextView(this).apply{text=intent.getStringExtra(EXTRA_NAME).takeUnless{it.isNullOrBlank()}?:intent.getStringExtra(EXTRA_NUMBER).orEmpty();setTextColor(Color.WHITE);textSize=36f;typeface=Typeface.create("sans-serif",Typeface.BOLD);gravity=Gravity.CENTER;maxLines=2}
    relationshipView=TextView(this).apply{text=intent.getStringExtra(EXTRA_RELATIONSHIP).orEmpty();setTextColor(Color.rgb(191,220,240));textSize=22f;gravity=Gravity.CENTER}
    warning=TextView(this).apply{text="⚠️ Número desconocido: No digas códigos ni des datos del banco";setTextColor(Color.WHITE);textSize=17f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setPadding(dp(12),dp(12),dp(12),dp(12));background=GradientDrawable().apply{cornerRadius=dp(15).toFloat();setColor(Color.rgb(190,25,35))};visibility=if(intent.getBooleanExtra(EXTRA_UNKNOWN,false))View.VISIBLE else View.GONE}
    actions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
    root.addView(titleView,LinearLayout.LayoutParams(-1,dp(54)))
    root.addView(avatar,LinearLayout.LayoutParams(dp(190),dp(190)).apply{gravity=Gravity.CENTER;topMargin=dp(20);bottomMargin=dp(18)})
    root.addView(nameView,LinearLayout.LayoutParams(-1,dp(94)))
    root.addView(relationshipView,LinearLayout.LayoutParams(-1,dp(34)))
    root.addView(warning,LinearLayout.LayoutParams(-1,dp(76)).apply{topMargin=dp(12);bottomMargin=dp(8)})
    root.addView(actions,LinearLayout.LayoutParams(-1,0,1f).apply{topMargin=dp(12)})
    setContentView(root)
  }
  private fun initials(value:String)=value.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase(Locale("es","MX"))}.ifBlank{"?"}
  private fun makeButton(label:String,color:Int,onClick:()->Unit):TextView=TextView(this).apply{
    text=label;setTextColor(Color.WHITE);textSize=28f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER
    background=GradientDrawable().apply{cornerRadius=dp(44).toFloat();setColor(color)}
    elevation=dp(7).toFloat();isFocusable=true;contentDescription=label
    layoutParams=LinearLayout.LayoutParams(-1,dp(84)).apply{topMargin=dp(14);bottomMargin=dp(14)}
    setOnClickListener{val now=System.currentTimeMillis();if(!touchReady||now-lastAction<600||near)return@setOnClickListener;if(label.contains("Colgar")&&answeredAt>0L&&now-answeredAt<1500L)return@setOnClickListener;lastAction=now;onClick()}
  }
  fun renderCallState(state:Int){
    if(!::actions.isInitialized)return
    actions.removeAllViews()
    when(state){
      Call.STATE_RINGING->{titleView.text="Te están llamando";actions.addView(makeButton("☎  Contestar",Color.rgb(0,168,107)){CallManager.answer();answeredAt=System.currentTimeMillis();handler.postDelayed({renderCallState(CallManager.currentState)},1500)});actions.addView(makeButton("✕  Colgar",Color.rgb(229,57,53)){CallManager.disconnect();finishAfterDisconnect()})}
      Call.STATE_ACTIVE,Call.STATE_DIALING,Call.STATE_CONNECTING,Call.STATE_HOLDING->{
        if(answeredAt==0L)answeredAt=System.currentTimeMillis()
        titleView.text="En llamada • "+elapsed()
        val autoSpeaker=getSharedPreferences("feros_settings",Context.MODE_PRIVATE).getBoolean("auto_speaker",false)
        if(autoSpeaker&&!speaker){speaker=true;CallManager.setSpeaker(true)}
        actions.addView(makeButton(if(speaker)"🔊  Altavoz activado" else "🔊  Altavoz",Color.rgb(34,101,143)){speaker=!speaker;CallManager.setSpeaker(speaker);renderCallState(CallManager.currentState)})
        actions.addView(makeButton("✕  Colgar",Color.rgb(229,57,53)){CallManager.disconnect();finishAfterDisconnect()})
        startTimer()
      }
      else->{titleView.text="Llamada finalizada";handler.postDelayed({if(!isFinishing)finish()},500)}
    }
  }
  private fun elapsed():String{val sec=((System.currentTimeMillis()-answeredAt).coerceAtLeast(0)/1000).toInt();return String.format(Locale.US,"%02d:%02d",sec/60,sec%60)}
  private fun startTimer(){timer?.let(handler::removeCallbacks);timer=Runnable{if(CallManager.currentState==Call.STATE_ACTIVE){titleView.text="En llamada • "+elapsed();handler.postDelayed(timer!!,1000)}};handler.postDelayed(timer!!,1000)}
  fun finishAfterDisconnect(){timer?.let(handler::removeCallbacks);handler.postDelayed({if(!isFinishing)finish()},650)}
  private fun announceIncoming(state:Int){if(state!=Call.STATE_RINGING)return
    if(!getSharedPreferences("feros_settings",Context.MODE_PRIVATE).getBoolean("call_voice_enabled",true))return
    val contact=nameView.text.toString()
    tts=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){tts?.language=Locale("es","MX");tts?.setSpeechRate(1.0f);tts?.speak("Llamada entrante de $contact",TextToSpeech.QUEUE_FLUSH,null,"feros-incoming-call")}}
  }
  override fun onSensorChanged(event:SensorEvent){if(event.sensor.type!=Sensor.TYPE_PROXIMITY)return;val max=event.sensor.maximumRange;val isNear=event.values[0]<max
    if(isNear!=near){near=isNear;if(near)window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)else window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)}
  }
  override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
  override fun onDestroy(){if(current===this)current=null;timer?.let(handler::removeCallbacks);proximity?.unregisterListener(this);try{tts?.stop();tts?.shutdown()}catch(_:Exception){};super.onDestroy()}
}