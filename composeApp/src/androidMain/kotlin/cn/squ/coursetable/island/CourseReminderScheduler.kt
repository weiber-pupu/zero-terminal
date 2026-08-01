package cn.squ.coursetable.island

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import cn.squ.coursetable.storage.ScheduleCache
import cn.squ.coursetable.ui.Periods
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar

/**
 * 上课提醒调度（精简版）：为今天的每节课只排一个闹钟，
 * 上课开始时弹出超级岛/焦点通知（通知 20 分钟后自动消失，不常驻）。
 * 课表同步成功 / 每日 WorkManager / App 启动 / 开机 后重排。
 */
object CourseReminderScheduler {

    private const val NOTIF_ID_BASE = 7000

    /** 重排今天的全部提醒（先清后排） */
    fun scheduleToday(context: Context) {
        cancelAll(context)
        val table = ScheduleCache.loadTable() ?: return
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val todayWd = now.dayOfWeek.ordinal + 1
        val week = table.currentWeek
        val nowMs = System.currentTimeMillis()
        val dayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        table.courses.forEach { c ->
            c.segments.forEach { s ->
                if (s.weekday != todayWd || week !in s.weeks) return@forEach
                val startMs = dayStartMs + Periods.toMin(Periods.start(s.periodStart)) * 60000L
                val endMs = dayStartMs + Periods.toMin(Periods.end(s.periodEnd)) * 60000L
                if (endMs <= nowMs) return@forEach // 已下课

                val notifId = NOTIF_ID_BASE + s.weekday * 20 + s.periodStart
                val timeText = "${Periods.start(s.periodStart)}~${Periods.end(s.periodEnd)}"
                // 还没到点开始：准点触发；正在上课：2 秒后补弹
                val atMs = if (nowMs < startMs) startMs else nowMs + 2000
                alarm(context, notifId, c.name, s.room, c.teacher, timeText, startMs, endMs, atMs)
            }
        }
    }

    /** 取消今天所有提醒闹钟与通知 */
    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        for (wd in 1..7) for (p in 1..12) {
            val id = NOTIF_ID_BASE + wd * 20 + p
            am.cancel(pending(context, id, "", "", "", "", 0, 0))
            IslandNotifier.cancel(context, id)
        }
    }

    private fun alarm(
        context: Context,
        notifId: Int,
        name: String,
        room: String,
        teacher: String,
        timeText: String,
        startMs: Long,
        endMs: Long,
        atMs: Long,
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, notifId, name, room, teacher, timeText, startMs, endMs)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } catch (e: SecurityException) {
            // 用户关掉了精确闹钟权限：退化为非精确闹钟
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    private fun pending(
        context: Context,
        notifId: Int,
        name: String,
        room: String,
        teacher: String,
        timeText: String,
        startMs: Long,
        endMs: Long,
    ): PendingIntent {
        val intent = Intent(ReminderReceiver.ACTION).apply {
            setPackage(context.packageName)
            putExtra(ReminderReceiver.EXTRA_STAGE, ReminderReceiver.STAGE_START)
            putExtra(ReminderReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(ReminderReceiver.EXTRA_NAME, name)
            putExtra(ReminderReceiver.EXTRA_ROOM, room)
            putExtra(ReminderReceiver.EXTRA_TEACHER, teacher)
            putExtra(ReminderReceiver.EXTRA_TIME, timeText)
            putExtra(ReminderReceiver.EXTRA_START_MS, startMs)
            putExtra(ReminderReceiver.EXTRA_END_MS, endMs)
        }
        return PendingIntent.getBroadcast(
            context, notifId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
