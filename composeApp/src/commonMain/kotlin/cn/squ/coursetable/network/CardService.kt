package cn.squ.coursetable.network

/**
 * 校园卡余额 —— 走 ehall 网上办事大厅（金智 myyktzd 应用）。
 *
 * 实测链路（与教务 CAS 同一套统一身份认证，免圈存密码/验证码）：
 * 1. CAS 登录：entryUrl 直接给余额 API，未登录会 302 到 authserver
 * 2. GET /publicapp/sys/myyktzd/api/getOverviewInfo.do
 *    返回 {"datas":{"KH":"0833498677","KNYE":7.54,"MC":"在用",...},"remining":"7.54",...}
 */
class EhallCardService {

    private val session = SchoolSession(createPlatformHttpClient())

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val message: String) : Result<Nothing>()
    }

    data class CardInfo(
        val balance: Double,
        val cardNo: String,      // 卡号（一卡通卡号，非学号）
        val statusName: String,  // 在用 / 挂失 等
    )

    /** 用教务统一身份认证账号查余额 */
    suspend fun fetch(username: String, password: String): Result<CardInfo> {
        try {
            when (val r = CasAuth(session).login(API_URL, username, password)) {
                is CasAuth.Result.Failed -> return Result.Err("统一认证登录失败：${r.reason}")
                is CasAuth.Result.NeedCaptcha ->
                    return Result.Err("统一认证触发验证码，请先在课表页重新登录")
                CasAuth.Result.Success -> Unit
            }
            val resp = session.get(API_URL)
            return if (resp.url.contains("authserver")) {
                Result.Err("会话异常：被重定向回登录页")
            } else {
                parse(resp.body)?.let { Result.Ok(it) }
                    ?: Result.Err("余额接口返回异常：${resp.body.take(80)}")
            }
        } catch (e: Exception) {
            return Result.Err("网络异常：${e.message?.take(60)}")
        }
    }

    companion object {
        const val API_URL = "http://ehall.squ.edu.cn/publicapp/sys/myyktzd/api/getOverviewInfo.do"

        fun parse(json: String): CardInfo? {
            val balance = Regex(""""remining"\s*:\s*"?([\d.]+)"?""").find(json)
                ?.groupValues?.get(1)?.toDoubleOrNull()
                ?: Regex(""""KNYE"\s*:\s*"?([\d.]+)"?""").find(json)
                    ?.groupValues?.get(1)?.toDoubleOrNull()
                ?: return null
            val cardNo = Regex(""""cardnum"\s*:\s*"([^"]*)"""").find(json)
                ?.groupValues?.get(1)
                ?: Regex(""""KH"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1).orEmpty()
            val status = Regex(""""cardstatusname"\s*:\s*"([^"]*)"""").find(json)
                ?.groupValues?.get(1)
                ?: Regex(""""MC"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1).orEmpty()
            return CardInfo(balance, cardNo, status)
        }
    }
}
