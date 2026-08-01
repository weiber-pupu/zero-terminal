package cn.squ.coursetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.squ.coursetable.island.CourseReminderScheduler
import cn.squ.coursetable.island.IslandNotifier
import cn.squ.coursetable.storage.PlatformStorage
import cn.squ.coursetable.widget.SyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlatformStorage.init(applicationContext)
        SyncWorker.enqueue(applicationContext)
        IslandNotifier.ensureChannel(applicationContext)
        // Android 13+ 通知运行时权限（超级岛/上课提醒依赖）
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        // 启动时按缓存课表重排今天的提醒（覆盖闹钟被杀/时间漂移）
        CourseReminderScheduler.scheduleToday(applicationContext)
        setContent {
            App()
        }
    }
}
