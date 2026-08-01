package cn.squ.coursetable.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.squ.coursetable.network.EhallCardService
import cn.squ.coursetable.storage.LifeServicesStore
import cn.squ.coursetable.storage.ScheduleCache
import kotlinx.datetime.Clock

/** 生活服务条目状态 */
enum class LifeStatus { UNSET, LOADING, OK, LOW, ERROR }

/**
 * 生活服务状态：校园卡余额。
 * 阈值：校园卡 < 5 元告警（红）。
 *
 * 校园卡走 ehall 办事大厅（与教务同一套统一身份认证），
 * 复用课表的 CAS 凭据，无需单独登录。
 */
class LifeServicesState {

    var cardBalance by mutableStateOf<Double?>(null); private set
    var cardNo by mutableStateOf<String?>(null); private set
    var cardStatusName by mutableStateOf<String?>(null); private set
    var cardStatus by mutableStateOf(LifeStatus.UNSET); private set
    var cardMessage by mutableStateOf<String?>(null); private set

    /** 校园卡配置 = 已有教务 CAS 凭据（无需单独配置） */
    val cardConfigured: Boolean get() = ScheduleCache.loadCredentials() != null

    /** 启动：读缓存值；已配置则后台静默刷新（节流 30 分钟） */
    suspend fun bootstrap() {
        LifeServicesStore.loadCardBalance()?.let {
            cardBalance = it.balance
            cardNo = it.cardNo
            cardStatusName = it.statusName
            cardStatus = if (it.balance < CARD_LOW_THRESHOLD) LifeStatus.LOW else LifeStatus.OK
            val ageMs = Clock.System.now().toEpochMilliseconds() - it.fetchedAtEpochMs
            if (cardConfigured && ageMs >= CARD_REFRESH_MS) refreshCardBalance()
        } ?: run {
            if (cardConfigured) refreshCardBalance()
        }
    }

    /** 刷新余额：ehall CAS 登录（复用教务凭据）→ getOverviewInfo */
    suspend fun refreshCardBalance() {
        val cred = ScheduleCache.loadCredentials()
        if (cred == null) {
            cardStatus = LifeStatus.UNSET
            cardMessage = "请先在课表页登录教务账号"
            return
        }
        cardStatus = LifeStatus.LOADING
        cardMessage = null
        when (val r = EhallCardService().fetch(cred.username, cred.password)) {
            is EhallCardService.Result.Ok -> {
                cardBalance = r.value.balance
                cardNo = r.value.cardNo.ifBlank { null }
                cardStatusName = r.value.statusName.ifBlank { null }
                cardStatus = if (r.value.balance < CARD_LOW_THRESHOLD) LifeStatus.LOW else LifeStatus.OK
                LifeServicesStore.saveCardBalance(
                    LifeServicesStore.BalanceCache(
                        balance = r.value.balance,
                        cardNo = cardNo,
                        statusName = cardStatusName,
                        fetchedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                    )
                )
            }
            is EhallCardService.Result.Err -> {
                cardStatus = cardBalance?.let {
                    if (it < CARD_LOW_THRESHOLD) LifeStatus.LOW else LifeStatus.OK
                } ?: LifeStatus.ERROR
                cardMessage = r.message
            }
        }
    }

    companion object {
        const val CARD_LOW_THRESHOLD = 5.0
        private const val CARD_REFRESH_MS = 30L * 60 * 1000
    }
}
