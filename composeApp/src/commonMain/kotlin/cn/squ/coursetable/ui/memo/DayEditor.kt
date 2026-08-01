package cn.squ.coursetable.ui.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.squ.coursetable.model.MemoEvent
import cn.squ.coursetable.ui.CourseColors
import cn.squ.coursetable.ui.Periods
import cn.squ.coursetable.ui.fx.contourBackground
import cn.squ.coursetable.ui.state.AppState
import cn.squ.coursetable.ui.theme.squPalette
import cn.squ.coursetable.ui.week.CutCornerShape
import kotlinx.datetime.Clock
import kotlin.math.roundToInt

private val AXIS_START_MIN = 7 * 60          // 时间轴起点 07:00
private val AXIS_END_MIN = 22 * 60           // 时间轴终点 22:00
private val MIN_PER_DP = 1.15f               // 每 dp 对应分钟密度（越大越紧凑）
private val WD_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")
private val WD_EN = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

private fun fmtMin(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** 解析 HH:MM（兼容 ：和 .） */
private fun parseHm(s: String): Int? {
    val m = Regex("""(\d{1,2})[:：.](\d{1,2})""").find(s.trim()) ?: return null
    val h = m.groupValues[1].toIntOrNull() ?: return null
    val min = m.groupValues[2].toIntOrNull() ?: return null
    if (h !in 0..23 || min !in 0..59) return null
    return h * 60 + min
}

/**
 * 当天备忘录编辑页：左侧时间轴 + 当天课程参考块 + 备忘块。
 * 点空白按 y 换算时间（对齐 30 分钟）创建；点备忘块编辑/删除。
 */
@Composable
fun DayEditor(state: AppState, weekday: Int, onClose: () -> Unit) {
    val pal = squPalette
    val density = LocalDensity.current
    val week = state.week
    val table = state.table
    val memos = state.memosOf(week, weekday)

    // 创建中（起止分钟）/ 编辑中的备忘
    var creating by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editing by remember { mutableStateOf<MemoEvent?>(null) }

    val pxPerMin = with(density) { MIN_PER_DP.dp.toPx() }
    val totalH: Dp = ((AXIS_END_MIN - AXIS_START_MIN) / MIN_PER_DP).dp

    Column(
        Modifier.fillMaxSize()
            .background(pal.bg)
            .contourBackground(pal),
    ) {
        // 头部
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(CutCornerShape(6.dp))
                    .border(1.dp, pal.line, CutCornerShape(6.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("◀ 返回", color = pal.fg2, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(10.dp).height(20.dp).background(pal.accent))
            Spacer(Modifier.width(8.dp))
            Text(
                "周${WD_NAMES[weekday - 1]} / ${WD_EN[weekday - 1]} · W.%02d · MEMO".format(week),
                color = pal.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        }
        Text(
            "点击时间轴空白处创建事件 · 点击黄色事件块编辑",
            color = pal.fg3, fontSize = 9.sp, letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))

        // 时间轴主体（可滚动）
        Row(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            // 左刻度
            Column(Modifier.width(46.dp).height(totalH)) {
                for (h in AXIS_START_MIN / 60 until AXIS_END_MIN / 60) {
                    Box(Modifier.height((60 / MIN_PER_DP).dp).fillMaxWidth()) {
                        Text(
                            "%02d:00".format(h),
                            color = pal.fg3, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp),
                        )
                    }
                }
            }
            // 画布
            Box(
                Modifier.weight(1f).height(totalH)
                    .pointerInput(week, weekday) {
                        detectTapGestures { pos ->
                            val raw = AXIS_START_MIN + pos.y / pxPerMin
                            val snapped = (raw / 30f).roundToInt() * 30
                            val st = snapped.coerceIn(AXIS_START_MIN, AXIS_END_MIN - 30)
                            creating = st to (st + 60).coerceAtMost(AXIS_END_MIN + 120)
                        }
                    },
            ) {
                // 小时网格线
                Column(Modifier.fillMaxSize()) {
                    repeat(AXIS_END_MIN / 60 - AXIS_START_MIN / 60) {
                        Box(
                            Modifier.height((60 / MIN_PER_DP).dp).fillMaxWidth()
                                .border(0.5.dp, pal.line.copy(alpha = .5f)),
                        )
                    }
                }
                // 当天课程参考块（半透明，不可点）
                table?.courses?.forEach { c ->
                    c.segments.forEach { s ->
                        if (s.weekday != weekday || week !in s.weeks) return@forEach
                        val st = Periods.toMin(Periods.start(s.periodStart))
                        val en = Periods.toMin(Periods.end(s.periodEnd))
                        if (en <= AXIS_START_MIN || st >= AXIS_END_MIN) return@forEach
                        val cc = CourseColors.of(c.name)
                        val top = ((st - AXIS_START_MIN).coerceAtLeast(0) / MIN_PER_DP).dp
                        val h = ((en - st) / MIN_PER_DP).dp
                        Column(
                            Modifier.offset(y = top).height(h).fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clip(CutCornerShape(6.dp))
                                .background(cc.copy(alpha = if (pal.isDark) .22f else .3f))
                                .border(1.dp, cc.copy(alpha = .6f), CutCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "${fmtMin(st)}~${fmtMin(en)} ${CourseColors.shortName(c.name)}",
                                color = pal.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                s.room, color = pal.fg2, fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // 备忘块（黄色，可点编辑）
                memos.forEach { memo ->
                    val top = ((memo.startMin - AXIS_START_MIN) / MIN_PER_DP).dp
                    val h = ((memo.endMin - memo.startMin) / MIN_PER_DP).dp.coerceAtLeast(22.dp)
                    Column(
                        Modifier.offset(y = top).height(h).fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(CutCornerShape(6.dp))
                            .background(pal.accent)
                            .clickable { editing = memo }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "◆ ${memo.title}",
                            color = pal.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (h >= 34.dp) Text(
                            "${fmtMin(memo.startMin)}~${fmtMin(memo.endMin)}",
                            color = pal.onAccent.copy(alpha = .75f), fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
        }
    }

    // 创建对话框
    creating?.let { (st, en) ->
        MemoDialog(
            title = "新建事件 / NEW",
            initial = MemoEvent(0L, week, weekday, st, en, ""),
            onDismiss = { creating = null },
            onSave = { ev ->
                state.addMemo(ev.copy(id = Clock.System.now().toEpochMilliseconds()))
                creating = null
            },
            onDelete = null,
        )
    }
    // 编辑对话框
    editing?.let { memo ->
        MemoDialog(
            title = "编辑事件 / EDIT",
            initial = memo,
            onDismiss = { editing = null },
            onSave = { ev ->
                state.updateMemo(ev.copy(id = memo.id))
                editing = null
            },
            onDelete = {
                state.deleteMemo(memo.id)
                editing = null
            },
        )
    }
}

@Composable
private fun MemoDialog(
    title: String,
    initial: MemoEvent,
    onDismiss: () -> Unit,
    onSave: (MemoEvent) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val pal = squPalette
    var name by remember { mutableStateOf(initial.title) }
    var startStr by remember { mutableStateOf(fmtMin(initial.startMin)) }
    var endStr by remember { mutableStateOf(fmtMin(initial.endMin)) }
    var note by remember { mutableStateOf(initial.note) }
    var err by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(pal.bg.copy(alpha = .72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(.86f)
                .clip(CutCornerShape(12.dp))
                .background(pal.panel)
                .border(1.dp, pal.line, CutCornerShape(12.dp))
                .border(2.dp, pal.accent.copy(alpha = .8f), CutCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title, color = pal.accent, fontSize = 11.sp,
                letterSpacing = 2.sp, fontFamily = FontFamily.Monospace,
            )
            MemoField("事件标题", name) { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { MemoField("开始 HH:MM", startStr) { startStr = it } }
                Box(Modifier.weight(1f)) { MemoField("结束 HH:MM", endStr) { endStr = it } }
            }
            MemoField("备注（可选）", note) { note = it }
            err?.let { Text("✗ $it", color = pal.pink, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton("保存", solid = true) {
                    val st = parseHm(startStr)
                    val en = parseHm(endStr)
                    when {
                        name.isBlank() -> err = "标题不能为空"
                        st == null || en == null -> err = "时间格式应为 HH:MM"
                        en <= st -> err = "结束时间需晚于开始时间"
                        else -> onSave(initial.copy(title = name.trim(), startMin = st, endMin = en, note = note.trim()))
                    }
                }
                if (onDelete != null) DialogButton("删除", danger = true, onClick = onDelete)
                DialogButton("取消", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun MemoField(label: String, value: String, onChange: (String) -> Unit) {
    val pal = squPalette
    val shape = CutCornerShape(8.dp)
    Column {
        Text(label, color = pal.fg3, fontSize = 9.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = pal.fg, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(pal.accent),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(40.dp)
                        .clip(shape).background(pal.card)
                        .border(1.dp, pal.line, shape)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

@Composable
private fun DialogButton(text: String, solid: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val pal = squPalette
    val c = if (danger) pal.pink else pal.accent
    val shape = CutCornerShape(6.dp)
    Box(
        Modifier.clip(shape)
            .background(if (solid) c else Color.Transparent)
            .border(1.dp, c, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text, color = if (solid) pal.onAccent else c,
            fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
        )
    }
}
