package cn.squ.coursetable.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.squ.coursetable.model.CourseTable
import cn.squ.coursetable.model.MemoEvent
import cn.squ.coursetable.notifyCourseWidgetChanged
import cn.squ.coursetable.storage.MemoStore
import cn.squ.coursetable.storage.PlatformStorage
import cn.squ.coursetable.storage.ScheduleCache
import cn.squ.coursetable.sync.ScheduleSyncer
import cn.squ.coursetable.ui.Periods
import cn.squ.coursetable.ui.state.LifeServicesState

/**
 * 全局 UI 状态：课表、当前周、大节/小节、深浅主题、同步状态。
 * 主题偏好持久化在 PlatformStorage（key=pref_theme）。
 */
class AppState {

    private val syncer = ScheduleSyncer()

    /** 生活服务（校园卡余额 / 电费） */
    val life = LifeServicesState()

    var table by mutableStateOf<CourseTable?>(null); private set
    var week by mutableIntStateOf(1)
    var blockMode by mutableStateOf(true)
    var darkTheme by mutableStateOf(PlatformStorage.readText(KEY_THEME) != "light")
    var syncing by mutableStateOf(false); private set
    var statusMsg by mutableStateOf<String?>(null)
    /** CAS 触发图形验证码时的图片字节；null 表示无需验证码 */
    var captchaImage by mutableStateOf<ByteArray?>(null); private set
    /** 备忘录事件（全量，按 week+weekday 过滤使用） */
    var memos by mutableStateOf<List<MemoEvent>>(emptyList()); private set

    /** 某周某天的备忘录，按开始时间排序 */
    fun memosOf(week: Int, weekday: Int): List<MemoEvent> =
        memos.filter { it.week == week && it.weekday == weekday }.sortedBy { it.startMin }

    fun addMemo(memo: MemoEvent) {
        memos = (memos + memo).also { MemoStore.saveAll(it) }
    }

    fun updateMemo(memo: MemoEvent) {
        memos = memos.map { if (it.id == memo.id) memo else it }.also { MemoStore.saveAll(it) }
    }

    fun deleteMemo(id: Long) {
        memos = memos.filter { it.id != id }.also { MemoStore.saveAll(it) }
    }

    val hasCredentials: Boolean get() = ScheduleCache.loadCredentials() != null

    val maxPeriod: Int
        get() = maxOf(
            Periods.maxUnit,
            table?.courses?.flatMap { c -> c.segments.map { it.periodEnd } }?.maxOrNull() ?: Periods.maxUnit,
        )

    /** 回到教务系统给出的当前周 */
    fun goCurrentWeek() {
        table?.let { week = it.currentWeek.coerceIn(1, 25) }
    }

    fun toggleTheme() {
        darkTheme = !darkTheme
        PlatformStorage.writeText(KEY_THEME, if (darkTheme) "dark" else "light")
    }

    fun changeWeek(delta: Int) {
        week = (week + delta).coerceIn(1, 25)
    }

    /** 启动：先读缓存，缓存过期且有凭据则后台自动重爬 */
    suspend fun bootstrap() {
        Periods.loadFromCache()
        memos = MemoStore.loadAll()
        val cached = ScheduleCache.loadTable()
        if (cached != null) adoptTable(cached)
        if (cached == null && !hasCredentials) return // 留在登录页
        val stale = ScheduleCache.cacheAgeMs(
            kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )?.let { it >= ScheduleSyncer.STALE_MS } ?: true
        if (stale && hasCredentials) refresh(force = true)
        // 生活服务后台静默刷新（校园卡/电费）
        runCatching { life.bootstrap() }
    }

    suspend fun login(username: String, password: String, captcha: String? = null) {
        syncing = true
        statusMsg = "正在登录 CAS…"
        when (val r = syncer.loginAndSync(username, password, captcha)) {
            is ScheduleSyncer.SyncResult.Ok -> {
                adoptTable(r.table)
                captchaImage = null
                statusMsg = "✓ 已同步 ${r.table.courses.size} 门课"
                notifyCourseWidgetChanged()
            }
            is ScheduleSyncer.SyncResult.NeedCredentials -> statusMsg = "✗ ${r.message}"
            is ScheduleSyncer.SyncResult.Failed -> statusMsg = "✗ ${r.message}"
            is ScheduleSyncer.SyncResult.NeedCaptcha -> {
                captchaImage = r.imageBytes
                statusMsg = "✗ ${r.message}"
            }
        }
        syncing = false
    }

    suspend fun refresh(force: Boolean = true) {
        if (syncing) return
        syncing = true
        statusMsg = "SYNCING…"
        when (val r = syncer.sync(force)) {
            is ScheduleSyncer.SyncResult.Ok -> {
                adoptTable(r.table)
                statusMsg = if (r.fromCache) "缓存未过期" else "✓ 已同步 / SYNCED"
                if (!r.fromCache) notifyCourseWidgetChanged()
            }
            is ScheduleSyncer.SyncResult.NeedCredentials -> statusMsg = "✗ ${r.message}"
            is ScheduleSyncer.SyncResult.Failed -> statusMsg = "✗ ${r.message}"
            is ScheduleSyncer.SyncResult.NeedCaptcha -> {
                captchaImage = r.imageBytes
                statusMsg = "✗ ${r.message}"
            }
        }
        syncing = false
    }

    private fun adoptTable(t: CourseTable) {
        table = t
        // 周初始化（与 mockup 一致）：当前周没课则落在课最多的周
        val count = mutableMapOf<Int, Int>()
        t.courses.forEach { c -> c.segments.forEach { s -> s.weeks.forEach { w -> count[w] = (count[w] ?: 0) + 1 } } }
        val best = count.maxByOrNull { it.value }?.key
        week = when {
            (count[t.currentWeek] ?: 0) > 0 -> t.currentWeek
            best != null -> best
            else -> t.currentWeek
        }.coerceIn(1, 25)
    }

    companion object {
        private const val KEY_THEME = "pref_theme"
    }
}
