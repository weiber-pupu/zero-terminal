package cn.squ.coursetable.network

import cn.squ.coursetable.model.CourseSegment

/**
 * 正方教务排课文本解析器。
 *
 * 支持两种格式：
 *
 * 1. 标准格式（多段用 ";" 或换行分隔）：
 *    "1~16周 星期五 5~6节 本部 T107 田径场"
 *
 * 2. 实践周格式（全周连排，用连续空格分隔，地点常在后一个 token）：
 *    "17周 星期一  1~4节  星期一 本部 X101 实验室(一) 5~8节  星期二  1~4节 ..."
 */
object ScheduleTextParser {

    private val WEEKDAY_MAP = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7,
    )

    // 标准段：1~3,5~6,8~13周 星期三 1~2节 本部 4-407 智慧教室
    private val SEGMENT_REGEX = Regex(
        """([\d,~\-]+)周\s*星期([一二三四五六日天])\s*(\d+)(?:~(\d+))?节\s*(.*)"""
    )

    // 实践周 token 类型
    private val P_WEEKDAY = Regex("""^(?:([\d,~\-]+)周\s+)?星期([一二三四五六日天])$""")
    private val P_PERIODS = Regex("""^(\d+)(?:~(\d+))?节$""")
    private val P_FULL =
        Regex("""^星期([一二三四五六日天])\s+(.+?)\s+(\d+)(?:~(\d+))?节$""")

    /** 把 "1~3,5~6,8~13" 展开为 [1,2,3,5,6,8,9,10,11,12,13] */
    fun parseWeeks(expr: String): List<Int> {
        val weeks = sortedSetOf<Int>()
        for (part in expr.split(",")) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val range = p.split("~", "-").mapNotNull { it.trim().toIntOrNull() }
            when (range.size) {
                1 -> weeks.add(range[0])
                2 -> if (range[0] <= range[1]) weeks.addAll(range[0]..range[1])
            }
        }
        return weeks.toList()
    }

    /** 解析整段排课文本为排课片段列表 */
    fun parse(textZh: String): List<CourseSegment> {
        val standard = parseStandard(textZh)
        // 标准解析把地点吞进了下一段（room 里出现"星期"）说明是实践周格式
        return if (standard.any { "星期" in it.room }) parsePracticeWeek(textZh) else standard
    }

    private fun splitPlace(placeRaw: String): Pair<String, String> {
        val parts = placeRaw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return parts.getOrElse(0) { "" } to parts.drop(1).joinToString(" ")
    }

    private fun parseStandard(textZh: String): List<CourseSegment> {
        return textZh.split(";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { seg ->
                val m = SEGMENT_REGEX.find(seg) ?: return@mapNotNull null
                val (weekExpr, weekdayZh, startStr, endStr, placeRaw) = m.destructured
                val (campus, room) = splitPlace(placeRaw)
                CourseSegment(
                    weeks = parseWeeks(weekExpr),
                    weekday = WEEKDAY_MAP[weekdayZh] ?: return@mapNotNull null,
                    periodStart = startStr.toInt(),
                    periodEnd = endStr.ifEmpty { startStr }.toInt(),
                    campus = campus,
                    room = room,
                )
            }
    }

    /**
     * 实践周格式状态机：
     * - "17周 星期一" / "星期二"          → 设定当前周次/星期
     * - "1~4节"                          → 暂存节次（地点取下一个完整 token 的）
     * - "星期一 本部 X101 实验室(一) 5~8节" → 完整段；若同星期有暂存节次则先补发
     */
    private fun parsePracticeWeek(textZh: String): List<CourseSegment> {
        val result = mutableListOf<CourseSegment>()
        val tokens = textZh.split(Regex("\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }

        var weeks = emptyList<Int>()
        var weekday = 0
        var pendingPeriods: Pair<Int, Int>? = null

        for (token in tokens) {
            P_WEEKDAY.matchEntire(token)?.let { m ->
                m.groupValues[1].takeIf { it.isNotEmpty() }?.let { weeks = parseWeeks(it) }
                weekday = WEEKDAY_MAP[m.groupValues[2]] ?: weekday
                return@let
            }
            P_PERIODS.matchEntire(token)?.let { m ->
                pendingPeriods =
                    m.groupValues[1].toInt() to (m.groupValues[2].ifEmpty { m.groupValues[1] }).toInt()
                return@let
            }
            P_FULL.matchEntire(token)?.let { m ->
                val fullWeekday = WEEKDAY_MAP[m.groupValues[1]] ?: return@let
                val place = m.groupValues[2]
                val (campus, room) = splitPlace(place)
                // 补发暂存的节次（同星期，共享地点）
                pendingPeriods?.takeIf { fullWeekday == weekday }?.let { (ps, pe) ->
                    result.add(CourseSegment(weeks, weekday, ps, pe, campus, room))
                }
                pendingPeriods = null
                result.add(
                    CourseSegment(
                        weeks = weeks,
                        weekday = fullWeekday,
                        periodStart = m.groupValues[3].toInt(),
                        periodEnd = (m.groupValues[4].ifEmpty { m.groupValues[3] }).toInt(),
                        campus = campus,
                        room = room,
                    )
                )
            }
        }
        return result
    }
}
