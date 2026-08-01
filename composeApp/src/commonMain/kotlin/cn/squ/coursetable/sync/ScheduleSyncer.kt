package cn.squ.coursetable.sync

import cn.squ.coursetable.model.CourseTable
import cn.squ.coursetable.model.Semester
import cn.squ.coursetable.network.CasAuth
import cn.squ.coursetable.network.JwglRepository
import cn.squ.coursetable.network.SchoolSession
import cn.squ.coursetable.network.createPlatformHttpClient
import cn.squ.coursetable.storage.ScheduleCache
import cn.squ.coursetable.ui.Periods
import kotlinx.datetime.Clock

/**
 * 课表同步编排器：登录（含失效重登）→ 拉取 → 缓存。
 *
 * 过期策略：缓存超过 [STALE_MS]（默认 20 小时）视为过期；
 * 每天由平台调度器（desktop 定时器 / android WorkManager）触发 sync()。
 */
class ScheduleSyncer {

    companion object {
        const val STALE_MS = 20L * 3600 * 1000
    }

    sealed class SyncResult {
        /** 成功拿到课表（fromCache=true 表示未过期直接用缓存） */
        data class Ok(val table: CourseTable, val fromCache: Boolean) : SyncResult()
        data class NeedCredentials(val message: String) : SyncResult()
        data class Failed(val message: String) : SyncResult()
        /** CAS 触发图形验证码：附带图片字节，登录页需展示并收集输入 */
        data class NeedCaptcha(val imageBytes: ByteArray?, val message: String) : SyncResult()
    }

    /** 本地有未过期缓存则直接用，否则联网同步 */
    suspend fun sync(force: Boolean = false): SyncResult {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force) {
            val age = ScheduleCache.cacheAgeMs(now)
            val cached = ScheduleCache.loadTable()
            if (cached != null && age != null && age < STALE_MS) {
                return SyncResult.Ok(cached, fromCache = true)
            }
        }
        return fetchAndCache()
    }

    /** 强制联网拉取（session 失效时自动用保存的凭据重登一次） */
    suspend fun fetchAndCache(): SyncResult {
        val credentials = ScheduleCache.loadCredentials()
            ?: return SyncResult.NeedCredentials("请先登录教务系统")

        val client = createPlatformHttpClient()
        try {
            val session = SchoolSession(client)
            val repo = JwglRepository(session)

            suspend fun doFetch(): CourseTable {
                val (semesters, studentId) = repo.fetchSemestersAndStudentId()
                ScheduleCache.saveSemesters(semesters)
                val sem = pickCurrentSemester(semesters)
                // 真实节次时间（失败保留旧缓存/兜底）
                repo.fetchCourseUnits(sem).takeIf { it.isNotEmpty() }?.let {
                    ScheduleCache.saveUnits(it)
                    Periods.install(it)
                }
                return repo.fetchCourseTable(sem, studentId)
            }

            val table = try {
                doFetch()
            } catch (e: JwglRepository.SessionExpiredException) {
                // session 失效：重登一次再试
                when (val r = CasAuth(session).login(
                    JwglRepository.ENTRY, credentials.username, credentials.password
                )) {
                    is CasAuth.Result.Success -> doFetch()
                    is CasAuth.Result.Failed ->
                        return SyncResult.NeedCredentials("自动重登失败：${r.reason}")
                    is CasAuth.Result.NeedCaptcha ->
                        return SyncResult.NeedCredentials("自动重登触发验证码，请打开 App 重新登录")
                }
            }

            ScheduleCache.saveTable(table)
            return SyncResult.Ok(table, fromCache = false)
        } catch (e: Exception) {
            return SyncResult.Failed(e.message ?: e.toString())
        } finally {
            client.close()
        }
    }

    /** 首次登录：验证凭据并保存，然后立即同步；captcha 为 NeedCaptcha 后用户输入的验证码 */
    suspend fun loginAndSync(username: String, password: String, captcha: String? = null): SyncResult {
        val client = createPlatformHttpClient()
        try {
            val session = SchoolSession(client)
            when (val r = CasAuth(session).login(JwglRepository.ENTRY, username, password, captcha)) {
                is CasAuth.Result.Failed -> return SyncResult.NeedCredentials(r.reason)
                is CasAuth.Result.NeedCaptcha ->
                    return SyncResult.NeedCaptcha(r.imageBytes, r.reason)
                is CasAuth.Result.Success -> {
                    ScheduleCache.saveCredentials(
                        ScheduleCache.Credentials(username, password)
                    )
                    val repo = JwglRepository(session)
                    val (semesters, studentId) = repo.fetchSemestersAndStudentId()
                    ScheduleCache.saveSemesters(semesters)
                    val sem = pickCurrentSemester(semesters)
                    repo.fetchCourseUnits(sem).takeIf { it.isNotEmpty() }?.let {
                        ScheduleCache.saveUnits(it)
                        Periods.install(it)
                    }
                    val table = repo.fetchCourseTable(sem, studentId)
                    ScheduleCache.saveTable(table)
                    return SyncResult.Ok(table, fromCache = false)
                }
            }
        } catch (e: Exception) {
            return SyncResult.Failed(e.message ?: e.toString())
        } finally {
            client.close()
        }
    }

    /**
     * 选当前学期：学期按 id 降序，取第二个（最新的是下学期预排，第二个是当前）。
     * TODO: 用课表页的默认选中项替代启发式规则
     */
    private fun pickCurrentSemester(semesters: List<Semester>): Semester =
        semesters.getOrElse(1) { semesters.first() }
}
