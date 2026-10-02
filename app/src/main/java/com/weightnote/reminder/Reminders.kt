package com.weightnote.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.weightnote.MainActivity
import com.weightnote.R
import com.weightnote.WeightNoteApp
import com.weightnote.data.db.AppDatabase
import com.weightnote.data.db.MetricKeys
import com.weightnote.data.db.ReminderEntity
import com.weightnote.data.epochMillisOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** 用 AlarmManager 为每个提醒安排“下一次”触发；触发后再安排下一天 */
class ReminderScheduler(private val context: Context, private val db: AppDatabase) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val reminders = db.groupDao().getAllReminders().filter { it.enabled }
        val newIds = reminders.map { it.id }.toSet()
        val oldIds = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        oldIds.filter { it !in newIds }.forEach { cancel(it) }
        reminders.forEach { schedule(it) }
        prefs.edit().putStringSet(KEY_SCHEDULED, newIds.map { it.toString() }.toSet()).apply()
    }

    fun schedule(reminder: ReminderEntity) {
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(reminder.minuteOfDay / 60, reminder.minuteOfDay % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillisOf(at), pendingIntent(reminder.id))
    }

    private fun cancel(id: Long) = alarmManager.cancel(pendingIntent(id))

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_REMINDER_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val KEY_SCHEDULED = "scheduled_ids"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}

object Notifications {
    const val CHANNEL_REMINDER = "reminder"
    const val EXTRA_PROFILE_ID = "open_profile_id"
    const val EXTRA_GROUP_ID = "open_group_id"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(CHANNEL_REMINDER, "记录提醒", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "按分组设置的称重提醒" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context, title: String, text: String, profileId: Long, groupId: Long, notifyId: Int) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PROFILE_ID, profileId)
            .putExtra(EXTRA_GROUP_ID, groupId)
        val content = PendingIntent.getActivity(
            context, notifyId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifyId, notification)
        } catch (_: SecurityException) {
            // 权限在检查后被撤销，忽略
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        if (id < 0) return
        val container = (context.applicationContext as WeightNoteApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = container.database
                val reminder = db.groupDao().getReminder(id) ?: return@launch
                if (!reminder.enabled) return@launch
                // 先安排下一次，避免后续出错导致提醒中断
                container.reminderScheduler.schedule(reminder)
                val group = db.groupDao().get(reminder.groupId) ?: return@launch
                val profile = db.profileDao().get(group.profileId) ?: return@launch
                val weight = db.metricDao().getByKey(profile.id, MetricKeys.WEIGHT) ?: return@launch
                val recorded = container.repository.hasRecordToday(group.id, weight.id)
                if (!recorded) {
                    val multiProfile = db.profileDao().getAll().size > 1
                    val who = if (multiProfile) "${profile.name} · " else ""
                    Notifications.showReminder(
                        context,
                        title = "该称体重啦",
                        text = "${who}「${group.name}」今天还没有记录",
                        profileId = profile.id,
                        groupId = group.id,
                        notifyId = group.id.toInt(),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WeightNoteApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminderScheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
