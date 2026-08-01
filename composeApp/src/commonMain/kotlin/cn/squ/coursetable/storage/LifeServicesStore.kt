package cn.squ.coursetable.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 生活服务（校园卡）本地持久化 */
object LifeServicesStore {

    private const val KEY_CARD_BALANCE = "life_card_balance_v2.json"

    private val json = Json { ignoreUnknownKeys = true }

    // ---------- 校园卡（ehall 链路，凭据复用教务 CAS，无需单独存） ----------

    @Serializable
    data class BalanceCache(
        val balance: Double,
        val cardNo: String? = null,
        val statusName: String? = null,
        val fetchedAtEpochMs: Long,
    )

    fun saveCardBalance(b: BalanceCache) = PlatformStorage.writeText(KEY_CARD_BALANCE, json.encodeToString(b))
    fun loadCardBalance(): BalanceCache? =
        PlatformStorage.readText(KEY_CARD_BALANCE)
            ?.let { runCatching { json.decodeFromString<BalanceCache>(it) }.getOrNull() }
}
