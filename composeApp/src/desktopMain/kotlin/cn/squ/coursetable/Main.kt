package cn.squ.coursetable

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import javax.imageio.ImageIO

fun main() = application {
    val windowIcon = remember {
        runCatching {
            ImageIO.read(javaClass.getResource("/icon.png")).toPainter()
        }.getOrNull()
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "零终端",
        icon = windowIcon,
        // 默认宽屏：触发周视图 + 右侧今日栏布局（阈值 980dp）
        state = rememberWindowState(size = DpSize(1360.dp, 860.dp)),
    ) {
        App()
    }
}
