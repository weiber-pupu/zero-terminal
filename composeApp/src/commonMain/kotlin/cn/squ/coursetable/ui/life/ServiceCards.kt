package cn.squ.coursetable.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.squ.coursetable.ui.state.LifeServicesState
import cn.squ.coursetable.ui.state.LifeStatus
import cn.squ.coursetable.ui.theme.squPalette
import cn.squ.coursetable.ui.week.CutCornerShape
import kotlinx.coroutines.launch

/**
 * 左下生活服务卡片：校园卡余额。
 * 绿=正常，红=低于 5 元阈值，灰=未配置。
 */
@Composable
fun ServiceCards(life: LifeServicesState, modifier: Modifier = Modifier) {
    var showCardDialog by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ServiceChip(
            label = "CARD",
            status = life.cardStatus,
            value = when {
                life.cardStatus == LifeStatus.UNSET -> "未配置"
                life.cardBalance != null -> "¥%.2f".format(life.cardBalance)
                life.cardStatus == LifeStatus.LOADING -> "···"
                else -> "异常"
            },
            onClick = { showCardDialog = true },
        )
    }

    if (showCardDialog) {
        CardConfigDialog(life) { showCardDialog = false }
    }
}

@Composable
private fun ServiceChip(
    label: String,
    status: LifeStatus,
    value: String,
    onClick: () -> Unit,
) {
    val pal = squPalette
    val color = when (status) {
        LifeStatus.OK -> pal.green
        LifeStatus.LOW -> pal.pink
        LifeStatus.LOADING -> pal.accent
        LifeStatus.ERROR -> pal.pink.copy(alpha = .7f)
        LifeStatus.UNSET -> pal.fg3
    }
    Row(
        Modifier.clip(CutCornerShape(6.dp))
            .background(if (pal.isDark) Color(0xD9141416) else Color(0xE6FFFFFF))
            .border(1.dp, pal.line, CutCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = pal.fg3, fontSize = 8.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(6.dp))
        Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

// ---------- 校园卡配置对话框 ----------

@Composable
private fun CardConfigDialog(life: LifeServicesState, onClose: () -> Unit) {
    val pal = squPalette
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    LifeDialogShell(title = "校园卡 / CARD", onClose = onClose) {
        if (life.cardConfigured) {
            StatusLine("余额", life.cardBalance?.let { "¥%.2f".format(it) } ?: "—", pal)
            life.cardNo?.let { StatusLine("卡号", it, pal) }
            life.cardStatusName?.let { StatusLine("状态", it, pal) }
            Text("数据源：ehall 办事大厅（与教务同账号，自动登录）", color = pal.fg3, fontSize = 9.sp)
            life.cardMessage?.let { Text(it, color = pal.pink, fontSize = 10.sp) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeButton(if (busy) "查询中…" else "⟳ 刷新") {
                    if (busy) return@LifeButton
                    busy = true
                    scope.launch { life.refreshCardBalance(); busy = false }
                }
            }
        } else {
            Text(
                "校园卡余额复用教务统一身份认证自动查询。\n请先在课表页登录教务账号。",
                color = pal.fg2, fontSize = 11.sp, lineHeight = 16.sp,
            )
        }
    }
}

// ---------- 共用小组件 ----------

@Composable
private fun LifeDialogShell(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pal = squPalette
    Box(
        Modifier.fillMaxWidth().background(pal.bg.copy(alpha = .6f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(.88f)
                .clip(CutCornerShape(12.dp))
                .background(pal.panel)
                .border(1.dp, pal.line, CutCornerShape(12.dp))
                .border(2.dp, pal.accent.copy(alpha = .8f), CutCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = pal.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "✕", color = pal.fg3, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onClose).padding(4.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
            content()
        }
    }
}

@Composable
private fun StatusLine(k: String, v: String, pal: cn.squ.coursetable.ui.theme.SquPalette) {
    Row {
        Text(k, color = pal.fg3, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(72.dp))
        Text(v, color = pal.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LifeInput(
    label: String,
    value: String,
    keyboard: KeyboardType,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    val pal = squPalette
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = TextFieldDefaults.colors(
            focusedTextColor = pal.fg, unfocusedTextColor = pal.fg,
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = pal.accent, unfocusedIndicatorColor = pal.line,
            cursorColor = pal.accent,
            focusedLabelColor = pal.accent, unfocusedLabelColor = pal.fg3,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(max = 58.dp),
    )
}

@Composable
private fun LifeButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val pal = squPalette
    val c = if (danger) pal.pink else pal.accent
    Box(
        Modifier.clip(CutCornerShape(6.dp))
            .border(1.dp, c, CutCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = c, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
    }
}
