package cn.squ.coursetable.storage

/** 各平台提供键值文本存储（desktop=用户目录文件，android=应用私有目录） */
expect object PlatformStorage {
    fun readText(name: String): String?
    fun writeText(name: String, text: String)
    fun delete(name: String)
}
