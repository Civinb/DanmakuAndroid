package com.civinb.danmuji.data.bili

import com.civinb.danmuji.util.DebugLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 访客身份：buvid3（设备标识）与 WBI 签名密钥。
 *
 * buvid3：优先用 /x/frontend/finger/spi（BiliNest 做法），失败再访问首页拿 Set-Cookie（blivedm 做法）。
 * WBI：从 /x/web-interface/nav 的 wbi_img 取 img_key/sub_key，未登录时该接口 code=-101 但仍返回 wbi_img。
 */
class BiliAuth(private val http: BiliHttp) {

    private val mutex = Mutex()
    @Volatile
    private var mixinKey: String? = null
    private var mixinKeyTime = 0L

    suspend fun ensureBuvid(): String = mutex.withLock {
        http.cookieJar.get("buvid3")?.let { return it }
        try {
            val json = http.getJson(SPI_URL.toHttpUrl(), referer = "https://www.bilibili.com/")
            val data = json.optJSONObject("data")
            val b3 = data?.optString("b_3").orEmpty()
            val b4 = data?.optString("b_4").orEmpty()
            if (b3.isNotEmpty()) {
                http.cookieJar.put("buvid3", b3)
                if (b4.isNotEmpty()) http.cookieJar.put("buvid4", b4)
                http.cookieJar.put("b_nut", (System.currentTimeMillis() / 1000).toString())
                DebugLog.log(TAG, "buvid3 已获取（spi）")
                return b3
            }
        } catch (e: Exception) {
            DebugLog.log(TAG, "spi 获取 buvid 失败：${e.message}")
        }
        try {
            http.touch(HOME_URL.toHttpUrl())
        } catch (e: Exception) {
            DebugLog.log(TAG, "首页获取 buvid 失败：${e.message}")
        }
        val fromHome = http.cookieJar.get("buvid3").orEmpty()
        DebugLog.log(TAG, if (fromHome.isNotEmpty()) "buvid3 已获取（首页）" else "未能获取 buvid3，继续尝试连接")
        fromHome
    }

    /** 返回可用的 WBI mixin key，过期（约 12 小时）或被重置后自动刷新。 */
    suspend fun wbiKey(): String = mutex.withLock {
        val cached = mixinKey
        if (cached != null && System.currentTimeMillis() - mixinKeyTime < WBI_TTL_MS) return cached
        val json = http.getJson(NAV_URL.toHttpUrl(), referer = "https://www.bilibili.com/")
        val img = json.optJSONObject("data")?.optJSONObject("wbi_img")
            ?: throw BiliApiException(json.optInt("code"), "nav 接口没有返回 wbi_img")
        val key = Wbi.mixinKey(Wbi.keyFromUrl(img.optString("img_url")), Wbi.keyFromUrl(img.optString("sub_url")))
        if (key.length < 32) throw BiliApiException(-1, "WBI 密钥格式异常")
        mixinKey = key
        mixinKeyTime = System.currentTimeMillis()
        DebugLog.log(TAG, "WBI 密钥已刷新")
        key
    }

    /** 接口返回 -352（签名/风控）时调用，下次强制重新获取密钥。 */
    fun resetWbiKey() {
        mixinKey = null
    }

    suspend fun sign(params: Map<String, String>): Map<String, String> =
        Wbi.sign(params, wbiKey(), System.currentTimeMillis() / 1000)

    private companion object {
        const val TAG = "Auth"
        const val SPI_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        const val HOME_URL = "https://www.bilibili.com/"
        const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        const val WBI_TTL_MS = (11 * 60 + 59) * 60 * 1000L // 与 blivedm 一致：11 小时 59 分
    }
}
