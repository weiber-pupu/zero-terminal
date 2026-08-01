package cn.squ.coursetable.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.squ.coursetable.model.Course
import cn.squ.coursetable.model.CourseSegment
import cn.squ.coursetable.ui.CourseColors
import cn.squ.coursetable.ui.CourseState
import cn.squ.coursetable.ui.Periods
import cn.squ.coursetable.ui.courseStateOf
import cn.squ.coursetable.ui.state.AppState
import cn.squ.coursetable.ui.theme.squPalette
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val WD_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

/** 今日课程面板 —— 设计规范 §8.4 三态系统 */
@Composable
fun TodayPanel(state: AppState, modifier: Modifier = Modifier) {
    val pal = squPalette
    val table = state.table ?: return
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val todayWd = now.dayOfWeek.ordinal + 1
    val nowMin = now.hour * 60 + now.minute
    val viewingCurrentWeek = state.week == table.currentWeek

    val list = remember(table, state.week) {
        buildList {
            table.courses.forEach { c ->
                c.segments.forEach { s ->
                    if (s.weekday == todayWd && state.week in s.weeks) add(c to s)
                }
            }
        }.sortedBy { it.second.periodStart }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        // 大日期
        Text(
            "%02d.%02d".format(now.monthNumber, now.dayOfMonth),
            color = pal.fg, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "星期${WD_NAMES[todayWd - 1]} · WEEK %02d".format(state.week),
            color = pal.fg3, fontSize = 11.sp, letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(14.dp))

        if (list.isEmpty()) {
            TodayItemShell(borderColor = pal.green) {
                Text("FREE DAY", color = pal.green, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("今天没课 🎉", color = pal.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("—— / 自由安排", color = pal.fg2, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        } else {
            list.forEach { (c, s) ->
                val st = if (viewingCurrentWeek) courseStateOf(s, nowMin) else CourseState.WAIT
                TodayCourseItem(c, s, st)
                Spacer(Modifier.height(10.dp))
            }
        }

        // 下一节横幅
        val upcoming = list.firstOrNull {
            !viewingCurrentWeek || courseStateOf(it.second, nowMin) != CourseState.DONE
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth()
                .border(1.dp, pal.fg3, androidx.compose.ui.graphics.RectangleShape)
                .padding(12.dp),
        ) {
            Column {
                Text("NEXT COURSE", color = pal.fg2, fontSize = 9.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                Text(
                    if (upcoming != null)
                        "${CourseColors.shortName(upcoming.first.name)} · ${upcoming.second.room} · ${Periods.start(upcoming.second.periodStart)}"
                    else "今日课程已结束",
                    color = pal.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun TodayCourseItem(course: Course, seg: CourseSegment, st: CourseState) {
    val pal = squPalette
    val (borderColor, badgeText, badgeBg, timeColor, dimmed) = when (st) {
        CourseState.NOW -> StateStyle(pal.green, "课时中", pal.green, pal.green, false)
        CourseState.WARN -> StateStyle(pal.accent, "课前预警", pal.accent, pal.accent, false)
        CourseState.DONE -> StateStyle(pal.fg3, "已结束", pal.fg3, pal.fg2, true)
        CourseState.WAIT -> StateStyle(pal.fg3, "待上课", null, pal.fg2, false)
    }
    TodayItemShell(borderColor = borderColor, dimmed = dimmed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${Periods.start(seg.periodStart)}~${Periods.end(seg.periodEnd)}",
                color = timeColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            if (badgeText != null && badgeBg != null) {
                Box(
                    Modifier.background(badgeBg).padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(badgeText, color = pal.onAccent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            } else if (badgeText != null) {
                Text(badgeText, color = pal.fg3, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Text(
            CourseColors.shortName(course.name),
            color = pal.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${seg.room} · ${course.teacher} · ${seg.periodStart}~${seg.periodEnd}节",
            color = pal.fg2, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
        )
    }
}

private data class StateStyle(
    val borderColor: androidx.compose.ui.graphics.Color,
    val badgeText: String?,
    val badgeBg: androidx.compose.ui.graphics.Color?,
    val timeColor: androidx.compose.ui.graphics.Color,
    val dimmed: Boolean,
)

@Composable
private fun TodayItemShell(
    borderColor: androidx.compose.ui.graphics.Color,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val pal = squPalette
    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, pal.line)
            .background(if (pal.isDark) pal.card else pal.card.copy(alpha = .6f))
            .padding(start = 0.dp),
    ) {
        Box(Modifier.width(3.dp).height(72.dp).background(borderColor))
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                .alpha(if (dimmed) .45f else 1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            content()
        }
    }
}
