package cn.squ.coursetable.network

/**
 * 宿迁学院统一身份认证（Apereo CAS）登录。
 *
 * 流程（已实测）：
 * 1. GET 业务入口（如 jwgl 学生首页）→ 302 到 authserver 登录页
 * 2. 从登录页 HTML 提取隐藏字段 lt / execution
 * 3. POST 表单（密码明文；触发风控时需附 captchaResponse）
 * 4. 成功后跟随跳转回到业务系统，cookie 由 SchoolSession 手动维护
 *
 * 验证码机制（实测页面结构）：
 * - 登录页有 id=CaptchaDiv，默认 style="display: none;"
 * - 触发风控后该 div 显示，图片地址为 /authserver/captcha.html?ts=xxx
 * - 表单字段名 captchaResponse
 */
class CasAuth(private val session: SchoolSession) {

    sealed class Result {
        data object Success : Result()
        /** 停留在登录页：密码错误等 */
        data class Failed(val reason: String) : Result()
        /** 触发了图形验证码：附带验证码图片字节，需要用户输入后重试 */
        data class NeedCaptcha(val imageBytes: ByteArray?, val reason: String) : Result()
    }

    private fun extractHidden(html: String, name: String): String? =
        Regex("""name="$name" value="([^"]*)"""").find(html)?.groupValues?.get(1)

    /** 登录页是否要求图形验证码（CaptchaDiv 未隐藏） */
    private fun captchaRequired(html: String): Boolean {
        val style = Regex("""CaptchaDiv"\s+style="([^"]*)"""").find(html)
            ?.groupValues?.get(1) ?: return false
        return !style.contains("none")
    }

    /** 拉验证码图片（绑定当前会话 cookie） */
    private suspend fun fetchCaptcha(loginUrl: String): ByteArray? {
        val base = loginUrl.substringBefore("/authserver")
        return runCatching {
            session.getBytes("$base/authserver/captcha.html?ts=${(1..999999).random()}")
        }.getOrNull()?.takeIf { it.size > 100 }
    }

    /**
     * @param entryUrl 业务系统入口，会被重定向到 CAS 登录页
     * @param captcha 用户输入的图形验证码（NeedCaptcha 后重试时传入）
     */
    suspend fun login(
        entryUrl: String,
        username: String,
        password: String,
        captcha: String? = null,
    ): Result {
        // 1) 拿登录页（SchoolSession 已跟随重定向）
        val loginPage = session.get(entryUrl)
        val loginUrl = loginPage.url

        if (!loginUrl.contains("authserver")) {
            // 已是登录态（cookie 未过期）
            return Result.Success
        }

        val html = loginPage.body
        if (captcha == null && captchaRequired(html)) {
            return Result.NeedCaptcha(fetchCaptcha(loginUrl), "该账号需要输入验证码")
        }

        val lt = extractHidden(html, "lt")
            ?: return Result.Failed("登录页结构异常：找不到 lt 字段")
        val execution = extractHidden(html, "execution") ?: "e1s1"

        // 2) 提交表单
        val form = mutableMapOf(
            "username" to username,
            "password" to password,
            "lt" to lt,
            "dllt" to "userNamePasswordLogin",
            "execution" to execution,
            "_eventId" to "submit",
            "rmShown" to "1",
        )
        if (captcha != null) form["captchaResponse"] = captcha
        val resp = session.postForm(loginUrl, form)

        return if (resp.url.contains("authserver") && resp.url.contains("login")) {
            if (captchaRequired(resp.body)) {
                // 验证码错误，或风控仍未解除 → 换新验证码让用户重试
                val reason = if (captcha != null) "验证码错误，请重新输入" else "该账号需要输入验证码"
                Result.NeedCaptcha(fetchCaptcha(loginUrl), reason)
            } else {
                val reason = Regex("""id="msg"[^>]*>([^<]+)""").find(resp.body)
                    ?.groupValues?.get(1)?.trim()
                    ?: "账号或密码错误"
                Result.Failed(reason)
            }
        } else {
            Result.Success
        }
    }
}
