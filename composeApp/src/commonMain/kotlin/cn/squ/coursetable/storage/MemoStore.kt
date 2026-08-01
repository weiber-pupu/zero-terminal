package cn.squ.coursetable.storage

import cn.squ.coursetable.model.MemoEvent
import kotlinx.serialization.json.Json

/**
 * 备忘录事件本地存储（memos.json，走 PlatformStorage，双端共享）。
 */
object MemoStore {

    private const val KEY_MEMOS = "memos.json"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun loadAll(): List<MemoEvent> =
        PlatformStorage.readText(KEY_MEMOS)
            ?.let { runCatching { json.decodeFromString<List<MemoEvent>>(it) }.getOrNull() }
            .orEmpty()

    fun saveAll(memos: List<MemoEvent>) {
        PlatformStorage.writeText(KEY_MEMOS, json.encodeToString(memos))
    }
}
