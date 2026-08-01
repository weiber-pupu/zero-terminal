package cn.squ.coursetable.ui.week

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.squ.coursetable.isDesktopPlatform
import cn.squ.coursetable.model.Course
import cn.squ.coursetable.model.CourseSegment
import cn.squ.coursetable.ui.CourseColors
import cn.squ.coursetable.ui.Periods
import cn.squ.coursetable.ui.fx.NoiseTexture
import cn.squ.coursetable.ui.fx.cellTexture
import cn.squ.coursetable.ui.fx.cornerBrackets
import cn.squ.coursetable.ui.state.AppState
import cn.squ.coursetable.ui.theme.squPalette
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

private val HEADER_H = 40.dp
private val AXIS_W_WIDE = 46.dp
private val DAY_W_WIDE = 136.dp
private val NARROW_BREAK = 980.dp

private val WD_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")
private val WD_EN = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** 卡片定位焦点（设计规范 §8.4：课时中绿脉冲 / 下一节黄脉冲） */
enum class FocusKind { NONE, NOW, NEXT }

/** 悬停浮层数据（桌面端） */
private data class HoverInfo(
    val course: Course,
    val seg: CourseSegment,
    val pos: Offset,   // 卡片右上角（viewport 坐标，px）
    val cardW: Float,  // 卡片宽（px，用于左侧翻转）
)

/** 单边切角（左上+右下）—— 设计规范 §3 形状语言 */
class CutCornerShape(private val cut: Dp) : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density,
    ): Outline {
        val c = with(density) { cut.toPx() }
        val p = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(p)
    }
}

/**
 * 周视图网格（设计规范 §8）：
 * 大节视图 + 自然折叠动画 + 磨砂玻璃卡 + 切周转场 +
 * 桌面光源/准星/悬停浮层 + 窄屏七列自适应。
 */
@Composable
fun WeekGrid(
    state: AppState,
    onCourseClick: (Course, CourseSegment) -> Unit,
    modifier: Modifier = Modifier,
    onDayClick: (Int) -> Unit = {},
) {
    val pal = squPalette
    val table = state.table ?: return
    val blocks = Periods.blocks(state.maxPeriod)
    val blockMode = state.blockMode
    val density = LocalDensity.current

    // 折叠 morph：0.72s 自然减速曲线（§8.1 调整版）
    val morph = remember { Animatable(1f) }
    LaunchedEffect(blockMode) {
        morph.snapTo(.5f)
        morph.animateTo(1f, tween(720, easing = CubicBezierEasing(.22f, 1f, .36f, 1f)))
    }
    val morphP = ((morph.value - .5f) / .5f).coerceIn(0f, 1f)

    // 切周错位转场（§8.5）
    val shift = remember { Animatable(1f) }
    LaunchedEffect(state.week) {
        shift.snapTo(0f)
        shift.animateTo(1f, tween(450))
    }
    val shiftX = if (shift.value < 1f)
        (sin(shift.value * PI * 3) * (1 - shift.value) * 8).toFloat() else 0f
    val shiftAlpha = if (shift.value < 1f)
        1f - .5f * abs(sin(shift.value * PI * 4)).toFloat() * (1 - shift.value) else 1f

    val todayWd = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.ordinal + 1
    val viewingCurrentWeek = state.week == table.currentWeek

    // 焦点定位：课时中 > 下一节（仅看本周）
    val focus = remember(table, state.week) {
        if (!viewingCurrentWeek) return@remember emptyMap<CourseSegment, FocusKind>()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val nowMin = now.hour * 60 + now.minute
        var nowSeg: CourseSegment? = null
        var nextSeg: CourseSegment? = null
        var nextStart = Int.MAX_VALUE
        table.courses.forEach { c ->
            c.segments.forEach { s ->
                if (s.weekday != todayWd || state.week !in s.weeks) return@forEach
                val st = Periods.toMin(Periods.start(s.periodStart))
                val en = Periods.toMin(Periods.end(s.periodEnd))
                if (nowMin in st..en) nowSeg = s
                else if (st > nowMin && st < nextStart) { nextStart = st; nextSeg = s }
            }
        }
        buildMap {
            nowSeg?.let { put(it, FocusKind.NOW) }
            if (nowSeg == null) nextSeg?.let { put(it, FocusKind.NEXT) }
        }
    }

    // 桌面指针与悬停浮层状态
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var hoverInfo by remember { mutableStateOf<HoverInfo?>(null) }
    val scroll = rememberScrollState()
    val pointerGrid: Offset? = pointer?.let { Offset(it.x + scroll.value, it.y) }

    // 手机端切周滑动的跟手位移
    val scope = rememberCoroutineScope()
    val swipeX = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier.then(
            // 挂在父容器上：不遮挡子卡片的事件（§8.3 修复）
            if (isDesktopPlatform) Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        when (ev.type) {
                            PointerEventType.Move ->
                                pointer = ev.changes.first().position
                            PointerEventType.Exit -> pointer = null
                            else -> {}
                        }
                    }
                }
            } else Modifier.pointerInput(Unit) {
                // 手机端：左右滑切换周（跟手位移 + 松手 56dp 触发）
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        acc += dragAmount
                        scope.launch { swipeX.snapTo(acc * .35f) }
                        change.consume()
                    },
                    onDragEnd = {
                        val threshold = 56.dp.toPx()
                        when {
                            acc < -threshold -> state.changeWeek(1)
                            acc > threshold -> state.changeWeek(-1)
                        }
                        acc = 0f
                        scope.launch { swipeX.animateTo(0f, tween(240)) }
                    },
                    onDragCancel = {
                        acc = 0f
                        scope.launch { swipeX.animateTo(0f, tween(240)) }
                    },
                )
            },
        ),
    ) {
        val narrow = maxWidth < NARROW_BREAK
        val axisW = if (narrow) 34.dp else AXIS_W_WIDE
        val dayW = if (narrow) ((maxWidth - axisW) / 7) else DAY_W_WIDE
        val compact = dayW < 100.dp
        val rowCount = if (blockMode) blocks.size else state.maxPeriod
        val rowH = when {
            blockMode && compact -> 82.dp
            blockMode -> 92.dp
            compact -> 46.dp
            else -> 56.dp
        }
        val vpW = constraints.maxWidth.toFloat()
        val vpH = constraints.maxHeight.toFloat()

        Row(
            Modifier
                .then(if (narrow) Modifier else Modifier.horizontalScroll(scroll))
                .graphicsLayer {
                    translationX = with(density) { shiftX.dp.toPx() } + swipeX.value
                    alpha = shiftAlpha
                }
                .cornerBrackets(pal),
        ) {
            // 轴列（morph 滑入）
            Column(
                Modifier.width(axisW).graphicsLayer {
                    translationX = with(density) { (-8).dp.toPx() } * (1f - morphP)
                    alpha = morphP
                },
            ) {
                Box(Modifier.height(HEADER_H).fillMaxWidth().border(0.5.dp, pal.line))
                repeat(rowCount) { i ->
                    Column(
                        Modifier.height(rowH).fillMaxWidth().border(0.5.dp, pal.line),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (blockMode) {
                            Text(
                                Periods.BLOCK_NAMES[i], color = pal.fg2,
                                fontSize = if (compact) 11.sp else 13.sp,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "${blocks[i].first}~${blocks[i].second}节", color = pal.fg3,
                                fontSize = if (compact) 7.sp else 8.sp, fontFamily = FontFamily.Monospace,
                            )
                        } else {
                            Text(
                                "%02d".format(i + 1), color = pal.fg2,
                                fontSize = if (compact) 10.sp else 12.sp,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            )
                            if (!compact) Text(
                                Periods.start(i + 1), color = pal.fg3,
                                fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
            // 七天列
            for (d in 1..7) {
                DayColumn(
                    weekday = d,
                    isToday = d == todayWd,
                    state = state,
                    blocks = blocks,
                    rowCount = rowCount,
                    rowH = rowH,
                    axisW = axisW,
                    dayW = dayW,
                    compact = compact,
                    focus = focus,
                    morph = morph.value,
                    pointerGrid = if (isDesktopPlatform) pointerGrid else null,
                    scrollPx = scroll.value,
                    onHover = { info -> hoverInfo = info },
                    onCourseClick = onCourseClick,
                    onDayClick = onDayClick,
                )
            }
        }

        if (isDesktopPlatform) {
            // 光源 + 十字准星（浮层打开时隐藏，§8.3 避让）
            val popOpen = hoverInfo != null
            Canvas(Modifier.fillMaxSize()) {
                pointer?.let { p ->
                    if (!popOpen) {
                        // 跟随光源（径向渐变光斑）
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to pal.accent.copy(alpha = if (pal.isDark) .07f else .10f),
                                .55f to pal.accent.copy(alpha = if (pal.isDark) .025f else .04f),
                                1f to Color.Transparent,
                                center = p, radius = 280f,
                            ),
                            radius = 280f, center = p,
                        )
                        val chColor = pal.accent.copy(alpha = if (pal.isDark) .35f else .55f)
                        drawLine(chColor, Offset(0f, p.y), Offset(size.width, p.y), strokeWidth = 1f)
                        drawLine(chColor, Offset(p.x, 0f), Offset(p.x, size.height), strokeWidth = 1f)
                    }
                }
            }
            // 坐标标签（浮层打开时隐藏）
            if (!popOpen) pointer?.let { p ->
                val coordText = with(density) {
                    val gx = p.x + scroll.value
                    val col = ((gx - axisW.toPx()) / dayW.toPx()).toInt()
                    if (col !in 0..6) return@with null
                    val row = ((p.y - HEADER_H.toPx()) / rowH.toPx()).toInt()
                    if (row !in 0 until rowCount) return@with null
                    if (blockMode)
                        "${WD_EN[col]} · BLK.${row + 1} (${blocks[row].first}~${blocks[row].second}节)"
                    else
                        "${WD_EN[col]} · P.%02d".format(row + 1)
                }
                if (coordText != null) {
                    val tagW = with(density) { 190.dp.toPx() }
                    val tagH = with(density) { 22.dp.toPx() }
                    val off = with(density) { 14.dp.toPx() }
                    var tx = p.x + off
                    var ty = p.y + off
                    if (tx + tagW > vpW - 8) tx = p.x - tagW - off
                    if (ty + tagH > vpH - 8) ty = p.y - tagH - off
                    Box(
                        Modifier.offset { IntOffset(tx.toInt(), ty.toInt()) }
                            .background(if (pal.isDark) Color(0xE6141416) else Color(0xF0FFFFFF))
                            .border(1.dp, pal.line)
                            .border(2.dp, pal.accent.copy(alpha = .9f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            coordText, color = pal.accent, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
                        )
                    }
                }
            }
            // 悬停信息浮层（§8.3 恢复版）
            hoverInfo?.let { info ->
                HoverPopover(info, state.week, vpW, vpH)
            }
        }
    }
}

@Composable
private fun DayColumn(
    weekday: Int,
    isToday: Boolean,
    state: AppState,
    blocks: List<Pair<Int, Int>>,
    rowCount: Int,
    rowH: Dp,
    axisW: Dp,
    dayW: Dp,
    compact: Boolean,
    focus: Map<CourseSegment, FocusKind>,
    morph: Float,
    pointerGrid: Offset?,
    scrollPx: Int,
    onHover: (HoverInfo?) -> Unit,
    onCourseClick: (Course, CourseSegment) -> Unit,
    onDayClick: (Int) -> Unit,
) {
    val pal = squPalette
    val table = state.table ?: return
    val blockMode = state.blockMode
    val density = LocalDensity.current

    Box(Modifier.width(dayW)) {
        Column {
            // 表头：周一 MON；今天反色；点击进入当天备忘录编辑
            Column(
                Modifier.height(HEADER_H).fillMaxWidth()
                    .background(if (isToday) pal.accent else Color.Transparent)
                    .border(0.5.dp, pal.line)
                    .clickable { onDayClick(weekday) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "周${WD_NAMES[weekday - 1]}", fontSize = if (compact) 10.sp else 12.sp,
                    color = if (isToday) pal.onAccent else pal.fg2,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    WD_EN[weekday - 1], fontSize = if (compact) 7.sp else 8.sp,
                    letterSpacing = if (compact) 1.sp else 2.sp,
                    color = if (isToday) pal.onAccent.copy(alpha = .6f) else pal.fg3,
                    fontFamily = FontFamily.Monospace,
                )
            }
            repeat(rowCount) {
                Box(
                    Modifier.height(rowH).fillMaxWidth()
                        .cellTexture(pal)
                        .border(0.5.dp, pal.line),
                )
            }
        }
        // 当天备忘：与课程重叠的事件替换卡片节次标签，不重叠的才显示独立细条
        val dayMemos = state.memosOf(state.week, weekday)
        fun memoSpan(m: cn.squ.coursetable.model.MemoEvent): Pair<Int, Int> {
            val sp = periodOfMinute(m.startMin, state.maxPeriod)
            val ep = periodOfMinute(m.endMin - 1, state.maxPeriod).coerceAtLeast(sp)
            return sp to ep
        }
        fun memoForSeg(seg: CourseSegment) = dayMemos.firstOrNull { m ->
            val (sp, ep) = memoSpan(m)
            sp <= seg.periodEnd && ep >= seg.periodStart
        }
        fun memoOverlapsAnyCourse(m: cn.squ.coursetable.model.MemoEvent): Boolean {
            val (sp, ep) = memoSpan(m)
            return table.courses.any { c ->
                c.segments.any { s ->
                    s.weekday == weekday && state.week in s.weeks &&
                        sp <= s.periodEnd && ep >= s.periodStart
                }
            }
        }
        // 课程卡片（绝对定位 + morph）
        table.courses.forEach { course ->
            course.segments.forEach { seg ->
                if (seg.weekday != weekday || state.week !in seg.weeks) return@forEach
                val startIdx = if (blockMode) Periods.blockOf(seg.periodStart, blocks) else seg.periodStart - 1
                val endIdx = if (blockMode) Periods.blockOf(seg.periodEnd, blocks) else seg.periodEnd - 1
                val span = (endIdx - startIdx + 1).coerceAtLeast(1)

                // 光源辉光：平滑衰减（替代二元 lit，§8.3 调整版）
                val glow = pointerGrid?.let { pg ->
                    with(density) {
                        val cx = axisW.toPx() + (weekday - 1) * dayW.toPx() + dayW.toPx() / 2
                        val cy = HEADER_H.toPx() + rowH.toPx() * startIdx + rowH.toPx() * span / 2
                        (1f - hypot(pg.x - cx, pg.y - cy) / 280f).coerceIn(0f, 1f)
                    }
                } ?: 0f

                // 悬停浮层锚点（卡片右上角，viewport 坐标）
                val hoverAnchor = with(density) {
                    Offset(
                        axisW.toPx() + weekday * dayW.toPx() - scrollPx,
                        HEADER_H.toPx() + rowH.toPx() * startIdx,
                    )
                }
                val cardWPx = with(density) { dayW.toPx() }

                CourseCard(
                    course = course,
                    seg = seg,
                    focus = focus[seg] ?: FocusKind.NONE,
                    glow = glow,
                    compact = compact,
                    memoTitle = memoForSeg(seg)?.title,
                    onHover = { entering ->
                        onHover(if (entering) HoverInfo(course, seg, hoverAnchor, cardWPx) else null)
                    },
                    onClick = { onCourseClick(course, seg) },
                    modifier = Modifier
                        .offset(y = HEADER_H + rowH * startIdx)
                        .height(rowH * span)
                        .fillMaxWidth()
                        .padding(2.dp)
                        .graphicsLayer {
                            scaleY = morph
                            alpha = ((morph - .5f) / .5f).coerceIn(0f, 1f)
                        },
                )
            }
        }
        // 备忘录细条（不与任何课程重叠的才独立显示；重叠的已替换卡片节次标签）
        dayMemos.filter { !memoOverlapsAnyCourse(it) }.forEachIndexed { i, memo ->
            val (sp, ep) = memoSpan(memo)
            val startIdx = if (blockMode) Periods.blockOf(sp, blocks) else sp - 1
            val endIdx = if (blockMode) Periods.blockOf(ep, blocks) else ep - 1
            val span = (endIdx - startIdx + 1).coerceAtLeast(1)
            Box(
                Modifier
                    .offset(y = HEADER_H + rowH * startIdx + 3.dp + 17.dp * (i % 2))
                    .height(14.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clip(CutCornerShape(3.dp))
                    .background(pal.accent)
                    .clickable { onDayClick(weekday) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "◆ ${memo.title}",
                    color = pal.onAccent, fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
            }
            // 跨行时底部细线提示该备忘覆盖多行
            if (span > 1) {
                Box(
                    Modifier
                        .offset(y = HEADER_H + rowH * (startIdx + span) - 4.dp)
                        .height(2.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .background(pal.accent.copy(alpha = .55f)),
                )
            }
        }
    }
}

/** 当天分钟 → 所在节次（第一个下课时间 ≥ min 的节） */
private fun periodOfMinute(min: Int, maxPeriod: Int): Int {
    for (p in 1..maxPeriod) {
        if (Periods.toMin(Periods.end(p)) >= min) return p
    }
    return maxPeriod
}

/** 磨砂玻璃机能卡（§8.2 调整版）+ 悬停 glitch（§5）；memoTitle 非空时节次标签替换为事件标题 */
@Composable
fun CourseCard(
    course: Course,
    seg: CourseSegment,
    focus: FocusKind,
    glow: Float,
    compact: Boolean,
    memoTitle: String? = null,
    onHover: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = squPalette
    val cc = CourseColors.of(course.name)
    val shape = CutCornerShape(10.dp)

    // 焦点脉冲（1.1s 呼吸）
    val pulseAlpha by rememberInfiniteTransition(label = "focus").animateFloat(
        initialValue = .35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "alpha",
    )
    val focusColor = when (focus) {
        FocusKind.NOW -> pal.green
        FocusKind.NEXT -> pal.accent
        FocusKind.NONE -> Color.Transparent
    }

    // 桌面悬停：glitch RGB 色散 + 抖动（§5）
    var hovered by remember { mutableStateOf(false) }
    val glitchT = remember { Animatable(1f) }
    LaunchedEffect(hovered) {
        if (hovered && isDesktopPlatform) {
            glitchT.snapTo(0f)
            glitchT.animateTo(1f, tween(400))
        }
    }
    val glitchOn = hovered && glitchT.value < 1f
    val glitchFrame = (glitchT.value * 8).toInt() % 2 == 0
    val glitchAlpha = if (glitchOn && glitchFrame) .85f else 0f
    val glitchDx = if (glitchFrame) (-2.5).dp else 2.5.dp

    val highlight = hovered || glow > .35f
    val borderBase = lerp(pal.line, cc, .45f)
    val borderColor = when {
        hovered -> cc
        glow > 0f -> lerp(borderBase, cc, glow)
        else -> borderBase
    }
    val borderWidth = (1f + glow * .8f + if (hovered) .5f else 0f).dp

    Box(
        modifier
            .shadow(
                elevation = if (highlight) 8.dp else 4.dp,
                shape = shape, clip = false,
                ambientColor = Color.Black.copy(alpha = if (pal.isDark) .4f else .18f),
                spotColor = cc.copy(alpha = .3f),
            )
            .clip(shape)
            // 磨砂底：半透明染色渐变，透出背景纹理
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(pal.card2, cc, .18f).copy(alpha = if (pal.isDark) .78f else .7f),
                        lerp(pal.card, cc, .08f).copy(alpha = if (pal.isDark) .58f else .5f),
                    ),
                )
            )
            .background(NoiseTexture.brush(pal.isDark))
            // 顶部高光（玻璃感）
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (pal.isDark) .1f else .45f),
                        Color.White.copy(alpha = if (pal.isDark) .015f else .08f),
                    ),
                )
            )
            .border(borderWidth, borderColor, shape)
            .then(
                if (focus != FocusKind.NONE)
                    Modifier.border(1.5.dp, focusColor.copy(alpha = pulseAlpha), shape)
                else Modifier
            )
            .then(
                if (isDesktopPlatform) Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            when (awaitPointerEvent().type) {
                                PointerEventType.Enter -> { hovered = true; onHover(true) }
                                PointerEventType.Exit -> { hovered = false; onHover(false) }
                                else -> {}
                            }
                        }
                    }
                } else Modifier
            )
            .clickable(onClick = onClick),
    ) {
        // 光源反射 + 悬停提亮
        val sheen = glow * .06f + if (hovered) .08f else 0f
        if (sheen > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = sheen)))
        }
        // glitch 闪烁层
        if (glitchOn && !glitchFrame) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f)))
        }
        Row(Modifier.fillMaxSize()) {
            // 左侧课程色条
            Box(Modifier.width(4.dp).fillMaxHeight().background(cc))
            Column(
                Modifier.padding(
                    start = if (compact) 6.dp else 8.dp,
                    end = if (compact) 5.dp else 8.dp,
                    top = if (compact) 4.dp else 6.dp,
                    bottom = 4.dp,
                ),
            ) {
                if (!compact) Text(
                    "C.${"%02d".format(course.id % 100)}",
                    color = cc, fontSize = 8.sp, letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace,
                )
                // 课程名（glitch RGB 色散 + 抖动）；窄卡片用缩写（高数/大英…）
                Box {
                    val name = if (compact) CourseColors.tinyName(course.name) else CourseColors.shortName(course.name)
                    val nameSize = if (compact) 11.sp else 12.sp
                    if (glitchAlpha > 0f) {
                        Text(
                            name, color = pal.pink.copy(alpha = glitchAlpha),
                            fontSize = nameSize, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
                            modifier = Modifier.offset(x = glitchDx),
                        )
                        Text(
                            name, color = pal.green.copy(alpha = glitchAlpha),
                            fontSize = nameSize, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
                            modifier = Modifier.offset(x = -glitchDx),
                        )
                    }
                    Text(
                        name,
                        color = pal.fg, fontSize = nameSize, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = if (compact) 14.sp else 15.sp,
                        modifier = if (glitchOn) Modifier.offset(x = glitchDx / 2) else Modifier,
                    )
                }
                Text(
                    seg.room, color = pal.fg2, fontSize = if (compact) 8.sp else 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // 节次标签（有重叠备忘时替换为事件标题，信号黄底）
                Box(
                    Modifier.padding(top = if (compact) 2.dp else 3.dp)
                        .clip(CutCornerShape(3.dp))
                        .background(if (memoTitle != null) pal.accent else cc)
                        .padding(
                            horizontal = if (compact) 4.dp else 6.dp,
                            vertical = 1.dp,
                        ),
                ) {
                    Text(
                        memoTitle?.let { "◆ $it" } ?: "${seg.periodStart}~${seg.periodEnd}节",
                        color = if (memoTitle != null) pal.onAccent else Color(0xFF191919),
                        fontSize = if (compact) 8.sp else 9.sp,
                        fontWeight = if (memoTitle != null) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 悬停信息浮层（桌面端，§8.3） */
@Composable
private fun HoverPopover(info: HoverInfo, week: Int, vpW: Float, vpH: Float) {
    val pal = squPalette
    val density = LocalDensity.current
    val c = info.course
    val s = info.seg
    val cc = CourseColors.of(c.name)
    val popW = with(density) { 250.dp.toPx() }
    val popH = with(density) { 240.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    var x = info.pos.x + gap
    if (x + popW > vpW - 8) x = info.pos.x - info.cardW - popW - gap
    var y = info.pos.y
    if (y + popH > vpH - 8) y = vpH - popH - 8
    if (y < 8) y = 8f

    Column(
        Modifier.offset { IntOffset(x.toInt(), y.toInt()) }
            .width(250.dp)
            .clip(CutCornerShape(10.dp))
            .background(if (pal.isDark) Color(0xF2141416) else Color(0xF7FFFFFF))
            .border(1.dp, pal.line, CutCornerShape(10.dp))
            .border(2.dp, cc.copy(alpha = .8f), CutCornerShape(10.dp)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "CODE ${c.code.ifBlank { "—" }} · W.%02d".format(week),
                color = pal.accent, fontSize = 9.sp, letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                c.name, color = pal.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (c.className.isNotBlank()) Text(
                "教学班 ${c.className}", color = cc, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PopRow("TEACHER", c.teacher.ifBlank { "—" })
            PopRow("PLACE", "${s.campus} ${s.room}")
            PopRow("TIME", "星期${"一二三四五六日"[s.weekday - 1]} ${s.periodStart}~${s.periodEnd}节")
            PopRow("CLOCK", "${Periods.start(s.periodStart)} ~ ${Periods.end(s.periodEnd)}")
            PopRow("WEEKS", c.weeksInfo.ifBlank { "—" })
            PopRow("DEPT", c.department.ifBlank { "—" })
        }
    }
}

@Composable
private fun PopRow(k: String, v: String) {
    val pal = squPalette
    Row {
        Text(
            k, color = pal.fg3, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(64.dp),
        )
        Text(
            v, color = pal.fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
