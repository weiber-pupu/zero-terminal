package cn.squ.coursetable.storage

import android.content.Context
import java.io.File

/** Android 端：存到应用私有目录 filesDir/storage/ 下，使用前需 init(context) */
actual object PlatformStorage {
    private lateinit var dir: File
    /** 全局上下文（供小组件/后台任务使用） */
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        dir = File(context.filesDir, "storage")
    }

    private fun file(name: String): File {
        check(::dir.isInitialized) { "PlatformStorage 未初始化，请在 Application/Activity 中调用 init(context)" }
        require(!name.contains("..") && !name.contains("/") && !name.contains("\\")) {
            "非法存储名: $name"
        }
        return File(dir, name)
    }

    actual fun readText(name: String): String? =
        runCatching {
            val f = file(name)
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        }.getOrNull()

    actual fun writeText(name: String, text: String) {
        dir.mkdirs()
        file(name).writeText(text, Charsets.UTF_8)
    }

    actual fun delete(name: String) {
        runCatching { file(name).delete() }
    }
}
