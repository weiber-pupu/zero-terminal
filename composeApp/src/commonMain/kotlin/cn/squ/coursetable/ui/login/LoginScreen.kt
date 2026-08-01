package cn.squ.coursetable.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.squ.coursetable.ui.state.AppState
import cn.squ.coursetable.ui.theme.squPalette
import cn.squ.coursetable.ui.week.CutCornerShape

/**
 * 登录页 —— 终端风：信号黄短标 + 切角输入框 + 等宽字体标签。
 */
@Composable
fun LoginScreen(state: AppState, onLogin: (String, String, String?) -> Unit) {
    val pal = squPalette
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    // 验证码刷新（换新图）时清空输入
    LaunchedEffect(state.captchaImage) { captcha = "" }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 品牌
        Box(Modifier.width(14.dp).height(28.dp).background(pal.accent))
        Spacer(Modifier.height(12.dp))
        Text(
            "ZERO://TERMINAL",
            color = pal.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Text(
            "零终端 · 宿迁学院课表终端",
            color = pal.fg3, fontSize = 10.sp, letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "办事大厅统一认证账号 · 初始密码 Sqxy+身份证后六位",
            color = pal.fg3, fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        TerminalField(
            value = username, onValueChange = { username = it },
            label = "学号 / ID", enabled = !state.syncing,
        )
        Spacer(Modifier.height(12.dp))
        TerminalField(
            value = password, onValueChange = { password = it },
            label = "密码 / PASSWORD", enabled = !state.syncing, isPassword = true,
        )

        // 图形验证码（CAS 风控触发时出现）
        state.captchaImage?.let { bytes ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(120.dp).height(48.dp)
                        .clip(CutCornerShape(8.dp))
                        .background(pal.card)
                        .border(1.dp, pal.line, CutCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = remember(bytes) {
                        runCatching { bytes.decodeToImageBitmap() }.getOrNull()
                    }
                    if (bmp != null) {
                        Image(bmp, contentDescription = "验证码", modifier = Modifier.fillMaxSize())
                    } else {
                        Text("CAPTCHA?", color = pal.fg3, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    TerminalField(
                        value = captcha, onValueChange = { captcha = it },
                        label = "验证码 / CAPTCHA", enabled = !state.syncing,
                        keyboardType = KeyboardType.Text,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // 登录按钮（信号黄实底切角）
        Box(
            Modifier.fillMaxWidth().height(46.dp)
                .clip(CutCornerShape(10.dp))
                .background(if (state.syncing) pal.fg3 else pal.accent)
                .clickable(enabled = !state.syncing && username.isNotBlank() && password.isNotBlank()) {
                    onLogin(username, password, captcha.trim().ifBlank { null })
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.syncing) "SYNCING…" else "登录并同步 / LOGIN",
                color = pal.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp, fontFamily = FontFamily.Monospace,
            )
        }

        state.statusMsg?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it, color = if (it.startsWith("✗")) pal.pink else pal.green,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun TerminalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    val pal = squPalette
    val shape = CutCornerShape(8.dp)
    var showPlain by remember { mutableStateOf(false) }
    val masked = isPassword && !showPlain
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = pal.fg, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(pal.accent),
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password)
        else KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (masked) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(48.dp)
                    .clip(shape)
                    .background(pal.card)
                    .border(1.dp, pal.line, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(label, color = pal.fg3, fontSize = 12.sp, letterSpacing = 2.sp)
                    }
                    inner()
                }
                if (isPassword) {
                    Text(
                        if (showPlain) "隐藏" else "显示",
                        color = pal.fg3, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(CutCornerShape(4.dp))
                            .clickable { showPlain = !showPlain }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        },
    )
}
