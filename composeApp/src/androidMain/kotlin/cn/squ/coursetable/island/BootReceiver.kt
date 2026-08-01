package cn.squ.coursetable.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.squ.coursetable.storage.PlatformStorage

/** 开机后重排今天的上课提醒 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching {
            PlatformStorage.init(context.applicationContext)
            CourseReminderScheduler.scheduleToday(context.applicationContext)
        }
    }
}
