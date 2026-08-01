package cn.squ.coursetable.model

import kotlinx.serialization.Serializable

/** 学期（来自课表页下拉框 option） */
@Serializable
data class Semester(
    val id: Int,
    val name: String, // 如 "2025-2026-2"
)

/** 一门课的一个排课片段：某几周 + 星期几 + 第几节 + 地点 */
@Serializable
data class CourseSegment(
    val weeks: List<Int>,      // 展开后的周次列表，如 [1,2,3,5,6,8..13]
    val weekday: Int,          // 1=周一 … 7=周日
    val periodStart: Int,      // 起始节次
    val periodEnd: Int,        // 结束节次
    val campus: String,        // 校区，如 "本部"
    val room: String,          // 教室，如 "4-407 智慧教室"
)

/** 一门课程（可含多个排课片段） */
@Serializable
data class Course(
    val id: Long,
    val name: String,          // 真实课程名（course.nameZh）
    val className: String = "", // 教学班名（与课程名不同时才有值，如 "25物联1"）
    val code: String,          // 课程代码
    val teacher: String,       // 教师（多个用 / 连接）
    val department: String = "", // 开课学院
    val property: String = "",  // 必修/选修属性
    val weeksInfo: String = "", // 周次摘要，如 "1~16周"
    val segments: List<CourseSegment>,
)

/** 一份完整课表 */
@Serializable
data class CourseTable(
    val semesterId: Int,
    val semesterName: String,
    val currentWeek: Int,
    val fetchedAtEpochMs: Long,
    val courses: List<Course>,
)

/** 备忘录事件：绑定某周某天的一段时间，显示在课表网格中 */
@Serializable
data class MemoEvent(
    val id: Long,              // 创建时间戳毫秒
    val week: Int,             // 教学周 1..25
    val weekday: Int,          // 1=周一 … 7=周日
    val startMin: Int,         // 当天开始分钟（0..1440）
    val endMin: Int,           // 当天结束分钟
    val title: String,
    val note: String = "",
)

/** 一节小课的真实时间（来自教务 timeTableLayout.courseUnitList） */
@Serializable
data class CourseUnit(
    val indexNo: Int,          // 节次号 1..12
    val startMin: Int,         // 当天开始分钟
    val endMin: Int,           // 当天结束分钟
)
