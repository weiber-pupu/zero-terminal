package cn.squ.coursetable.ui

import androidx.compose.ui.graphics.Color

/**
 * 课程专属色板 —— 设计规范 §1「课程色板」。
 * 每门课按课程名 hash 稳定分配，与 HTML 设计稿同一套色值。
 */
object CourseColors {

    private val PALETTE = listOf(
        Color(0xFFFF9E64), Color(0xFFFFFA00), Color(0xFF00FFA2),
        Color(0xFF4FC3F7), Color(0xFF7C8CFF), Color(0xFFB98CFF),
        Color(0xFFFF6EB4), Color(0xFFFF5A5A), Color(0xFF8CE99A),
        Color(0xFFFFD43B),
    )

    /** 与 mockup JS 相同的 hash：h = h*31 + code，按无符号 32 位取模 */
    fun of(name: String): Color {
        var h = 0
        for (ch in name) h = h * 31 + ch.code
        val idx = (h.toUInt() % PALETTE.size.toUInt()).toInt()
        return PALETTE[idx]
    }

    /** 卡片上的短课程名：取 "-" 前段，最长 14 字 */
    fun shortName(name: String): String =
        name.split("-")[0].take(14)

    // ---------- 移动端极小卡片缩写 ----------

    /**
     * 常见课程名缩写表（移动端窄卡片用）。
     * key 为课程名去括号后的前缀；新增课程时按需补充。
     */
    private val ALIASES = linkedMapOf(
        "高等数学" to "高数",
        "线性代数" to "线代",
        "概率论与数理统计" to "概率论",
        "离散数学" to "离散",
        "大学英语" to "大英",
        "英语拓展课程" to "英语拓展",
        "大学体育" to "体育",
        "思想道德与法治" to "思政",
        "思想政治理论课实践" to "思政实践",
        "马克思主义基本原理" to "马原",
        "毛泽东思想和中国特色社会主义理论体系概论" to "毛概",
        "中国近现代史纲要" to "史纲",
        "大学生心理素质训练" to "心理",
        "大学生心理健康教育" to "心理",
        "Python程序设计" to "Python",
        "C语言程序设计" to "C语言",
        "电路与模拟电子技术" to "电路",
        "数字电子技术" to "数电",
        "模拟电子技术" to "模电",
        "数据结构" to "数据结构",
        "计算机组成原理" to "计组",
        "计算机网络" to "计网",
        "操作系统" to "OS",
        "数据库原理" to "数据库",
        "劳动教育" to "劳动",
        "军事理论" to "军理",
        "形势与政策" to "形策",
        "东方舞塑性与健身" to "东方舞",
    )

    /**
     * 移动端窄卡片缩写名：
     * 先查缩写表；册别标记压缩为 Ⅰ/Ⅱ/Ⅲ + 上/下（如 "高等数学 I（下）"→"高数Ⅰ下"），
     * 未命中则去括号取前段，最长 6 字。
     */
    fun tinyName(name: String): String {
        val base = name.split("-")[0].trim()
        // 册别：罗马数字 + 上/下 册
        val roman = when (Regex("""(?:^|\s)(I{1,3}|IV)(?=\s|[（(]|$)""").find(base)?.value?.trim()) {
            "I" -> "Ⅰ"; "II" -> "Ⅱ"; "III" -> "Ⅲ"; "IV" -> "Ⅳ"; else -> ""
        }
        val volume = when {
            base.contains("（下）") || base.contains("(下)") -> "下"
            base.contains("（上）") || base.contains("(上)") -> "上"
            else -> ""
        }
        val suffix = roman + volume
        val stem = base
            .replace(Regex("""[（(][上下][）)]"""), "")
            .replace(Regex("""\s+I{1,3}\s*$"""), "")
            .trim()
        for ((full, abbr) in ALIASES) {
            if (stem.startsWith(full)) return (abbr + suffix).take(7)
        }
        // 未命中：去全部括号取前段
        val plain = stem.replace(Regex("""[（(].*?[）)]"""), "").trim()
        return (plain + suffix).take(6)
    }
}
