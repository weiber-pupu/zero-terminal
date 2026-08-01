package cn.squ.coursetable.ui.fx

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import cn.squ.coursetable.ui.theme.SquPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 故障转场（glitch transition）—— 切周 / 切大小节 / 切主题时的全屏过渡。
 * 风格：「信号显示错误」——RGB 通道分离 + 花屏色块 + 反相闪烁 + 滚动亮带。
 *
 * 由外部 Animatable(1f→0f) 驱动 progress，~0.45s。
 * 所有"随机"用正弦叠加生成（确定性，无帧间跳动）。
 */
fun Modifier.glitchOverlay(pal: SquPalette, progress: Float): Modifier = drawBehind {
    if (progress <= 0f) return@drawBehind
    val w = size.width
    val h = size.height
    val t = 1f - progress // 0→1
    val strong = progress * progress // 前期强后期快速收敛

    // ---- 1. RGB 通道分离切片（8 条，位错大、带品红/绿双色拖影） ----
    var seed = 3.7f
    for (i in 0 until 8) {
        seed = (seed * 1.93f + 1.17f) % 7.31f
        val bandY = h * ((seed * 0.137f + i * 0.121f) % 1f)
        val bandH = h * (0.010f + (seed % 1.7f) * 0.02f)
        val dir = if (i % 2 == 0) 1f else -1f
        val dx = dir * w * 0.09f * strong * abs(sin(t * PI.toFloat() * 2 + i))
        // 通道分离：品红左拖、绿色右拖、本体微亮
        drawRect(
            pal.pink.copy(alpha = 0.28f * strong),
            topLeft = Offset(dx - 6f * strong, bandY),
            size = Size(w, bandH),
        )
        drawRect(
            pal.green.copy(alpha = 0.24f * strong),
            topLeft = Offset(dx + 6f * strong, bandY + bandH * .5f),
            size = Size(w, bandH * .5f),
        )
        drawRect(
            Color.White.copy(alpha = 0.06f * strong),
            topLeft = Offset(dx, bandY),
            size = Size(w, bandH),
        )
    }

    // ---- 2. 花屏色块（digital corruption）：伪随机矩形坏点群 ----
    seed = 11.3f
    for (i in 0 until 10) {
        seed = (seed * 2.71f + 3.31f) % 13.7f
        val bx = w * ((seed * 0.731f) % 1f)
        val by = h * ((seed * 0.377f + i * 0.093f) % 1f)
        val bw = w * (0.02f + (seed % 1.3f) * 0.05f)
        val bh = h * (0.004f + (seed % 0.9f) * 0.012f)
        val c = when (i % 3) {
            0 -> pal.accent
            1 -> pal.pink
            else -> pal.green
        }
        drawRect(c.copy(alpha = 0.35f * strong), topLeft = Offset(bx, by), size = Size(bw, bh))
    }

    // ---- 3. 滚动亮带（vertical hold 感）：一条宽带从上到下滚过 ----
    if (progress > .35f) {
        val rollY = h * t * 1.2f - h * .1f
        drawRect(
            Color.White.copy(alpha = 0.08f * strong),
            topLeft = Offset(0f, rollY),
            size = Size(w, h * .05f),
        )
        drawRect(
            pal.accent.copy(alpha = 0.15f * strong),
            topLeft = Offset(0f, rollY - 3f),
            size = Size(w, 2f),
        )
    }

    // ---- 4. 反相/白噪闪烁（前 50% 时段的"显示错误"帧） ----
    if (t < .5f) {
        val flicker = sin(t * PI.toFloat() * 10) > 0f
        if (flicker) {
            // 全屏微弱反相感（浅色压暗、深色提亮）
            drawRect(
                (if (pal.isDark) Color.White else Color.Black).copy(alpha = 0.06f * strong),
                size = size,
            )
        }
        // 细密白噪横线
        val a = 0.045f * strong
        var y = if (flicker) 0f else 3f
        while (y < h) {
            drawRect(Color.White.copy(alpha = a), topLeft = Offset(0f, y), size = Size(w, 1f))
            y += 6f
        }
    }
}

/** 转场期整体抖动位移（挂在内容层），progress 1→0 时抖动收敛 */
fun Modifier.glitchJitter(progress: Float): Modifier = graphicsLayer {
    if (progress > 0f) {
        val t = 1f - progress
        translationX = sin(t * PI.toFloat() * 5f) * progress * 8f
    }
}
