package com.viejitos

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MedicineAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val id = intent.getStringExtra("reminder_id") ?: "medicine"
    val name = intent.getStringExtra("medicine_name") ?: "tu medicina"
    val dose = intent.getStringExtra("medicine_dose") ?: ""
    val photo = intent.getStringExtra("medicine_photo")
    val daysJson = intent.getStringExtra("days_json") ?: "[]"
    val channelId = "feros_medicine_alarm"
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
      manager.createNotificationChannel(NotificationChannel(channelId, "Recordatorios de medicinas", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Alarmas de medicinas y citas"
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
      })
    }
    val open = Intent(context, MedicineAlarmActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("reminder_id", id); putExtra("medicine_name", name)
      putExtra("medicine_dose", dose); putExtra("medicine_photo", photo); putExtra("days_json", daysJson)
    }
    val pi = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle("Hora de tomar $name")
      .setContentText(dose)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setFullScreenIntent(pi, true)
      .setContentIntent(pi).setAutoCancel(true).build()
    try { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) } catch (_: SecurityException) {}
    scheduleNext(context, id, name, dose, photo, daysJson)
    try { context.startActivity(open) } catch (_: Exception) {}
  }

  companion object {
    private fun scheduleNext(context: Context, id: String, name: String, dose: String, photo: String?, daysJson: String) {
      try {
        val days = org.json.JSONArray(daysJson)
        if (days.length() == 0) return
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance()
        var found = false
        for (offset in 1..7) {
          target.timeInMillis = now.timeInMillis
          target.add(java.util.Calendar.DAY_OF_YEAR, offset)
          val weekday = target.get(java.util.Calendar.DAY_OF_WEEK) - 1
          var matches = false
          for (i in 0 until days.length()) if (days.optInt(i, -1) == weekday) matches = true
          if (matches) { found = true; break }
        }
        if (found) {
          val prefs = context.getSharedPreferences("feros_medicine", Context.MODE_PRIVATE)
          val times = try { org.json.JSONObject(prefs.getString("times", "{}") ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
          val saved = times.optJSONObject(id)
          if (saved != null) { target.set(java.util.Calendar.HOUR_OF_DAY, saved.optInt("hour", 8)); target.set(java.util.Calendar.MINUTE, saved.optInt("minute", 0)) }
          target.set(java.util.Calendar.SECOND, 0); target.set(java.util.Calendar.MILLISECOND, 0)
          if (target.timeInMillis <= now.timeInMillis) target.add(java.util.Calendar.DAY_OF_YEAR, 7)
          schedule(context, id, name, dose, target.timeInMillis, photo, daysJson)
        }
      } catch (_: Exception) {}
    }

    fun scheduleSnooze(context: Context, id: String, name: String, dose: String, at: Long, photo: String?, daysJson: String) {
      val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      if(Build.VERSION.SDK_INT>=31&&!alarm.canScheduleExactAlarms())return
      val intent=Intent(context,MedicineAlarmReceiver::class.java).apply {
        putExtra("reminder_id",id);putExtra("medicine_name",name);putExtra("medicine_dose",dose)
        putExtra("medicine_photo",photo);putExtra("days_json",daysJson)
      }
      val pi=PendingIntent.getBroadcast(context,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)
    }

    fun cancel(context: Context, id: String) {
      val intent = Intent(context, MedicineAlarmReceiver::class.java)
      val pi = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
      if (pi != null) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        pi.cancel()
      }
      val prefs=context.getSharedPreferences("feros_medicine",Context.MODE_PRIVATE)
      val alarms=try{org.json.JSONObject(prefs.getString("alarms","{}") ?: "{}")}catch(_:Exception){org.json.JSONObject()}
      val times=try{org.json.JSONObject(prefs.getString("times","{}") ?: "{}")}catch(_:Exception){org.json.JSONObject()}
      alarms.remove(id);times.remove(id)
      prefs.edit().putString("alarms",alarms.toString()).putString("times",times.toString()).apply()
    }

    fun schedule(context: Context, id: String, name: String, dose: String, at: Long, photo: String?, daysJson: String = "[]"): Boolean {
      val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) return false
      val clock = java.util.Calendar.getInstance().apply { timeInMillis = at }
      val prefs = context.getSharedPreferences("feros_medicine", Context.MODE_PRIVATE)
      val times = try { org.json.JSONObject(prefs.getString("times", "{}") ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
      times.put(id, org.json.JSONObject().put("hour", clock.get(java.util.Calendar.HOUR_OF_DAY)).put("minute", clock.get(java.util.Calendar.MINUTE)))
      prefs.edit().putString("times", times.toString()).apply()
      val alarms = try { org.json.JSONObject(prefs.getString("alarms", "{}") ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
      alarms.put(id, org.json.JSONObject().put("name", name).put("dose", dose).put("at", at).put("photo", photo).put("days", daysJson))
      prefs.edit().putString("alarms", alarms.toString()).apply()
      val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
        putExtra("reminder_id", id); putExtra("medicine_name", name)
        putExtra("medicine_dose", dose); putExtra("medicine_photo", photo); putExtra("days_json", daysJson)
      }
      val pi = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
      return true
    }
  }
}