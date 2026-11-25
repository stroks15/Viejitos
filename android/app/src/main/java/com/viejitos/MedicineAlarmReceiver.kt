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
      putExtra("medicine_dose", dose); putExtra("medicine_photo", photo)
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
    try { context.startActivity(open) } catch (_: Exception) {}
  }

  companion object {
    fun schedule(context: Context, id: String, name: String, dose: String, at: Long, photo: String?): Boolean {
      val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) return false
      val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
        putExtra("reminder_id", id); putExtra("medicine_name", name)
        putExtra("medicine_dose", dose); putExtra("medicine_photo", photo)
      }
      val pi = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
      return true
    }
  }
}