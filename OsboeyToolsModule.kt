package com.asem.flashcards

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.Calendar

class OsboeyToolsModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  private var pendingExport: String? = null
  private var pendingPromise: Promise? = null
  override fun getName() = "OsboeyTools"

  private val listener: ActivityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: android.app.Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
      if (resultCode != android.app.Activity.RESULT_OK || data?.data == null) {
        pendingPromise?.resolve(false); pendingPromise = null; pendingExport = null; return
      }
      try {
        if (requestCode == EXPORT_REQUEST) {
          context.contentResolver.openOutputStream(data.data!!)?.bufferedWriter()?.use { it.write(pendingExport ?: "") }
          emit("OsboeyNativeResult", mapOf("ok" to true, "message" to "Backup saved"))
        } else if (requestCode == IMPORT_REQUEST) {
          val text = context.contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use { it.readText() } ?: ""
          context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit("OsboeyBackupImported", text)
        }
        pendingPromise?.resolve(true)
      } catch (error: Exception) {
        pendingPromise?.reject("OSBOEY_FILE", error)
        emit("OsboeyNativeResult", mapOf("ok" to false, "message" to "Could not read or save the file"))
      } finally { pendingPromise = null; pendingExport = null }
    }
  }

  init { context.addActivityEventListener(listener) }

  private fun emit(event: String, value: Map<String, Any>) {
    val map = Arguments.createMap()
    value.forEach { (key, item) ->
      when (item) {
        is Boolean -> map.putBoolean(key, item)
        is Number -> map.putDouble(key, item.toDouble())
        else -> map.putString(key, item.toString())
      }
    }
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(event, map)
  }

  @ReactMethod fun exportBackup(json: String, promise: Promise) {
    val activity = currentActivity ?: return promise.reject("NO_ACTIVITY", "No active screen")
    pendingExport = json; pendingPromise = promise
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
      putExtra(Intent.EXTRA_TITLE, "Osboey-backup-${System.currentTimeMillis()}.json")
    }
    activity.startActivityForResult(intent, EXPORT_REQUEST)
  }

  @ReactMethod fun importBackup(promise: Promise) {
    val activity = currentActivity ?: return promise.reject("NO_ACTIVITY", "No active screen")
    pendingPromise = promise
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }
    activity.startActivityForResult(intent, IMPORT_REQUEST)
  }

  private fun reminderIntent(id: String, title: String = "Osboey", body: String = "Review your tasks today"): PendingIntent {
    val intent = Intent(context, ReminderReceiver::class.java).apply { putExtra("id", id); putExtra("title", title); putExtra("body", body) }
    return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  @ReactMethod fun scheduleReminder(id: String, at: Double, title: String, body: String, promise: Promise) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toLong().coerceAtLeast(System.currentTimeMillis() + 1000), reminderIntent(id, title, body))
    promise.resolve(true)
  }

  @ReactMethod fun cancelReminder(id: String, promise: Promise) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.cancel(reminderIntent(id)); promise.resolve(true)
  }

  @ReactMethod fun setDailyReminder(enabled: Boolean, hour: Double, minute: Double, promise: Promise) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = reminderIntent(DAILY_ID, "Osboey", "Good morning — review your tasks for today")
    alarm.cancel(intent)
    if (enabled) {
      val whenToNotify = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour.toInt()); set(Calendar.MINUTE, minute.toInt()); set(Calendar.SECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1) }
      alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, whenToNotify.timeInMillis, AlarmManager.INTERVAL_DAY, intent)
    }
    promise.resolve(true)
  }

  companion object { const val EXPORT_REQUEST = 6401; const val IMPORT_REQUEST = 6402; const val DAILY_ID = "osboey-daily" }
}
