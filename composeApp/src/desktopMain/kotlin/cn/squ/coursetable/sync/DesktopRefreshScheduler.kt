package cn.squ.coursetable.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 桌面端定时重爬调度：启动时同步一次（过期才联网），之后每 6 小时检查一次。
 * 检查本身零网络开销（缓存未过期直接返回），只有过期才真正重爬。
 * Android 端对应物是 WorkManager（M3 实现）。
 */
object DesktopRefreshScheduler {

    private const val CHECK_INTERVAL_MS = 6L * 3600 * 1000

    fun start(
        syncer: ScheduleSyncer,
        onResult: (ScheduleSyncer.SyncResult) -> Unit,
    ) = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            onResult(syncer.sync())
            delay(CHECK_INTERVAL_MS)
        }
    }
}
