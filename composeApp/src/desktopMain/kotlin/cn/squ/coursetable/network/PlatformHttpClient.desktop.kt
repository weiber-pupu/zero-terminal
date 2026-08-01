package cn.squ.coursetable.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*

actual fun createPlatformHttpClient(): HttpClient = HttpClient(CIO) {
    // 重定向和 cookie 由 SchoolSession 手动管理，引擎层全部关闭
    followRedirects = false
}
