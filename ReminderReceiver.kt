package com.asem.flashcards

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Osboey reminders", NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) android.app.Notification.Builder(context, CHANNEL) else android.app.Notification.Builder(context)
    builder.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(intent.getStringExtra("title") ?: "Osboey").setContentText(intent.getStringExtra("body") ?: "Review your tasks").setAutoCancel(true).setContentIntent(open)
    manager.notify((intent.getStringExtra("id") ?: System.currentTimeMillis().toString()).hashCode(), builder.build())
  }
  companion object { const val CHANNEL = "osboey-reminders" }
}
