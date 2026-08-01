package cn.squ.coursetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.squ.coursetable.model.Course
import cn.squ.coursetable.model.CourseSegment
import cn.squ.coursetable.ui.CourseColors
import cn.squ.coursetable.ui.Periods
import cn.squ.coursetable.ui.fx.contourBackground
import cn.squ.coursetable.ui.fx.glitchJitter
import cn.squ.coursetable.ui.fx.glitchOverlay
import cn.squ.coursetable.ui.fx.scanlineFlash
import cn.squ.coursetable.ui.life.ServiceCards
import cn.squ.coursetable.ui.login.LoginScreen
import cn.squ.coursetable.ui.memo.DayEditor
import cn.squ.coursetable.ui.state.AppState
import cn.squ.coursetable.ui.theme.SquTheme
import cn.squ.coursetable.ui.theme.squPalette
import cn.squ.coursetable.ui.today.TodayPanel
import cn.squ.coursetable.ui.week.CutCornerShape
import cn.squ.coursetable.ui.week.WeekGrid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 「终端 / TERMINAL」正式 UI（设计规范 v0.5）。
 */
@Composable
fun App() {
    val state = remember { AppState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { state.bootstrap() }

    SquTheme(dark = state.darkTheme) {
        val pal = squPalette
        Box(
            Modifier.fillMaxSize().background(pal.bg)
                .contourBackground(pal)
                .safeContentPadding(),
        ) {
            when {
                state.table == null && !state.hasCredentials ->
                    LoginScreen(state) { u, p, c -> scope.launch { state.login(u, p, c) } }

                state.table == null ->
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("SYNCING…", color = pal.accent, fontSize = 14.sp,
                            letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
                        Text("正在从教务系统拉取课表", color = pal.fg3, fontSize = 11.sp)
                    }

                else -> MainScreen(state)
            }

            // 状态条（3 秒自动消隐）
            state.statusMsg?.let { msg ->
                LaunchedEffect(msg) {
                    if (!state.syncing) { delay(3000); state.statusMsg = null }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp)
                        .clip(CutCornerShape(6.dp))
                        .background(pal.panel)
                        .border(1.dp, pal.line, CutCornerShape(6.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        msg, color = if (msg.startsWith("✗")) pal.pink else pal.accent,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var showToday by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<Course, CourseSegment>?>(null) }
    var dayEditor by remember { mutableStateOf<Int?>(null) }
    val pal = squPalette
    val density = LocalDensity.current

    // 切周扫描线 flash（§8.5，0.35s）
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.week) {
        flash.snapTo(1f)
        flash.animateTo(0f, tween(350))
    }

    // 故障转场：切周 / 切大小节 / 切主题（0.42s，自然克制）
    val glitch = remember { Animatable(0f) }
    LaunchedEffect(state.week, state.blockMode, state.darkTheme) {
        glitch.snapTo(1f)
        glitch.animateTo(0f, tween(420))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 980.dp
        if (wide) {
            // 桌面宽屏：周视图 + 右侧今日栏（对应设计稿布局）
            Row(Modifier.fillMaxSize().glitchJitter(glitch.value)) {
                Column(Modifier.weight(1f)) {
                    Header(state)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        WeekGrid(state, onCourseClick = { c, s -> detail = c to s }, onDayClick = { dayEditor = it })
                    }
                }
                Column(
                    Modifier.width(320.dp).fillMaxHeight()
                        .border(1.dp, pal.line)
                        .background(if (pal.isDark) pal.panel.copy(alpha = .35f) else pal.panel.copy(alpha = .5f))
                        .padding(20.dp),
                ) {
                    Text("今日课程 / TODAY", color = pal.fg2, fontSize = 12.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f)) { TodayPanel(state) }
                    Spacer(Modifier.height(10.dp))
                    ServiceCards(state.life)
                    Spacer(Modifier.height(10.dp))
                    SyncButton(state) { scope.launch { state.refresh(force = true) } }
                }
            }
        } else {
            // 手机窄屏：周视图 + 左下生活卡 + 底部 NEXT 条 + 今日抽屉
            Column(Modifier.fillMaxSize().glitchJitter(glitch.value)) {
                Header(state)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    WeekGrid(state, onCourseClick = { c, s -> detail = c to s }, onDayClick = { dayEditor = it })
                    // 左下空白区：校园卡余额 + 电费
                    ServiceCards(
                        state.life,
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = 2.dp, bottom = 8.dp),
                    )
                }
                NextBar(
                    state,
                    onClick = { showToday = true },
                    onSwipeUp = { showToday = true },
                )
            }

            // 今日抽屉（底部上滑；下滑关闭）
            AnimatedVisibility(
                visible = showToday,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(pal.panel)
                        .border(1.dp, pal.line)
                        .pointerInput(Unit) {
                            // 整个抽屉区域下滑关闭（内容滚动到顶后继续下拉即关）
                            var acc = 0f
                            detectVerticalDragGestures(
                                onDragStart = { acc = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount > 0) {
                                        acc += dragAmount
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    if (acc > with(density) { 36.dp.toPx() }) showToday = false
                                    acc = 0f
                                },
                            )
                        }
                        .padding(20.dp),
                ) {
                    // 拖动指示条
                    Box(
                        Modifier.align(Alignment.CenterHorizontally)
                            .width(44.dp).height(4.dp)
                            .clip(CutCornerShape(2.dp))
                            .background(pal.fg3.copy(alpha = .6f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("今日课程 / TODAY", color = pal.fg2, fontSize = 12.sp, letterSpacing = 3.sp)
                        Spacer(Modifier.weight(1f))
                        HeaderButton("⟳ 同步", active = state.syncing) {
                            scope.launch { state.refresh(force = true) }
                        }
                        Spacer(Modifier.width(8.dp))
                        HeaderButton("✕ 关闭") { showToday = false }
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.height(360.dp)) { TodayPanel(state) }
                }
            }
        }

        // 切周扫描线 flash 覆盖层
        Box(Modifier.fillMaxSize().scanlineFlash(pal, flash.value))
        // 故障转场覆盖层（切周/大小节/主题）
        Box(Modifier.fillMaxSize().glitchOverlay(pal, glitch.value))

        // 课程详情浮层
        detail?.let { (c, s) ->
            Box(
                Modifier.fillMaxSize().background(pal.bg.copy(alpha = .72f))
                    .clickable { detail = null },
                contentAlignment = Alignment.Center,
            ) {
                CourseDetailCard(c, s, state.week)
            }
        }

        // 当天备忘录编辑页（全屏覆盖层，最顶层）
        dayEditor?.let { wd ->
            DayEditor(state, weekday = wd, onClose = { dayEditor = null })
        }
    }
}

/** 信号黄同步按钮（右栏底部） */
@Composable
private fun SyncButton(state: AppState, onClick: () -> Unit) {
    val pal = squPalette
    Box(
        Modifier.fillMaxWidth().height(44.dp)
            .clip(CutCornerShape(10.dp))
            .background(if (state.syncing) pal.fg3 else pal.accent)
            .clickable(enabled = !state.syncing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (state.syncing) "SYNCING…" else "⟳ 同步课表 / SYNC",
            color = pal.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp, fontFamily = FontFamily.Monospace,
        )
    }
}

/** 头部：品牌 + 折叠/主题/周控制 */
@Composable
private fun Header(state: AppState) {
    val pal = squPalette
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(10.dp).height(22.dp).background(pal.accent))
            Spacer(Modifier.width(8.dp))
            Text(
                "ZERO://TERMINAL", color = pal.fg, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            HeaderButton(
                if (state.darkTheme) "浅色" else "深色",
            ) { state.toggleTheme() }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(
                if (state.blockMode) "大节 BLOCK" else "小节 PERIOD",
                active = state.blockMode,
            ) { state.blockMode = !state.blockMode }
            Spacer(Modifier.weight(1f))
            HeaderButton("◀") { state.changeWeek(-1) }
            Text(
                "WEEK %02d".format(state.week),
                color = pal.fg, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HeaderButton("▶") { state.changeWeek(1) }
            // 不在当前周时显示「回本周」
            state.table?.let { t ->
                if (state.week != t.currentWeek) {
                    Spacer(Modifier.width(8.dp))
                    HeaderButton("回本周", active = true) { state.goCurrentWeek() }
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
}

/** 底部「下一节」条：点击或上划打开今日抽屉 */
@Composable
private fun NextBar(state: AppState, onClick: () -> Unit, onSwipeUp: () -> Unit = onClick) {
    val pal = squPalette
    val table = state.table ?: return
    val density = LocalDensity.current
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val todayWd = now.dayOfWeek.ordinal + 1
    val nowMin = now.hour * 60 + now.minute
    val viewingCurrentWeek = state.week == table.currentWeek

    val next = remember(table, state.week) {
        table.courses.asSequence()
            .flatMap { c -> c.segments.filter { it.weekday == todayWd && state.week in it.weeks }.map { c to it } }
            .sortedBy { it.second.periodStart }
            .firstOrNull {
                !viewingCurrentWeek ||
                    cn.squ.coursetable.ui.courseStateOf(it.second, nowMin) != cn.squ.coursetable.ui.CourseState.DONE
            }
    }

    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, pal.line)
            .background(pal.panel)
            .pointerInput(Unit) {
                // 上划打开今日抽屉
                var acc = 0f
                detectVerticalDragGestures(
                    onDragStart = { acc = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        acc += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        if (acc < -with(density) { 36.dp.toPx() }) onSwipeUp()
                        acc = 0f
                    },
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("NEXT ▸", color = pal.fg3, fontSize = 10.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(10.dp))
        Text(
            if (next != null)
                "${CourseColors.shortName(next.first.name)} · ${next.second.room} · ${Periods.start(next.second.periodStart)}"
            else "今日课程已结束",
            color = pal.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** 课程详情卡（对应设计稿 popover） */
@Composable
private fun CourseDetailCard(course: Course, seg: CourseSegment, week: Int) {
    val pal = squPalette
    val cc = CourseColors.of(course.name)
    val shape = CutCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth(.86f)
            .clip(shape)
            .background(pal.panel)
            .border(1.dp, pal.line, shape)
            .padding(18.dp),
    ) {
        Text(
            "CODE ${course.code.ifBlank { "—" }} · W.%02d".format(week),
            color = pal.accent, fontSize = 9.sp, letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(course.name, color = pal.fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (course.className.isNotBlank()) {
            Text("教学班 ${course.className}", color = cc, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        Spacer(Modifier.height(10.dp))
        DetailRow("TEACHER", course.teacher.ifBlank { "—" })
        DetailRow("PLACE", "${seg.campus} ${seg.room}")
        DetailRow("TIME", "星期${"一二三四五六日"[seg.weekday - 1]} ${seg.periodStart}~${seg.periodEnd}节")
        DetailRow("CLOCK", "${Periods.start(seg.periodStart)} ~ ${Periods.end(seg.periodEnd)}")
        DetailRow("WEEKS", course.weeksInfo.ifBlank { "—" })
        DetailRow("DEPT", course.department.ifBlank { "—" })
        DetailRow("PROP", course.property.ifBlank { "—" })
    }
}

@Composable
private fun DetailRow(k: String, v: String) {
    val pal = squPalette
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            k.padEnd(8), color = pal.fg3, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.width(72.dp),
        )
        Text(v, color = pal.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 终端风小按钮：切角 + 1px 描边；active 时信号黄实底 */
@Composable
private fun HeaderButton(text: String, active: Boolean = false, onClick: () -> Unit) {
    val pal = squPalette
    val shape = CutCornerShape(6.dp)
    Box(
        Modifier.clip(shape)
            .background(if (active) pal.accent else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (active) pal.accent else pal.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (active) pal.onAccent else pal.fg2,
            fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
        )
    }
}
