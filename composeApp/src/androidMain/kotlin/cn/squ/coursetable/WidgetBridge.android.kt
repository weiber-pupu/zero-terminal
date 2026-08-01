package cn.squ.coursetable

import androidx.glance.appwidget.updateAll
import cn.squ.coursetable.island.CourseReminderScheduler
import cn.squ.coursetable.storage.PlatformStorage
import cn.squ.coursetable.widget.CourseWidget

actual suspend fun notifyCourseWidgetChanged() {
    runCatching { CourseWidget().updateAll(PlatformStorage.appContext) }
    // 课表变化后重排今天的上课提醒（超级岛/焦点通知）
    runCatching { CourseReminderScheduler.scheduleToday(PlatformStorage.appContext) }
}
