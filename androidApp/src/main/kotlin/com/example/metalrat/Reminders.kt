package com.example.metalrat

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * "Üben fällig" notifications. One alarm for the next due bookmark; when it fires, every bookmark
 * that became due since the last check gets a notification, then the next alarm is set.
 * Exact alarms via USE_EXACT_ALARM (fine for a private app outside the Play Store).
 */
object Reminders {
    private const val CHANNEL = "due"
    const val EXTRA_PRACTICE = "practice"

    fun schedule(context: Context, items: List<Bookmark>) {
        val now = System.currentTimeMillis()
        // Start of the "became due since" window for notifyDue: set once, before anything can be due.
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        if (!prefs.contains("last_check")) prefs.edit().putLong("last_check", now).apply()
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        val next = items.mapNotNull { it.practice?.due }.filter { it > now }.minOrNull() ?: return
        // Exact: an inexact alarm may come half an hour late, too much for a 45 min interval.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    /** Notifies bookmarks that became due since the last check (no backlog on the very first run). */
    fun notifyDue(context: Context, items: List<Bookmark>) {
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = prefs.getLong("last_check", now)
        prefs.edit().putLong("last_check", now).apply()
        val due = items.filter { b -> b.practice?.due?.let { it in (since + 1)..now } == true }
        if (due.isEmpty()) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Üben fällig", NotificationManager.IMPORTANCE_DEFAULT),
        )
        for (b in due) {
            val open = PendingIntent.getActivity(
                context, b.id.hashCode(),
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_PRACTICE, b.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val p = b.practice!!
            notifications.notify(
                b.id.hashCode(),
                Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_tile)
                    .setContentTitle("Üben fällig")
                    .setContentText(b.title ?: b.url?.let(Links::displayLink) ?: "Video")
                    .setSubText("${p.count + 1}. Wiederholung")
                    .setContentIntent(open)
                    .addAction(Notification.Action.Builder(null, "Üben", open).build())
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, DueReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** The alarm went off: notify, then the store observer in [MetalRatApp] sets the next alarm. */
class DueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MetalRatApp
        val items = app.store.items.value
        Reminders.notifyDue(context, items)
        Reminders.schedule(context, items)
    }
}

/** Alarms don't survive a reboot: starting the app process re-schedules them (see [MetalRatApp]). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MetalRatApp
        Reminders.notifyDue(context, app.store.items.value)
    }
}
