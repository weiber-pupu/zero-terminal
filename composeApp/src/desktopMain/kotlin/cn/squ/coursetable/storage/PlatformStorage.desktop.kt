package cn.squ.coursetable.storage

import java.nio.file.Files
import java.nio.file.Path

/** 桌面端：存到用户主目录 ~/.squ-course-table/ 下 */
actual object PlatformStorage {
    private val dir: Path = Path.of(System.getProperty("user.home"), ".squ-course-table")

    private fun file(name: String): Path {
        require(!name.contains("..") && !name.contains("/") && !name.contains("\\")) {
            "非法存储名: $name"
        }
        return dir.resolve(name)
    }

    actual fun readText(name: String): String? =
        runCatching {
            val f = file(name)
            if (Files.exists(f)) Files.readString(f, Charsets.UTF_8) else null
        }.getOrNull()

    actual fun writeText(name: String, text: String) {
        Files.createDirectories(dir)
        Files.writeString(file(name), text, Charsets.UTF_8)
    }

    actual fun delete(name: String) {
        runCatching { Files.deleteIfExists(file(name)) }
    }
}
