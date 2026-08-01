package cn.squ.coursetable.network

import cn.squ.coursetable.model.Course
import cn.squ.coursetable.model.CourseTable
import cn.squ.coursetable.model.CourseUnit
import cn.squ.coursetable.model.Semester
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 宿迁学院 PC 端教务（jwgl，正方教务系统）数据仓库。
 *
 * 已实测接口：
 * - GET /student/for-std/course-table           课表页（含 studentIds、学期下拉框）
 * - GET /student/for-std/course-table/get-data  课表 JSON
 *       参数：bizTypeId=2 & semesterId={id} & dataId={studentId}
 * - GET /student/for-std/course-table/semester/{id}/print-data  打印数据
 *       参数：semesterId={id} & hasExperiment=1
 *       其中 studentTableVms[0].timeTableLayout.courseUnitList 为真实节次时间
 *       （startTime/endTime 为 HHMM 整数，如 800=08:00）
 */
class JwglRepository(private val session: SchoolSession) {

    companion object {
        const val BASE = "https://jwgl.squ.edu.cn"
        const val ENTRY = "$BASE/student/home"
        private const val COURSE_TABLE_PAGE = "$BASE/student/for-std/course-table"
        private const val COURSE_TABLE_DATA = "$BASE/student/for-std/course-table/get-data"
        private const val PRINT_DATA = "$BASE/student/for-std/course-table/semester"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ---------- 课表页解析 ----------

    /** studentIds = [91908] → 91908 */
    private fun extractStudentId(html: String): Int =
        Regex("""studentIds\s*=\s*\[(\d+)]""").find(html)
            ?.groupValues?.get(1)?.toInt()
            ?: error("无法从课表页解析 studentId，session 可能已失效")

    /** <option value="145">2025-2026-2 → Semester(145, "2025-2026-2") */
    private fun extractSemesters(html: String): List<Semester> =
        Regex("""<option value="(\d+)">([^<]+)""")
            .findAll(html)
            .map { Semester(it.groupValues[1].toInt(), it.groupValues[2].trim()) }
            .distinctBy { it.id }
            .sortedByDescending { it.id }
            .toList()

    /** session 失效（被弹回统一认证）时抛出 */
    class SessionExpiredException : Exception("教务 session 已失效，需要重新登录")

    suspend fun fetchSemestersAndStudentId(): Pair<List<Semester>, Int> {
        val resp = session.get(COURSE_TABLE_PAGE)
        if ("authserver" in resp.url) throw SessionExpiredException()
        return extractSemesters(resp.body) to extractStudentId(resp.body)
    }

    // ---------- 课表数据 ----------

    @Serializable
    private data class TextEntry(val textZh: String? = null)

    @Serializable
    private data class ScheduleText(val dateTimePlaceText: TextEntry? = null)

    @Serializable
    private data class CourseRef(val nameZh: String? = null, val code: String? = null)

    @Serializable
    private data class DeptRef(val nameZh: String? = null)

    @Serializable
    private data class LessonDto(
        val id: Long,
        val nameZh: String? = null,          // 教学班名（可能是班级名，如 "25物联1"）
        val code: String? = null,
        val course: CourseRef? = null,       // 真实课程名在 course.nameZh
        val openDepartment: DeptRef? = null,
        val courseProperty: DeptRef? = null, // 必修/选修属性
        val scheduleText: ScheduleText? = null,
        val teacherAssignmentString: String? = null,
        val scheduleWeeksInfo: String? = null,
    )

    @Serializable
    private data class CourseTableResponse(
        val lessons: List<LessonDto> = emptyList(),
        val currentWeek: Int = 0,
    )

    suspend fun fetchCourseTable(
        semester: Semester,
        studentId: Int,
    ): CourseTable {
        val resp = session.get(
            COURSE_TABLE_DATA,
            query = mapOf(
                "bizTypeId" to "2",
                "semesterId" to semester.id.toString(),
                "dataId" to studentId.toString(),
            ),
        )
        val dto = json.decodeFromString<CourseTableResponse>(resp.body)
        val courses = dto.lessons.map { lesson ->
            val lessonName = lesson.nameZh?.trim().orEmpty()
            val realName = lesson.course?.nameZh?.trim().orEmpty().ifEmpty { lessonName }
            Course(
                id = lesson.id,
                name = realName,
                className = if (lessonName != realName) lessonName else "",
                code = lesson.course?.code ?: lesson.code.orEmpty(),
                teacher = lesson.teacherAssignmentString.orEmpty(),
                department = lesson.openDepartment?.nameZh.orEmpty(),
                property = lesson.courseProperty?.nameZh.orEmpty(),
                weeksInfo = lesson.scheduleWeeksInfo.orEmpty(),
                segments = ScheduleTextParser.parse(
                    lesson.scheduleText?.dateTimePlaceText?.textZh.orEmpty()
                ),
            )
        }
        return CourseTable(
            semesterId = semester.id,
            semesterName = semester.name,
            currentWeek = dto.currentWeek,
            fetchedAtEpochMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            courses = courses,
        )
    }

    // ---------- 节次时间（真实作息） ----------

    @Serializable
    private data class UnitDto(
        val indexNo: Int,
        val startTime: Int = 0,   // HHMM 整数，如 800=08:00
        val endTime: Int = 0,
    )

    @Serializable
    private data class LayoutDto(val courseUnitList: List<UnitDto> = emptyList())

    @Serializable
    private data class TableVmDto(val timeTableLayout: LayoutDto? = null)

    @Serializable
    private data class PrintDataResponse(val studentTableVms: List<TableVmDto> = emptyList())

    /** 拉真实节次时间；解析失败返回空列表（调用方保留旧值/兜底） */
    suspend fun fetchCourseUnits(semester: Semester): List<CourseUnit> {
        val resp = session.get(
            "$PRINT_DATA/${semester.id}/print-data",
            query = mapOf("semesterId" to semester.id.toString(), "hasExperiment" to "1"),
        )
        if ("authserver" in resp.url) throw SessionExpiredException()
        fun hhmm(t: Int) = (t / 100) * 60 + t % 100
        return runCatching {
            json.decodeFromString<PrintDataResponse>(resp.body)
                .studentTableVms.firstOrNull()
                ?.timeTableLayout?.courseUnitList
                ?.filter { it.startTime > 0 && it.endTime > it.startTime }
                ?.map { CourseUnit(it.indexNo, hhmm(it.startTime), hhmm(it.endTime)) }
                ?.sortedBy { it.indexNo }
                .orEmpty()
        }.getOrDefault(emptyList())
    }
}
