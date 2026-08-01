package cn.squ.coursetable.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.squ.coursetable.MainActivity
import org.json.JSONObject

/**
 * 澎湃超级岛 / MIUI 焦点通知（参考 mikcb 实现）。
 *
 * 要点：
 * - 普通通知走 Notification.Builder + LOW 渠道；
 * - 小米/Redmi/POCO 设备在 extras 注入 "miui.focus.param"（param_v2 JSON），
 *   即可在状态栏超级岛区域显示（摘要态 island + 展开态 baseInfo/hintInfo）；
 * - 非小米设备退化为普通 ongoing 提醒通知。
 */
object IslandNotifier {

    const val CHANNEL_ID = "course_live"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_LOW).apply {
                description = "课前预警与上课中的超级岛/焦点通知"
            }
        )
    }

    /** 小米系设备判定（HyperOS/MIUI 焦点通知仅在这些设备生效） */
    fun isXiaomiFamily(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manu = Build.MANUFACTURER.lowercase()
        return manu.contains("xiaomi") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco")
    }

    /**
     * 发/更新上课提醒通知。
     * @param stage "warn" 课前预警 / "during" 上课中
     */
    fun post(
        context: Context,
        notifId: Int,
        stage: String,
        courseName: String,
        room: String,
        teacher: String,
        timeText: String,      // "10:00~11:40"
        startMs: Long,
        endMs: Long,
    ) {
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val hint = when (stage) {
            "warn" -> {
                val left = ((startMs - now) / 60000).coerceAtLeast(1)
                "${left} 分钟后上课"
            }
            else -> {
                val left = ((endMs - now) / 60000).coerceAtLeast(1)
                "上课中 · ${left} 分钟后下课"
            }
        }
        val title = (if (stage == "warn") "即将上课 · " else "上课中 · ") + courseName
        val content = "$room · $timeText"

        val pi = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pi)
            .setOngoing(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(if (stage == "warn") startMs else endMs)
            .setTimeoutAfter(20 * 60 * 1000L) // 弹窗式提醒：20 分钟后自动消失
            .setCategory(
                if (stage == "during") Notification.CATEGORY_REMINDER
                else Notification.CATEGORY_STATUS
            )
            .setStyle(Notification.BigTextStyle().bigText("$content\n$hint"))

        val notification = builder.build()
        if (isXiaomiFamily()) {
            notification.extras.putString(
                "miui.focus.param",
                buildFocusParam(title, content, hint, courseName, room, teacher, timeText, stage, startMs, endMs),
            )
        }
        context.getSystemService(NotificationManager::class.java)?.notify(notifId, notification)
    }

    fun cancel(context: Context, notifId: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notifId)
    }

    /** 焦点通知 param_v2 JSON（结构参照 mikcb） */
    private fun buildFocusParam(
        title: String,
        content: String,
        hint: String,
        courseName: String,
        room: String,
        teacher: String,
        timeText: String,
        stage: String,
        startMs: Long,
        endMs: Long,
    ): String = try {
        val progress = if (stage == "during") {
            val total = (endMs - startMs).coerceAtLeast(1)
            (((System.currentTimeMillis() - startMs).coerceIn(0, total).toDouble() / total) * 100)
                .toInt().coerceIn(0, 100)
        } else null

        val imageTextInfoLeft = JSONObject().apply {
            put("type", 1)
            put("textInfo", JSONObject().apply {
                put("title", courseName)
                put("content", hint)
            })
            if (progress != null) {
                put("progressInfo", JSONObject().apply {
                    put("progress", progress)
                    put("colorReach", "#00FFA2")
                    put("colorUnReach", "#33FFFFFF")
                })
            }
        }

        val paramIsland = JSONObject().apply {
            put("islandProperty", 1)
            put("islandTimeout", 3600)
            put("bigIslandArea", JSONObject().apply {
                put("imageTextInfoLeft", imageTextInfoLeft)
            })
            put("smallIslandArea", JSONObject())
        }

        val extraInfo = JSONObject().apply {
            if (room.isNotBlank()) put("location", room)
            if (teacher.isNotBlank()) put("teacher", teacher)
            if (timeText.isNotBlank()) put("time", timeText)
        }

        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("business", "course_remind")
            put("updatable", true)
            put("enableFloat", true)
            put("ticker", title)
            put("baseInfo", JSONObject().apply {
                put("type", 2)
                put("title", title)
                put("content", content)
            })
            put("hintInfo", JSONObject().apply {
                put("type", 1)
                put("title", hint)
            })
            if (extraInfo.length() > 0) put("extraInfo", extraInfo)
            put("param_island", paramIsland)
        }

        JSONObject().apply { put("param_v2", paramV2) }.toString()
    } catch (e: Exception) {
        "{}"
    }
}
