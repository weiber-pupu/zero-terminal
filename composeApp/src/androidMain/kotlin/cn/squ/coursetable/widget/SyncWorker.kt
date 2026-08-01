package cn.squ.coursetable.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.squ.coursetable.notifyCourseWidgetChanged
import cn.squ.coursetable.storage.PlatformStorage
import cn.squ.coursetable.sync.ScheduleSyncer
import java.util.concurrent.TimeUnit

/**
 * 每日课表重爬任务：WorkManager 每 24h 触发一次，
 * 缓存超过 20h 才真实联网（见 ScheduleSyncer.STALE_MS），
 * 成功后刷新桌面小组件。
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        PlatformStorage.init(applicationContext)
        return when (val r = ScheduleSyncer().sync(force = false)) {
            is ScheduleSyncer.SyncResult.Ok -> {
                notifyCourseWidgetChanged()
                Result.success()
            }
            // 网络异常重试；凭据问题不重试（等用户打开 App 处理）
            is ScheduleSyncer.SyncResult.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "daily-course-sync"

        /** 注册每日同步（已存在则保留，App 启动时调用一次即可） */
        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
