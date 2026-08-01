package cn.squ.coursetable.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cn.squ.coursetable.MainActivity
import cn.squ.coursetable.storage.PlatformStorage
import cn.squ.coursetable.storage.ScheduleCache
import cn.squ.coursetable.ui.CourseColors
import cn.squ.coursetable.ui.CourseState
import cn.squ.coursetable.ui.Periods
import cn.squ.coursetable.ui.courseStateOf
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 终端风小组件配色（固定深色，桌面壁纸上最醒目） */
private object WC {
    val bg = ColorProvider(Color(0xE6191919))
    val fg = ColorProvider(Color(0xFFFFFFFF))
    val fg2 = ColorProvider(Color(0xFFB3B3B3))
    val fg3 = ColorProvider(Color(0xFF777777))
    val accent = ColorProvider(Color(0xFFFFFA00))
    val green = ColorProvider(Color(0xFF00FFA2))
    val pink = ColorProvider(Color(0xFFFF1AAC))
}

/** 小组件数据模型：从课表缓存 + 当前时间算出今日课程列表与 NEXT 条 */
object CourseWidgetData {

    data class Item(
        val timeText: String,   // "08:00~09:40"
        val name: String,       // 缩写课名
        val room: String,
        val state: CourseState,
    )

    data class Model(
        val dateText: String,   // "8月1日 周六 · W.20"
        val items: List<Item>,
        val nextText: String,   // "NEXT ▸ 高数Ⅰ下 · 4-407 · 10:00"
        val hasTable: Boolean,
    )

    fun load(now: LocalDateTime): Model {
        val wdNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val wd = now.dayOfWeek.ordinal + 1
        val dateText = "${now.monthNumber}月${now.dayOfMonth}日 周${wdNames[wd - 1]}"

        val table = ScheduleCache.loadTable()
            ?: return Model(dateText, emptyList(), "未同步课表 · 点我打开登录", hasTable = false)

        val week = table.currentWeek
        val nowMin = now.hour * 60 + now.minute
        val items = table.courses.asSequence()
            .flatMap { c ->
                c.segments.filter { it.weekday == wd && week in it.weeks }.map { c to it }
            }
            .sortedBy { it.second.periodStart }
            .map { (c, s) ->
                Item(
                    timeText = "${Periods.start(s.periodStart)}~${Periods.end(s.periodEnd)}",
                    name = CourseColors.tinyName(c.name),
                    room = s.room,
                    state = courseStateOf(s, nowMin),
                )
            }
            .toList()

        val next = items.firstOrNull { it.state != CourseState.DONE }
        val nextText = when {
            next != null -> "NEXT ▸ ${next.name} · ${next.room} · ${next.timeText.substringBefore("~")}"
            items.isEmpty() -> "今日无课 / NO CLASS"
            else -> "今日课程已结束 / ALL DONE"
        }
        return Model("$dateText · W.%02d".format(week), items, nextText, hasTable = true)
    }
}

/** 今日课程桌面小组件（Glance） */
class CourseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlatformStorage.init(context.applicationContext)
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val model = CourseWidgetData.load(now)
        provideContent { Content(model) }
    }

    @androidx.compose.runtime.Composable
    private fun Content(m: CourseWidgetData.Model) {
        Column(
            GlanceModifier.fillMaxSize()
                .background(WC.bg)
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // 头部：黄标 + 标题 + 日期
            Row(
                GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(GlanceModifier.width(7.dp).height(15.dp).background(WC.accent)) {}
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    "今日课程 TODAY",
                    style = TextStyle(color = WC.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    m.dateText,
                    style = TextStyle(color = WC.fg3, fontSize = 9.sp),
                )
            }
            Spacer(GlanceModifier.height(6.dp))

            if (m.items.isEmpty()) {
                Box(
                    GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (m.hasTable) "今日无课 / NO CLASS" else "点我打开零终端登录同步",
                        style = TextStyle(color = WC.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(m.items) { item -> CourseRow(item) }
                }
            }

            Spacer(GlanceModifier.height(4.dp))
            // NEXT 条
            Text(
                m.nextText,
                style = TextStyle(color = WC.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun CourseRow(item: CourseWidgetData.Item) {
        val barColor = when (item.state) {
            CourseState.NOW -> WC.green
            CourseState.WARN -> WC.accent
            CourseState.WAIT -> WC.fg3
            CourseState.DONE -> WC.fg3
        }
        val nameColor = when (item.state) {
            CourseState.NOW -> WC.green
            CourseState.WARN -> WC.accent
            CourseState.WAIT -> WC.fg
            CourseState.DONE -> WC.fg3
        }
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.width(3.dp).height(14.dp).background(barColor)) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                item.timeText,
                style = TextStyle(color = WC.fg3, fontSize = 9.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                item.name,
                style = TextStyle(
                    color = nameColor, fontSize = 11.sp,
                    fontWeight = if (item.state == CourseState.NOW) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                item.room,
                style = TextStyle(color = WC.fg2, fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}

/** 小组件接收器（注册到 Manifest） */
class CourseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CourseWidget()
}
