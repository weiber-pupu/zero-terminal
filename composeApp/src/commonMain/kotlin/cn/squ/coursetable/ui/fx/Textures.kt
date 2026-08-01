package cn.squ.coursetable.ui.fx

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import cn.squ.coursetable.ui.theme.SquPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 纹理层 —— 设计规范 §8.6 冻结项。
 * 等高线背景 / 单元格制图纹理 / 卡片颗粒噪点 / 扫描线。
 */

/** 页面背景：等高线 + 点阵网格 + 横向扫描线。
 *  离屏渲染成 ImageBitmap 缓存：尺寸/主题不变时不再每帧重画几千个图元
 *  （v0.4.1 之前逐帧绘制导致低端机掉帧、输入法弹起卡顿）。 */
fun Modifier.contourBackground(pal: SquPalette): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val bmp = ImageBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    val canvas = Canvas(bmp)
    CanvasDrawScope().draw(
        this@drawWithCache, LayoutDirection.Ltr, canvas, size,
    ) {
        val accentStroke = pal.accent.copy(alpha = if (pal.isDark) .07f else .13f)
        val neutral = if (pal.isDark) Color.White else Color(0xFF1A1D24)
        val neutralStroke = neutral.copy(alpha = if (pal.isDark) .06f else .08f)

        // 点阵网格（工程图纸感）
        val dotColor = neutral.copy(alpha = if (pal.isDark) .045f else .07f)
        val step = 26f
        var gx = step / 2
        while (gx < w) {
            var gy = step / 2
            while (gy < h) {
                drawCircle(dotColor, radius = .8f, center = Offset(gx, gy))
                gy += step
            }
            gx += step
        }

        // 中央嵌套等高线（5 层不规则闭合环）
        val cx = w * .45f
        val cy = h * .52f
        val baseR = minOf(w, h) * .34f
        for (layer in 0..4) {
            val r = baseR * (1f - layer * .17f)
            val p = wobblyRing(cx, cy, r, phase = layer * 1.3f)
            drawPath(p, accentStroke, style = Stroke(width = 1f))
        }
        // 右下第二组等高线（丰富层次）
        for (layer in 0..2) {
            val r = baseR * .45f * (1f - layer * .22f)
            val p = wobblyRing(w * .86f, h * .88f, r, phase = layer * 2.1f)
            drawPath(p, accentStroke.copy(alpha = accentStroke.alpha * .7f), style = Stroke(width = 1f))
        }
        // 左上 / 右下波浪线组
        for (i in 0..3) {
            drawPath(waveLine(-w * .06f, h * (.14f + i * .045f), w * .62f, amp = h * .05f, phase = i * .9f), neutralStroke, style = Stroke(1f))
            drawPath(waveLine(w * .55f, h * (.82f + i * .045f), w * .5f, amp = h * .04f, phase = i * 1.1f), neutralStroke, style = Stroke(1f))
        }
        // 扫描线
        val scan = neutral.copy(alpha = if (pal.isDark) .014f else .025f)
        var y = 3f
        while (y < h) {
            drawLine(scan, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += 4f
        }
    }
    onDrawBehind { drawImage(bmp) }
}

/** 单元格纹理：中心十字标 + 45° 细斜线（制图感） */
fun Modifier.cellTexture(pal: SquPalette): Modifier = drawBehind {
    val markColor = (if (pal.isDark) Color.White else Color(0xFF1A1D24))
        .copy(alpha = if (pal.isDark) .13f else .18f)
    val hatchColor = (if (pal.isDark) Color.White else Color(0xFF1A1D24))
        .copy(alpha = if (pal.isDark) .02f else .035f)
    // 中心十字标
    drawLine(markColor, Offset(center.x - 2.5f, center.y), Offset(center.x + 2.5f, center.y), strokeWidth = 1f)
    drawLine(markColor, Offset(center.x, center.y - 2.5f), Offset(center.x, center.y + 2.5f), strokeWidth = 1f)
    // 45° 斜线，间距 9px
    val w = size.width
    val h = size.height
    var x = -h
    while (x < w) {
        drawLine(hatchColor, Offset(x, h), Offset(x + h, 0f), strokeWidth = 1f)
        x += 9f
    }
}

/** 网格四角落取景框括号（⌐ ¬ 线框角，设计规范 §3） */
fun Modifier.cornerBrackets(pal: SquPalette): Modifier = drawBehind {
    val c = pal.accent.copy(alpha = .85f)
    val len = 14f * density
    val t = 1.5f * density
    val w = size.width
    val h = size.height
    // 左上
    drawLine(c, Offset(0f, 0f), Offset(len, 0f), t)
    drawLine(c, Offset(0f, 0f), Offset(0f, len), t)
    // 右上
    drawLine(c, Offset(w - len, 0f), Offset(w, 0f), t)
    drawLine(c, Offset(w, 0f), Offset(w, len), t)
    // 左下
    drawLine(c, Offset(0f, h - len), Offset(0f, h), t)
    drawLine(c, Offset(0f, h), Offset(len, h), t)
    // 右下
    drawLine(c, Offset(w - len, h), Offset(w, h), t)
    drawLine(c, Offset(w, h - len), Offset(w, h), t)
}

/** 全屏扫描线 flash（切周/同步转场用），alpha 由外部动画驱动 */
fun Modifier.scanlineFlash(pal: SquPalette, alpha: Float): Modifier = drawBehind {
    if (alpha <= 0f) return@drawBehind
    val c = pal.accent.copy(alpha = .06f * alpha)
    var y = 0f
    while (y < size.height) {
        drawRect(c, topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, 2f))
        y += 6f
    }
}

// ---------- 噪点颗粒 ----------

object NoiseTexture {
    private var white: ImageBitmap? = null
    private var dark: ImageBitmap? = null

    /** 卡片颗粒：深色主题用白点、浅色用黑点（设计规范 §8.2 opacity .6/.35 近似） */
    fun brush(isDarkTheme: Boolean): ShaderBrush {
        val bmp = if (isDarkTheme) {
            white ?: generate(Color.White).also { white = it }
        } else {
            dark ?: generate(Color.Black).also { dark = it }
        }
        return ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
    }

    private fun generate(dot: Color): ImageBitmap {
        val n = 120
        val bmp = ImageBitmap(n, n)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { color = dot.copy(alpha = .05f) }
        val rnd = kotlin.random.Random(42)
        repeat(n * n / 2) {
            canvas.drawCircle(
                Offset(rnd.nextFloat() * n, rnd.nextFloat() * n),
                radius = .7f,
                paint = paint,
            )
        }
        return bmp
    }
}

// ---------- 路径生成 ----------

/** 不规则闭合环（等高线一圈） */
private fun wobblyRing(cx: Float, cy: Float, r: Float, phase: Float): Path {
    val p = Path()
    val steps = 72
    for (i in 0..steps) {
        val t = i.toFloat() / steps * 2 * PI
        val wobble = 1f + .16f * sin(3 * t + phase) + .07f * sin(7 * t + phase * 2f)
        val x = cx + (r * wobble * cos(t)).toFloat()
        val y = cy + (r * .72f * wobble * sin(t)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/** 正弦波浪线 */
private fun waveLine(x0: Float, y0: Float, len: Float, amp: Float, phase: Float): Path {
    val p = Path()
    val steps = 48
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val x = x0 + len * t
        val y = y0 + (amp * sin(t * 2.2 * PI + phase)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    return p
}

/** 空 Brush 占位（避免重复构造） */
internal val EMPTY_BRUSH: Brush get() = ShaderBrush(ImageShader(ImageBitmap(1, 1)))
