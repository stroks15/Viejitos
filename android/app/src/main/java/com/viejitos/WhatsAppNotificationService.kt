package com.viejitos

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import org.json.JSONArray
import org.json.JSONObject

/** Guarda localmente (SharedPreferences) las notificaciones recientes de aplicaciones importantes. */
object NotificationStore {
  private const val PREFS = "device_notifications"
  private const val KEY = "items"
  private const val MAX_ITEMS = 30

  @Synchronized
  fun all(context: Context): JSONArray {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
      ?: context.getSharedPreferences("wa_notifications", Context.MODE_PRIVATE).getString(KEY, null)
    return try { if (raw.isNullOrEmpty()) JSONArray() else JSONArray(raw) } catch (_: Exception) { JSONArray() }
  }

  @Synchronized
  fun add(context: Context, item: JSONObject) {
    val current = all(context)
    val next = JSONArray()
    next.put(item)
    for (i in 0 until current.length()) {
      val old = current.optJSONObject(i) ?: continue
      if (old.optString("key") == item.optString("key")) continue
      if (next.length() >= MAX_ITEMS) break
      next.put(old)
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.toString()).apply()
  }

  @Synchronized
  fun clear(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
  }

  fun toMap(item: JSONObject): WritableMap {
    val map = Arguments.createMap()
    map.putString("key", item.optString("key"))
    map.putString("packageName", item.optString("packageName"))
    map.putString("sender", item.optString("sender"))
    map.putString("appName", item.optString("appName"))
    map.putString("text", item.optString("text"))
    map.putDouble("time", item.optLong("time").toDouble())
    return map
  }

  fun toArray(items: JSONArray): WritableArray {
    val out = Arguments.createArray()
    for (i in 0 until items.length()) {
      val item = items.optJSONObject(i) ?: continue
      out.pushMap(toMap(item))
    }
    return out
  }
}

class WhatsAppNotificationService : NotificationListenerService() {

  companion object {
    val IGNORED = setOf("android", "com.android.systemui", "com.google.android.googlequicksearchbox")
    // Avisos técnicos de WhatsApp que no son mensajes.
    private val NOISE = Regex(
      "comprobando|verificando|checking for new|\\d+\\s+(mensajes?|messages?)\\s+(de|from)\\s+\\d+\\s+(chats?|conversaciones|conversations)",
      RegexOption.IGNORE_CASE
    )
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    try {
      activeNotifications?.sortedBy { it.postTime }?.forEach { handle(it, false) }
    } catch (_: Exception) {
    }
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn != null) handle(sbn, true)
  }

  private fun handle(sbn: StatusBarNotification, emit: Boolean) {
    try {
      if (sbn.packageName in IGNORED || sbn.packageName == packageName) return
      val n = sbn.notification ?: return
      if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
      if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
      val extras = n.extras ?: return
      val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
      val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
        ?.toString()?.trim().orEmpty()
      if (title.isEmpty() && text.isEmpty()) return
      if (NOISE.containsMatchIn(text) || NOISE.containsMatchIn(title)) return

      val item = JSONObject()
      item.put("key", sbn.key ?: "${sbn.packageName}:${sbn.id}:${sbn.tag}")
      item.put("packageName", sbn.packageName)
      item.put("appName", try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (_: Exception) { sbn.packageName })
      item.put("sender", title)
      item.put("text", text)
      item.put("time", sbn.postTime)
      NotificationStore.add(applicationContext, item)
      if (emit) LauncherEvents.emit("DeviceNotification", NotificationStore.toMap(item))
    } catch (_: Exception) {
    }
  }
}
