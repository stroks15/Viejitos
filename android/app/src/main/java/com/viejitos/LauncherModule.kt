package com.viejitos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
  @Volatile private var speech: TextToSpeech? = null

  init {
    speech = TextToSpeech(ctx) { status -> if (status == TextToSpeech.SUCCESS) speech?.language = Locale("es", "MX") }
    LauncherEvents.reactContext = ctx
    LauncherEvents.ensureTorchTracking(ctx)
  }

  override fun getName(): String = "LauncherModule"

  @ReactMethod
  fun speakSpanish(text: String, promise: Promise) {
    try {
      val engine = speech
      if (engine == null) { promise.resolve(false); return }
      engine.language = Locale("es", "MX")
      engine.speak(text.take(240), TextToSpeech.QUEUE_FLUSH, null, "feros-${System.currentTimeMillis()}")
      promise.resolve(true)
    } catch (e: Exception) { promise.reject("TTS_FAILED", "No se pudo reproducir la voz.", e) }
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
