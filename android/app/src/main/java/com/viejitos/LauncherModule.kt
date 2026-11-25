package com.viejitos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.app.Activity
import com.facebook.react.bridge.ActivityEventListener
import android.content.pm.PackageManager
import android.app.role.RoleManager
import android.telecom.TelecomManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.CallLog
import android.provider.Settings
import android.util.Base64
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.ByteArrayOutputStream

/**
 * Puente entre el servicio/Activity nativos y React Native (eventos) y estado
 * de la linterna compartido por todo el proceso.
 */
object LauncherEvents {
  @Volatile var reactContext: ReactApplicationContext? = null
  @Volatile var torchOn: Boolean = false
  @Volatile private var torchId: String? = null
  private var torchRegistered = false

  fun emit(name: String, params: WritableMap?) {
    val ctx = reactContext ?: return
    try {
      if (ctx.hasActiveReactInstance()) {
        ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(name, params)
      }
    } catch (_: Exception) {
    }
  }

  fun torchCameraId(): String? = torchId

  private fun findTorchId(cm: CameraManager): String? {
    var fallback: String? = null
    for (id in cm.cameraIdList) {
      val ch = cm.getCameraCharacteristics(id)
      if (ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
      if (ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) return id
      if (fallback == null) fallback = id
    }
    return fallback
  }

  @Synchronized
  fun ensureTorchTracking(context: Context) {
    if (torchRegistered) return
    try {
      val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      val id = findTorchId(cm) ?: return
      torchId = id
      cm.registerTorchCallback(object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
          if (cameraId != id) return
          torchOn = enabled
          val map = Arguments.createMap()
          map.putBoolean("on", enabled)
          emit("TorchStateChanged", map)
        }
      }, Handler(Looper.getMainLooper()))
      torchRegistered = true
    } catch (_: Exception) {
    }
  }
}

class LauncherModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
  private var speechPromise: Promise? = null
  init {
    ctx.addActivityEventListener(object : ActivityEventListener {
      override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != 4311) return
        val promise = speechPromise ?: return
        speechPromise = null
        if (resultCode == Activity.RESULT_OK) promise.resolve(data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: "")
        else promise.resolve("")
      }
      override fun onNewIntent(intent: Intent) {}
    })
    LauncherEvents.reactContext = ctx
    LauncherEvents.ensureTorchTracking(ctx)
    SpeechController.initialize(ctx)
  }

  override fun getName(): String = "LauncherModule"

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
  }

  @ReactMethod
  fun startSpeechRecognition(promise: Promise) {
    try {
      if (speechPromise != null) { promise.reject("SPEECH_BUSY", "Ya hay un dictado en curso."); return }
      val activity = ctx.currentActivity ?: run { promise.reject("NO_ACTIVITY", "Abre FerOs para usar el dictado."); return }
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el nombre del producto")
      }
      speechPromise = promise
      activity.startActivityForResult(intent, 4311)
    } catch (e: Exception) { speechPromise = null; promise.reject("SPEECH_UNAVAILABLE", "El dictado por voz no está disponible.", e) }
  }

  @ReactMethod
  fun saveContactRelationship(number: String, relationship: String, promise: Promise) {
    try {
      val key = number.filter { it.isDigit() }
      val prefs = ctx.getSharedPreferences("feros_settings", Context.MODE_PRIVATE)
      val json = try { org.json.JSONObject(prefs.getString("contact_relationships", "{}") ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
      if (key.isNotBlank()) { if (relationship.isBlank()) json.remove(key) else json.put(key, relationship) }
      prefs.edit().putString("contact_relationships", json.toString()).apply()
      promise.resolve(true)
    } catch (e: Exception) { promise.reject("RELATIONSHIP_SAVE", "No se pudo guardar el parentesco.", e) }
  }

  @ReactMethod
  fun setAutoSpeakerEnabled(enabled: Boolean, promise: Promise) {
    try { ctx.getSharedPreferences("feros_settings", Context.MODE_PRIVATE).edit().putBoolean("auto_speaker", enabled).apply(); promise.resolve(true) }
    catch (e: Exception) { promise.reject("AUTO_SPEAKER", "No se pudo guardar el altavoz automático.", e) }
  }

  @ReactMethod
  fun setRingtoneProtection(enabled: Boolean, promise: Promise) {
    try {
      ctx.getSharedPreferences("feros_settings", Context.MODE_PRIVATE)
        .edit().putBoolean("ringtone_protection", enabled).apply()
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("RINGTONE_SETTING_FAILED", "No se pudo guardar el protector de timbre.", e)
    }
  }

  @ReactMethod
  fun speakSpanish(text: String, promise: Promise) {
    try {
      promise.resolve(SpeechController.speak(text))
    } catch (e: Exception) {
      promise.reject("TTS_FAILED", "No se pudo reproducir la voz.", e)
    }
  }

  private fun launch(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
  }

  // ───────────────────────── Aplicaciones instaladas ─────────────────────────

  private fun iconToBase64(drawable: Drawable, size: Int = 96): String? {
    return try {
      val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      drawable.setBounds(0, 0, size, size)
      drawable.draw(canvas)
      val out = ByteArrayOutputStream()
      bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
      bmp.recycle()
      Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Throwable) {
      null
    }
  }

  @Suppress("DEPRECATION")
  @ReactMethod
  fun getInstalledApps(promise: Promise) {
    Thread {
      try {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val items = pm.queryIntentActivities(query, 0)
          .filter { it.activityInfo.packageName != ctx.packageName && seen.add(it.activityInfo.packageName) }
          .map { Pair(it.loadLabel(pm).toString(), it) }
          .sortedBy { it.first.lowercase() }
        val result = Arguments.createArray()
        for ((label, info) in items) {
          val map = Arguments.createMap()
          map.putString("label", label)
          map.putString("packageName", info.activityInfo.packageName)
          val icon = iconToBase64(info.loadIcon(pm))
          if (icon != null) map.putString("icon", icon)
          result.pushMap(map)
        }
        promise.resolve(result)
      } catch (e: Throwable) {
        promise.reject("APPS_READ_FAILED", "No se pudo leer la lista de aplicaciones.", e)
      }
    }.start()
  }

  /** Abre una app por paquete. Resuelve false si no está instalada o no tiene actividad lanzable. */
  @ReactMethod
  fun openApp(packageName: String, promise: Promise) {
    try {
      val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
      if (intent == null) {
        promise.resolve(false)
        return
      }
      launch(intent)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.resolve(false)
    }
  }

  /** Abre la app predeterminada de una categoría: MESSAGING, CALCULATOR, CAMERA, DIALER, MUSIC. */
  @ReactMethod
  fun openCategory(category: String, promise: Promise) {
    try {
      val intent = when (category) {
        "MESSAGING" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        "CALCULATOR" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        "MUSIC" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        // INTENT_ACTION_STILL_IMAGE_CAMERA no exige el permiso CAMERA (ACTION_IMAGE_CAPTURE sí falla si está declarado y denegado).
        "CAMERA" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        "DIALER" -> Intent(Intent.ACTION_DIAL)
        else -> null
      }
      if (intent == null) {
        promise.resolve(false)
        return
      }
      launch(intent)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.resolve(false)
    }
  }

  // ───────────────────────────── Llamadas ─────────────────────────────

  /** Llamada directa si CALL_PHONE está concedido; si no, abre el marcador (ACTION_DIAL). */
  @ReactMethod
  fun callNumber(number: String, promise: Promise) {
    val uri = Uri.fromParts("tel", number, null)
    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    try {
      launch(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
      promise.resolve(if (granted) "call" else "dial")
    } catch (e: SecurityException) {
      try {
        launch(Intent(Intent.ACTION_DIAL, uri))
        promise.resolve("dial")
      } catch (e2: Exception) {
        promise.reject("CALL_FAILED", "No se pudo abrir el teléfono.", e2)
      }
    } catch (e: Exception) {
      promise.reject("CALL_FAILED", "No se pudo abrir el teléfono.", e)
    }
  }


  /** Abre el panel oficial de Android; Wi-Fi/Bluetooth/avión no se fuerzan desde apps normales en Android moderno. */
  @ReactMethod
  fun openSystemPanel(panel: String, promise: Promise) {
    try {
      val action = when (panel) {
        "wifi" -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
        "internet" -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
        "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
        "volume" -> Settings.ACTION_SOUND_SETTINGS
        else -> Settings.ACTION_SETTINGS
      }
      launch(Intent(action))
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("PANEL_FAILED", "No se pudo abrir el control de Android.", e)
    }
  }

  @ReactMethod
  fun speakAndOpenApp(label: String, packageName: String, promise: Promise) {
    try {
      val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
      if (intent == null) { promise.resolve(false); return }
      val launched = java.util.concurrent.atomic.AtomicBoolean(false)
      val run = Runnable {
        if (launched.compareAndSet(false, true)) {
          try { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(intent); promise.resolve(true) }
          catch (e: Exception) { promise.reject("APP_OPEN_FAILED", "No se pudo abrir la aplicación.", e) }
        }
      }
      val handler = Handler(Looper.getMainLooper())
      handler.postDelayed(run, 600)
      if (!SpeechController.speak(label) { handler.removeCallbacks(run); run.run() }) run.run()
    } catch (e: Exception) { promise.reject("APP_OPEN_FAILED", "No se pudo abrir la aplicación.", e) }
  }


  @ReactMethod
  fun getMissedCalls(promise: Promise) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
      promise.resolve(Arguments.createArray())
      return
    }
    try {
      val grouped = linkedMapOf<String, MutableMap<String, Any>>()
      val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE)
      ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, projection,
        "${CallLog.Calls.TYPE} = ?", arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
        "${CallLog.Calls.DATE} DESC")?.use { cursor ->
        val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
        while (cursor.moveToNext()) {
          val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
          if (number.isBlank()) continue
          val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty().ifBlank { number } else number
          val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
          val item = grouped[number]
          if (item == null) grouped[number] = mutableMapOf("number" to number, "name" to name, "count" to 1, "timestamp" to date)
          else item["count"] = (item["count"] as Int) + 1
          if (grouped.size >= 20) break
        }
      }
      val result = Arguments.createArray()
      grouped.values.forEach { item ->
        val map = Arguments.createMap()
        map.putString("number", item["number"] as String)
        map.putString("name", item["name"] as String)
        map.putInt("count", item["count"] as Int)
        map.putDouble("timestamp", (item["timestamp"] as Long).toDouble())
        result.pushMap(map)
      }
      promise.resolve(result)
    } catch (e: SecurityException) { promise.resolve(Arguments.createArray()) }
      catch (e: Exception) { promise.reject("MISSED_CALLS_FAILED", "No se pudieron consultar las llamadas perdidas.", e) }
  }

  @ReactMethod
  fun requestExactAlarmPermission(promise: Promise) {
    try {
      if (Build.VERSION.SDK_INT < 31) { promise.resolve(true); return }
      val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
      if (alarm.canScheduleExactAlarms()) { promise.resolve(true); return }
      val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      ctx.startActivity(intent); promise.resolve(false)
    } catch (e: Exception) { promise.reject("EXACT_ALARM_PERMISSION", "No se pudo abrir el permiso de alarmas exactas.", e) }
  }

  @ReactMethod
  fun requestFullScreenAlarmPermission(promise: Promise) {
    try {
      if (Build.VERSION.SDK_INT < 34) { promise.resolve(true); return }
      val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
      if (manager.canUseFullScreenIntent()) { promise.resolve(true); return }
      val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      ctx.startActivity(intent); promise.resolve(false)
    } catch (e: Exception) { promise.reject("FULL_SCREEN_PERMISSION", "No se pudo abrir el permiso de pantalla completa.", e) }
  }

  @ReactMethod
  fun scheduleMedicineAlarm(id: String, name: String, dose: String, at: Double, photo: String?, daysJson: String, kind: String, promise: Promise) {
    try { promise.resolve(MedicineAlarmReceiver.schedule(ctx, id, name, dose, at.toLong(), photo, daysJson, kind)) }
    catch (e: Exception) { promise.reject("MEDICINE_ALARM_FAILED", "No se pudo programar la alarma.", e) }
  }

  @ReactMethod
  fun cancelMedicineAlarm(id: String, promise: Promise) {
    try { MedicineAlarmReceiver.cancel(ctx, id); promise.resolve(true) }
    catch (e: Exception) { promise.reject("MEDICINE_CANCEL_FAILED", "No se pudo cancelar el recordatorio.", e) }
  }

  @ReactMethod
  fun getMedicineHistory(promise: Promise) {
    try {
      val raw = ctx.getSharedPreferences("feros_medicine", Context.MODE_PRIVATE).getString("history", "[]") ?: "[]"
      val arr = org.json.JSONArray(raw); val result = Arguments.createArray()
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val map = Arguments.createMap()
        map.putString("id", item.optString("id")); map.putString("reminderId", item.optString("reminderId"))
        map.putString("name", item.optString("name")); map.putString("dose", item.optString("dose"))
        map.putDouble("at", item.optLong("at").toDouble()); result.pushMap(map)
      }
      promise.resolve(result)
    } catch (e: Exception) { promise.resolve(Arguments.createArray()) }
  }

  @ReactMethod
  fun shareImageToWhatsApp(uriString: String, promise: Promise) {
    try {
      val uri = Uri.parse(uriString)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage("com.whatsapp")
      }
      if (intent.resolveActivity(ctx.packageManager) == null) { promise.resolve(false); return }
      launch(intent); promise.resolve(true)
    } catch (e: Exception) { promise.reject("WHATSAPP_SHARE_FAILED", "No se pudo compartir la fotografía por WhatsApp.", e) }
  }

  @ReactMethod
  fun getGalleryImages(limit: Int, promise: Promise) {
    val permission = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES" else Manifest.permission.READ_EXTERNAL_STORAGE
    if (ContextCompat.checkSelfPermission(ctx, permission) != PackageManager.PERMISSION_GRANTED) {
      promise.reject("GALLERY_PERMISSION", "Falta permiso para leer fotografías.")
      return
    }
    try {
      val result = Arguments.createArray()
      val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
      ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
        MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { cursor ->
        val idIndex=cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameIndex=cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        var count=0
        while(cursor.moveToNext() && count < limit.coerceIn(1,300)) {
          val imageId=cursor.getLong(idIndex)
          val uri=Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId.toString())
          val map=Arguments.createMap(); map.putString("id", imageId.toString()); map.putString("uri",uri.toString())
          map.putString("name",cursor.getString(nameIndex) ?: "Fotografía"); result.pushMap(map); count++
        }
      }
      promise.resolve(result)
    } catch (e: SecurityException) { promise.reject("GALLERY_PERMISSION", "Android no permitió leer las fotografías.", e) }
      catch (e: Exception) { promise.reject("GALLERY_READ_FAILED", "No se pudo leer la galería.", e) }
  }

  @ReactMethod
  fun getStoredSmsMessages(promise: Promise) {
    try {
      val raw = ctx.getSharedPreferences("feros_sms", Context.MODE_PRIVATE).getString("messages", "[]") ?: "[]"
      val parsed = org.json.JSONArray(raw)
      val result = Arguments.createArray()
      for (i in 0 until parsed.length()) {
        val item = parsed.optJSONObject(i) ?: continue
        val map = Arguments.createMap()
        map.putString("id", item.optString("id"))
        map.putString("sender", item.optString("sender"))
        map.putString("text", item.optString("text"))
        map.putDouble("timestamp", item.optLong("timestamp").toDouble())
        result.pushMap(map)
      }
      promise.resolve(result)
    } catch (e: Exception) { promise.reject("SMS_READ_FAILED", "No se pudieron leer los SMS guardados.", e) }
  }

  // ─────────────────────────── Volumen accesible ─────────────────────────────

  @ReactMethod
  fun getMusicVolume(promise: Promise) {
    try {
      val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
      val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
      val map = Arguments.createMap()
      map.putInt("current", current)
      map.putInt("max", max)
      map.putBoolean("low", current <= (max * 0.25f))
      promise.resolve(map)
    } catch (e: Exception) {
      promise.reject("VOLUME_READ_FAILED", "No se pudo consultar el volumen.", e)
    }
  }

  @ReactMethod
  fun setMusicVolumeMax(promise: Promise) {
    try {
      val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("VOLUME_SET_FAILED", "No se pudo subir el volumen multimedia.", e)
    }
  }

  // ─────────────────────────── Teléfono predeterminado ───────────────────────────

  @ReactMethod
  fun isDefaultDialer(promise: Promise) {
    try {
      val isDefault = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val manager = ctx.getSystemService(RoleManager::class.java)
        manager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
      } else {
        val telecom = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecom.defaultDialerPackage == ctx.packageName
      }
      promise.resolve(isDefault)
    } catch (_: Exception) {
      promise.resolve(false)
    }
  }

  @ReactMethod
  fun requestDefaultDialerRole(promise: Promise) {
    try {
      val activity = ctx.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "Abre FerOs abues para solicitar el teléfono predeterminado.")
        return
      }
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val manager = ctx.getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
          promise.resolve(false)
          return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_DIALER)) {
          promise.resolve(true)
          return
        }
        activity.startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 4102)
      } else {
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
          putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, ctx.packageName)
        }
        activity.startActivity(intent)
      }
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("DIALER_ROLE_FAILED", "No se pudo abrir la solicitud de teléfono predeterminado.", e)
    }
  }

  @ReactMethod
  fun setCallVoiceEnabled(enabled: Boolean, promise: Promise) {
    try {
      ctx.getSharedPreferences("feros_settings", Context.MODE_PRIVATE)
        .edit().putBoolean("call_voice_enabled", enabled).apply()
      promise.resolve(true)
    } catch (_: Exception) {
      promise.resolve(false)
    }
  }

  // ───────────────────────────── Linterna ─────────────────────────────

  @ReactMethod
  fun isFlashAvailable(promise: Promise) {
    promise.resolve(
      ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) && LauncherEvents.torchCameraId() != null
    )
  }

  @ReactMethod
  fun isTorchOn(promise: Promise) {
    promise.resolve(LauncherEvents.torchOn)
  }

  @ReactMethod
  fun setTorch(enabled: Boolean, promise: Promise) {
    val id = LauncherEvents.torchCameraId()
    if (id == null) {
      promise.reject("NO_FLASH", "Este teléfono no tiene linterna.")
      return
    }
    try {
      val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      cm.setTorchMode(id, enabled)
      LauncherEvents.torchOn = enabled
      promise.resolve(enabled)
    } catch (e: CameraAccessException) {
      promise.reject("TORCH_IN_USE", "La linterna está en uso por otra aplicación.", e)
    } catch (e: IllegalArgumentException) {
      promise.reject("NO_FLASH", "Este teléfono no tiene linterna.", e)
    } catch (e: SecurityException) {
      promise.reject("TORCH_DENIED", "Android no permitió usar la linterna.", e)
    } catch (e: Exception) {
      promise.reject("TORCH_FAILED", "No se pudo cambiar la linterna.", e)
    }
  }

  // ─────────────────────── Notificaciones de WhatsApp ───────────────────────

  @ReactMethod
  fun isNotificationAccessGranted(promise: Promise) {
    try {
      promise.resolve(NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName))
    } catch (e: Exception) {
      promise.resolve(false)
    }
  }

  @ReactMethod
  fun openNotificationAccessSettings(promise: Promise) {
    try {
      launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
      promise.resolve(true)
    } catch (e: Exception) {
      try {
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
        promise.resolve(false)
      } catch (e2: Exception) {
        promise.reject("SETTINGS_FAILED", "No se pudo abrir los ajustes de Android.", e2)
      }
    }
  }

  /** Información de la app (desde ahí se activan los "ajustes restringidos" en APK instaladas manualmente). */
  @ReactMethod
  fun openAppSettings(promise: Promise) {
    try {
      launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("SETTINGS_FAILED", "No se pudo abrir los ajustes de la app.", e)
    }
  }

  @ReactMethod
  fun getStoredNotifications(promise: Promise) {
    try {
      promise.resolve(NotificationStore.toArray(NotificationStore.all(ctx)))
    } catch (e: Exception) {
      promise.resolve(Arguments.createArray())
    }
  }

  @ReactMethod
  fun clearStoredNotifications(promise: Promise) {
    NotificationStore.clear(ctx)
    promise.resolve(true)
  }

  // ───────────────────── Pantalla completa / márgenes seguros ─────────────────────

  /** Márgenes en dp: muesca (cutout) + barras de sistema visibles (p. ej. navegación inferior). */
  @ReactMethod
  fun getInsets(promise: Promise) {
    Handler(Looper.getMainLooper()).post {
      try {
        val map = Arguments.createMap()
        val decor = ctx.currentActivity?.window?.decorView
        val root = if (decor != null) ViewCompat.getRootWindowInsets(decor) else null
        val density = ctx.resources.displayMetrics.density.toDouble()
        val i = root?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        map.putDouble("top", (i?.top ?: 0) / density)
        map.putDouble("bottom", (i?.bottom ?: 0) / density)
        map.putDouble("left", (i?.left ?: 0) / density)
        map.putDouble("right", (i?.right ?: 0) / density)
        promise.resolve(map)
      } catch (e: Exception) {
        promise.reject("INSETS_FAILED", "No se pudieron leer los márgenes de pantalla.", e)
      }
    }
  }
}
