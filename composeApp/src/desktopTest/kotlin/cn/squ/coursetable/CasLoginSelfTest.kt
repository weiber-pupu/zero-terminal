package cn.squ.coursetable

import cn.squ.coursetable.storage.ScheduleCache
import cn.squ.coursetable.sync.ScheduleSyncer
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.Test

/**
 * 自检：验证 登录 → 缓存 → 缓存命中 → 强制重爬 完整链路。
 * 运行：gradle :composeApp:desktopTest --tests "cn.squ.coursetable.CasLoginSelfTest" -PSQU_USER=学号 -PSQU_PASS=密码
 */
class CasLoginSelfTest {

    @Test
    fun loginCacheAndSync() = runBlocking {
        val user = System.getProperty("SQU_USER") ?: error("缺少 -DSQU_USER")
        val pass = System.getProperty("SQU_PASS") ?: error("缺少 -DSQU_PASS")
        val syncer = ScheduleSyncer()

        // 1) 登录并同步（联网）
        val r1 = syncer.loginAndSync(user, pass)
        println("[1] loginAndSync: ${r1.summary()}")

        // 2) 立刻再同步（应命中缓存，零网络）
        val r2 = syncer.sync()
        println("[2] sync (expect cache): ${r2.summary()}")

        // 3) 强制重爬（联网；此时凭据已存，session 失效也能自动重登）
        val r3 = syncer.fetchAndCache()
        println("[3] fetchAndCache: ${r3.summary()}")

        // 4) 缓存状态
        val age = ScheduleCache.cacheAgeMs(Clock.System.now().toEpochMilliseconds())
        println("[4] 缓存年龄: ${age?.div(1000)}s, 学期缓存: ${ScheduleCache.loadSemesters()}")

        check(r2 is ScheduleSyncer.SyncResult.Ok && r2.fromCache) { "第二次同步应命中缓存" }
    }

    private fun ScheduleSyncer.SyncResult.summary(): String = when (this) {
        is ScheduleSyncer.SyncResult.Ok ->
            "Ok(${table.semesterName} ${table.courses.size}门 第${table.currentWeek}周" +
                (if (fromCache) " 缓存" else " 联网") + ")"
        is ScheduleSyncer.SyncResult.NeedCredentials -> "NeedCredentials($message)"
        is ScheduleSyncer.SyncResult.Failed -> "Failed($message)"
    }
}
