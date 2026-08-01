package cn.squ.coursetable.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 闹钟触发：课前预警 / 上课开始 / 下课取消 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val stage = intent.getStringExtra(EXTRA_STAGE) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        when (stage) {
            STAGE_END -> IslandNotifier.cancel(context, notifId)
            STAGE_WARN, STAGE_START -> IslandNotifier.post(
                context = context,
                notifId = notifId,
                stage = stage,
                courseName = intent.getStringExtra(EXTRA_NAME).orEmpty(),
                room = intent.getStringExtra(EXTRA_ROOM).orEmpty(),
                teacher = intent.getStringExtra(EXTRA_TEACHER).orEmpty(),
                timeText = intent.getStringExtra(EXTRA_TIME).orEmpty(),
                startMs = intent.getLongExtra(EXTRA_START_MS, 0L),
                endMs = intent.getLongExtra(EXTRA_END_MS, 0L),
            )
        }
    }

    companion object {
        const val ACTION = "cn.squ.coursetable.action.COURSE_REMIND"
        const val STAGE_WARN = "warn"
        const val STAGE_START = "during"
        const val STAGE_END = "end"
        const val EXTRA_STAGE = "stage"
        const val EXTRA_NOTIF_ID = "notifId"
        const val EXTRA_NAME = "name"
        const val EXTRA_ROOM = "room"
        const val EXTRA_TEACHER = "teacher"
        const val EXTRA_TIME = "time"
        const val EXTRA_START_MS = "startMs"
        const val EXTRA_END_MS = "endMs"
    }
}
