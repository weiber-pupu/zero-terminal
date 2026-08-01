package cn.squ.coursetable.ui

import cn.squ.coursetable.model.CourseSegment
import cn.squ.coursetable.model.CourseUnit
import cn.squ.coursetable.storage.ScheduleCache

/**
 * 节次时间与大节折叠 —— 设计规范 §8.1 / §8.4。
 *
 * 时间为教务系统实测默认（40 分钟小课），课表同步时从
 * print-data 的 timeTableLayout.courseUnitList 更新为真实配置并缓存。
 */
object Periods {

    /** 实测兜底作息（jwgl layoutId=21） */
    private val DEFAULT_UNITS = listOf(
        CourseUnit(1, 8 * 60, 8 * 60 + 40), CourseUnit(2, 8 * 60 + 50, 9 * 60 + 30),
        CourseUnit(3, 9 * 60 + 40, 10 * 60 + 20), CourseUnit(4, 10 * 60 + 30, 11 * 60 + 10),
        CourseUnit(5, 11 * 60 + 20, 12 * 60),
        CourseUnit(6, 14 * 60, 14 * 60 + 40), CourseUnit(7, 14 * 60 + 50, 15 * 60 + 30),
        CourseUnit(8, 15 * 60 + 40, 16 * 60 + 20), CourseUnit(9, 16 * 60 + 30, 17 * 60 + 10),
        CourseUnit(10, 18 * 60 + 40, 19 * 60 + 20), CourseUnit(11, 19 * 60 + 30, 20 * 60 + 10),
        CourseUnit(12, 20 * 60 + 20, 21 * 60),
    )

    private var units: List<CourseUnit> = DEFAULT_UNITS

    /** 启动时从缓存恢复真实作息 */
    fun loadFromCache() {
        ScheduleCache.loadUnits().takeIf { it.isNotEmpty() }?.let { install(it) }
    }

    /** 同步成功后安装真实作息 */
    fun install(list: List<CourseUnit>) {
        units = list.sortedBy { it.indexNo }
    }

    val maxUnit: Int get() = units.lastOrNull()?.indexNo ?: 12

    private fun fmt(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    fun start(period: Int): String =
        units.firstOrNull { it.indexNo == period }?.let { fmt(it.startMin) }
            ?: units.lastOrNull()?.let { fmt(it.startMin) } ?: "00:00"

    fun end(period: Int): String =
        units.firstOrNull { it.indexNo == period }?.let { fmt(it.endMin) }
            ?: units.lastOrNull()?.let { fmt(it.endMin) } ?: "00:00"

    fun toMin(time: String): Int {
        val (h, m) = time.split(":")
        return h.toInt() * 60 + m.toInt()
    }

    /**
     * 大节划分（按实测排课分布）：1-2 / 3-4 / 5 / 6-7 / 8-9 / 10-11 / 12。
     * 第 5 节（11:20-12:00）与第 12 节为独立小节，不跨午休/晚休配对。
     * 非 12 节配置的学校退化为顺序两两配对。
     */
    fun blocks(maxPeriod: Int): List<Pair<Int, Int>> {
        val template = if (maxUnit == 12) BLOCKS_12 else sequentialBlocks(maxUnit)
        return template.filter { it.first <= maxPeriod }
    }

    private fun sequentialBlocks(count: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 1
        while (i <= count) {
            out.add(if (i + 1 <= count) i to i + 1 else i to i)
            i += 2
        }
        return out
    }

    fun blockOf(period: Int, blocks: List<Pair<Int, Int>>): Int =
        blocks.indexOfFirst { period >= it.first && period <= it.second }.coerceAtLeast(0)

    /** 大节汉字序号 */
    val BLOCK_NAMES = listOf("壹", "贰", "叁", "肆", "伍", "陆", "柒")

    private val BLOCKS_12 = listOf(
        1 to 2, 3 to 4, 5 to 5, 6 to 7, 8 to 9, 10 to 11, 12 to 12,
    )
}

/** 今日课程状态 —— 设计规范 §8.4 */
enum class CourseState { NOW, WARN, DONE, WAIT }

/** 课前预警窗口（冻结：40 分钟） */
const val WARN_WINDOW_MIN = 40

fun courseStateOf(seg: CourseSegment, nowMin: Int): CourseState {
    val st = Periods.toMin(Periods.start(seg.periodStart))
    val en = Periods.toMin(Periods.end(seg.periodEnd))
    return when {
        nowMin > en -> CourseState.DONE
        nowMin >= st -> CourseState.NOW
        st - nowMin <= WARN_WINDOW_MIN -> CourseState.WARN
        else -> CourseState.WAIT
    }
}
