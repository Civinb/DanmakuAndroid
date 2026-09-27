package com.civinb.danmuji.data.bili

import android.content.SharedPreferences
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** B 站接口返回 code != 0 */
class BiliApiException(val code: Int, message: String) : IOException("B站接口错误 $code：$message")

/** HTTP 状态码不是 2xx（412 通常是触发了风控） */
class BiliHttpException(val status: Int) : IOException("HTTP $status")

/**
 * 与 B 站通信的 HTTP 基础设施。所有 B 站相关的请求头、Cookie 都集中在这里。
 */
class BiliHttp(prefs: SharedPreferences) {

    val cookieJar = BiliCookieJar(prefs)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** WebSocket 用：关闭 OkHttp 自己的读超时，由我们自己的心跳看门狗判断掉线。 */
    val wsClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** 解析短链用：不自动跟随跳转，自己读 Location。 */
    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun getJson(url: HttpUrl, referer: String? = null): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .apply { if (referer != null) header("Referer", referer) }
            .get()
            .build()
        return client.newCall(request).await().use { resp ->
            if (!resp.isSuccessful) throw BiliHttpException(resp.code)
            JSONObject(resp.body?.string() ?: throw IOException("empty body"))
        }
    }

    /** GET 一个页面，只为了拿到 Set-Cookie（例如 buvid3），忽略响应体。 */
    suspend fun touch(url: HttpUrl) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        client.newCall(request).await().use { }
    }

    /** 请求一次（HEAD），返回 Location 跳转地址；没有跳转返回 null。 */
    suspend fun redirectLocation(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).head().build()
        return noRedirectClient.newCall(request).await().use { resp ->
            val loc = resp.header("Location") ?: return@use null
            resp.request.url.resolve(loc)?.toString() ?: loc
        }
    }

    companion object {
        /** 与 blivedm 当前（2026-04 更新）使用的 UA 保持一致。getDanmuInfo 的 token 与 UA 绑定，WebSocket 必须用同一个 UA。 */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    }
}

/** 可取消的 OkHttp 调用 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Throwable) {
        }
    }
}

/**
 * 简单的内存 CookieJar。buvid3/buvid4（设备标识）额外持久化，重启应用后继续用同一个，
 * 看起来像同一台设备，而不是每次都是新访客。
 * 不保存任何登录 Cookie（本应用不做登录）。
 */
class BiliCookieJar(private val prefs: SharedPreferences) : CookieJar {

    private val store = ConcurrentHashMap<String, Cookie>()

    init {
        for (name in PERSISTED) {
            prefs.getString(PREF_PREFIX + name, null)?.let { put(name, it, persist = false) }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            store["${c.name}@${c.domain}"] = c
            if (c.name in PERSISTED && c.value.isNotEmpty()) {
                prefs.edit().putString(PREF_PREFIX + c.name, c.value).apply()
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store.values.filter { it.expiresAt > now && it.matches(url) }
    }

    fun put(name: String, value: String, persist: Boolean = true) {
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain("bilibili.com")
            .path("/")
            .build()
        store["$name@bilibili.com"] = cookie
        if (persist && name in PERSISTED) prefs.edit().putString(PREF_PREFIX + name, value).apply()
    }

    fun get(name: String): String? = store.values.firstOrNull { it.name == name && it.value.isNotEmpty() }?.value

    private companion object {
        const val PREF_PREFIX = "cookie_"
        val PERSISTED = setOf("buvid3", "buvid4", "b_nut")
    }
}
