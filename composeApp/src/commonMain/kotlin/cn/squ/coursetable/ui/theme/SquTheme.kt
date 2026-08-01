package cn.squ.coursetable.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 「终端 / TERMINAL」配色 —— 设计规范 v0.5 冻结值。
 * 深色为主模式；浅色 accent 压暗保证对比度。
 */
data class SquPalette(
    val bg: Color,
    val panel: Color,
    val card: Color,
    val card2: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val accent: Color,   // 信号黄
    val green: Color,    // 荧光绿
    val pink: Color,     // 霓虹粉
    val line: Color,
    val isDark: Boolean,
) {
    /** accent 实底上的文字色（黄底用深字） */
    val onAccent: Color get() = Color(0xFF191919)
}

val DarkPalette = SquPalette(
    bg = Color(0xFF191919),
    panel = Color(0xFF35373C),
    card = Color(0xFF202126),
    card2 = Color(0xFF26272D),
    fg = Color(0xFFFFFFFF),
    fg2 = Color(0xFFB3B3B3),
    fg3 = Color(0xFF666666),
    accent = Color(0xFFFFFA00),
    green = Color(0xFF00FFA2),
    pink = Color(0xFFFF1AAC),
    line = Color(0xFF35373C),
    isDark = true,
)

/**
 * 浅色「工程图纸」风：冷调蓝灰底 + 更强网格存在感，
 * 信号黄压暗后作为绝对视觉焦点，避免"全白没特点"。
 */
val LightPalette = SquPalette(
    bg = Color(0xFFE9ECF0),
    panel = Color(0xFFDDE1E7),
    card = Color(0xFFF7F8FA),
    card2 = Color(0xFFFFFFFF),
    fg = Color(0xFF1A1D24),
    fg2 = Color(0xFF4A5160),
    fg3 = Color(0xFF8A93A3),
    accent = Color(0xFFD8C400),
    green = Color(0xFF00A66B),
    pink = Color(0xFFE00085),
    line = Color(0xFFB9C0CC),
    isDark = false,
)

val LocalSquPalette = staticCompositionLocalOf { DarkPalette }

/** 当前调色板（Composable 作用域内使用） */
val squPalette: SquPalette
    @Composable get() = LocalSquPalette.current

@Composable
fun SquTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSquPalette provides if (dark) DarkPalette else LightPalette) {
        content()
    }
}
