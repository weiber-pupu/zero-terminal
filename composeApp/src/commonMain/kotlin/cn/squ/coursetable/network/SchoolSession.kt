package cn.squ.coursetable.network

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
/**
 * 手动管理 cookie 和重定向的 HTTP 会话。
 *
 * 为什么不用 Ktor 自带的 HttpCookies + followRedirects：
 * 实测 CAS 长跳转链（5+ 跳）中间 302 设置的 cookie 不会被插件保存，
 * 导致"登录成功但 session 丢失"。手动管理后行为完全确定。
 */
class SchoolSession(private val client: HttpClient) {

    data class Response(
        val status: Int,
        val url: String,      // 最终 URL（跟随重定向后）
        val body: String,
        val headers: Headers,
    )

    /** domain -> (name -> value) */
    private val jar = mutableMapOf<String, MutableMap<String, String>>()

    private fun storeCookies(setCookieHeaders: List<String>, requestHost: String) {
        for (header in setCookieHeaders) {
            val pair = header.substringBefore(";")
            val name = pair.substringBefore("=").trim()
            val value = pair.substringAfter("=", "").trim()
            if (name.isEmpty()) continue
            val domainAttr = header.split(";")
                .map { it.trim() }
                .find { it.lowercase().startsWith("domain=") }
                ?.substringAfter("=")
                ?.removePrefix(".")
                ?.lowercase()
            val domain = domainAttr ?: requestHost.lowercase()
            jar.getOrPut(domain) { mutableMapOf() }[name] = value
        }
    }

    private fun cookieHeaderFor(host: String): String {
        val h = host.lowercase()
        return jar.entries
            .filter { (domain, _) -> h == domain || h.endsWith(".$domain") }
            .flatMap { it.value.entries }
            .joinToString("; ") { (name, value) -> "$name=$value" }
    }

    /** 当前已保存的 cookie 名（调试用） */
    fun cookieNames(): Map<String, Set<String>> = jar.mapValues { it.value.keys.toSet() }

    /**
     * 发起请求并手动跟随重定向（最多 10 跳），每一跳都存/带 cookie。
     */
    private suspend fun execute(
        method: HttpMethod,
        startUrl: String,
        form: Map<String, String>? = null,
        query: Map<String, String> = emptyMap(),
    ): Response {
        var url = startUrl
        var currentMethod = method
        var currentForm = form
        var hops = 0

        while (true) {
            if (++hops > 10) error("重定向次数过多（>10），疑似循环：$startUrl")

            val host = Url(url).host
            val response = client.request(url) {
                this.method = currentMethod
                if (currentMethod == HttpMethod.Get && query.isNotEmpty() && hops == 1) {
                    query.forEach { (k, v) -> parameter(k, v) }
                }
                val cookies = cookieHeaderFor(host)
                if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookies)
                header(HttpHeaders.UserAgent, UA)
                if (currentForm != null && currentMethod == HttpMethod.Post) {
                    setBody(FormDataContent(parameters {
                        currentForm!!.forEach { (k, v) -> append(k, v) }
                    }))
                    // content-type 由 FormDataContent 自动设置
                }
            }

            storeCookies(response.headers.getAll(HttpHeaders.SetCookie).orEmpty(), host)

            val status = response.status.value
            if (status in 301..303 || status == 307 || status == 308) {
                val location = response.headers[HttpHeaders.Location]
                    ?: return Response(status, url, "", response.headers)
                url = resolveUrl(url, location)
                if (status in 301..303) {
                    currentMethod = HttpMethod.Get
                    currentForm = null
                }
                continue
            }

            return Response(status, url, response.bodyAsText(), response.headers)
        }
    }

    private fun resolveUrl(base: String, location: String): String =
        if (location.startsWith("http")) location
        else {
            val b = Url(base)
            val portPart = if (b.port != 0 && b.port != b.protocol.defaultPort) ":${b.port}" else ""
            val path = if (location.startsWith("/")) location else "/$location"
            "${b.protocol.name}://${b.host}$portPart$path"
        }

    suspend fun get(url: String, query: Map<String, String> = emptyMap()): Response =
        execute(HttpMethod.Get, url, query = query)

    suspend fun postForm(url: String, form: Map<String, String>): Response =
        execute(HttpMethod.Post, url, form = form)

    /** 拉取二进制资源（验证码图片等），带 cookie、不跟随重定向 */
    suspend fun getBytes(url: String): ByteArray {
        val host = Url(url).host
        val response = client.get(url) {
            val cookies = cookieHeaderFor(host)
            if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookies)
            header(HttpHeaders.UserAgent, UA)
        }
        storeCookies(response.headers.getAll(HttpHeaders.SetCookie).orEmpty(), host)
        return response.bodyAsBytes()
    }

    // ---------- 会话持久化（app 重启后免重新过验证码） ----------

    fun exportCookies(): Map<String, Map<String, String>> =
        jar.mapValues { it.value.toMap() }

    fun importCookies(data: Map<String, Map<String, String>>) {
        jar.clear()
        data.forEach { (domain, cookies) ->
            jar.getOrPut(domain) { mutableMapOf() }.putAll(cookies)
        }
    }

    companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    }
}

/** 各平台在各自 sourceSet 中提供 HttpClient 引擎 */
expect fun createPlatformHttpClient(): HttpClient
