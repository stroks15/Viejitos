package com.viejitos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Recibe SMS nuevos y conserva una bandeja local pequeña para la interfaz accesible. */
class SmsReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    try {
      val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
      if (messages.isEmpty()) return
      val grouped = linkedMapOf<String, String>()
      messages.forEach { sms ->
        val sender = sms.originatingAddress ?: "Remitente desconocido"
        grouped[sender] = (grouped[sender].orEmpty() + sms.messageBody.orEmpty())
      }
      val prefs = context.getSharedPreferences("feros_sms", Context.MODE_PRIVATE)
      val old = JSONArray(prefs.getString("messages", "[]") ?: "[]")
      val next = JSONArray()
      for (i in 0 until minOf(old.length(), 79)) next.put(old.getJSONObject(i))
      grouped.forEach { (sender, body) ->
        next.put(JSONObject().put("id", UUID.randomUUID().toString()).put("sender", sender)
          .put("text", body.take(4000)).put("timestamp", System.currentTimeMillis()))
      }
      prefs.edit().putString("messages", next.toString()).apply()
    } catch (_: Exception) { }
  }
}
