package cn.squ.coursetable.storage

import cn.squ.coursetable.model.CourseTable
import cn.squ.coursetable.model.CourseUnit
import cn.squ.coursetable.model.Semester
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本地缓存：课表、学期列表、节次时间、登录凭据、上次同步状态。
 * 全部走 PlatformStorage，双端共享。
 */
object ScheduleCache {

    // 课程名字段修正（course.nameZh）后旧缓存数据失效，换 key 强制重爬
    private const val KEY_TABLE = "course_table_v2.json"
    private const val KEY_SEMESTERS = "semesters.json"
    private const val KEY_CREDENTIALS = "credentials.json"
    private const val KEY_UNITS = "course_units.json"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // ---------- 课表 ----------

    fun saveTable(table: CourseTable) {
        PlatformStorage.writeText(KEY_TABLE, json.encodeToString(table))
    }

    fun loadTable(): CourseTable? =
        PlatformStorage.readText(KEY_TABLE)
            ?.let { runCatching { json.decodeFromString<CourseTable>(it) }.getOrNull() }

    // ---------- 学期列表 ----------

    fun saveSemesters(semesters: List<Semester>) {
        PlatformStorage.writeText(KEY_SEMESTERS, json.encodeToString(semesters))
    }

    fun loadSemesters(): List<Semester> =
        PlatformStorage.readText(KEY_SEMESTERS)
            ?.let { runCatching { json.decodeFromString<List<Semester>>(it) }.getOrNull() }
            .orEmpty()

    // ---------- 节次时间（真实作息） ----------

    fun saveUnits(units: List<CourseUnit>) {
        PlatformStorage.writeText(KEY_UNITS, json.encodeToString(units))
    }

    fun loadUnits(): List<CourseUnit> =
        PlatformStorage.readText(KEY_UNITS)
            ?.let { runCatching { json.decodeFromString<List<CourseUnit>>(it) }.getOrNull() }
            .orEmpty()

    // ---------- 凭据 ----------
    // 桌面端先明文落盘（仅本机自用）；Android 端后续 M3 换 EncryptedSharedPreferences。

    @Serializable
    data class Credentials(val username: String, val password: String)

    fun saveCredentials(c: Credentials) {
        PlatformStorage.writeText(KEY_CREDENTIALS, json.encodeToString(c))
    }

    fun loadCredentials(): Credentials? =
        PlatformStorage.readText(KEY_CREDENTIALS)
            ?.let { runCatching { json.decodeFromString<Credentials>(it) }.getOrNull() }

    fun clearCredentials() = PlatformStorage.delete(KEY_CREDENTIALS)

    // ---------- 过期判断 ----------

    /** 课表数据距今年数；null 表示没有缓存 */
    fun cacheAgeMs(nowMs: Long): Long? =
        loadTable()?.let { nowMs - it.fetchedAtEpochMs }
}
