package com.civinb.danmuji.data.bili.auth

import com.civinb.danmuji.data.bili.BiliApiException
import com.civinb.danmuji.data.bili.BiliHttp
import com.civinb.danmuji.util.DebugLog
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * B 站网页扫码登录（2026-09 查证：JLiverTool api.rs、BiliNest server.mjs 用法一致）：
 *  - 生成：GET passport.bilibili.com/x/passport-login/web/qrcode/generate → data.url（做成二维码）、data.qrcode_key
 *  - 轮询：GET .../qrcode/poll?qrcode_key=… → data.code：0 成功 / 86101 未扫码 / 86090 已扫码待确认 / 86038 已过期
 *  - 成功时会话 Cookie 通过 Set-Cookie 下发；若没带，访问 data.url 补取（两个项目都有此兜底）
 *  - 退出：POST passport.bilibili.com/login/exit/v2，表单 biliCSRF=bili_jct（JLiverTool）
 * 不在应用内输入账号密码。
 */
class LoginApi(private val http: BiliHttp, private val store: CredentialStore) {

    data class QrCode(val url: String, val key: String)

    enum class QrState { WAIT_SCAN, WAIT_CONFIRM, EXPIRED, SUCCESS }

    data class Account(val isLogin: Boolean, val mid: Long, val uname: String)

    /** 应用启动时把保存的凭证放回 Cookie */
    fun restore() {
        val saved = store.load() ?: return
        saved.forEach { (k, v) -> http.cookieJar.put(k, v, persist = false) }
        DebugLog.log(TAG, "已恢复登录凭证")
    }

    fun isLoggedIn(): Boolean = http.cookieJar.get("SESSDATA") != null

    /** 已登录用户的 UID（DedeUserID Cookie），未登录为 0 */
    fun mid(): Long = if (isLoggedIn()) http.cookieJar.get("DedeUserID")?.toLongOrNull() ?: 0L else 0L

    suspend fun generate(): QrCode {
        val json = http.getJson(GENERATE_URL.toHttpUrl(), REFERER)
        val code = json.optInt("code", -1)
        if (code != 0) throw BiliApiException(code, json.optString("message"))
        val data = json.optJSONObject("data") ?: throw BiliApiException(-1, "缺少 data")
        return QrCode(url = data.optString("url"), key = data.optString("qrcode_key"))
    }

    suspend fun poll(key: String): QrState {
        val url = POLL_URL.toHttpUrl().newBuilder().addQueryParameter("qrcode_key", key).build()
        val json = http.getJson(url, REFERER)
        val rootCode = json.optInt("code", -1)
        if (rootCode != 0) throw BiliApiException(rootCode, json.optString("message"))
        val data = json.optJSONObject("data") ?: throw BiliApiException(-1, "缺少 data")
        return when (val c = data.optInt("code", -1)) {
            0 -> {
                if (http.cookieJar.get("SESSDATA") == null) {
                    val follow = data.optString("url")
                    if (follow.startsWith("https://")) {
                        DebugLog.log(TAG, "轮询响应未带 SESSDATA，访问跳转地址补取")
                        http.touch(follow.toHttpUrl())
                    }
                }
                val cookies = LOGIN_COOKIES.mapNotNull { name -> http.cookieJar.get(name)?.let { name to it } }.toMap()
                if (!cookies.containsKey("SESSDATA")) throw BiliApiException(-1, "登录成功，但没有拿到会话 Cookie")
                store.save(cookies)
                DebugLog.log(TAG, "扫码登录成功，已加密保存 ${cookies.size} 个 Cookie")
                QrState.SUCCESS
            }
            86101 -> QrState.WAIT_SCAN
            86090 -> QrState.WAIT_CONFIRM
            86038 -> QrState.EXPIRED
            else -> throw BiliApiException(c, data.optString("message"))
        }
    }

    /** 通过 nav 接口查询登录状态（未登录时该接口 code=-101） */
    suspend fun account(): Account {
        val json = http.getJson(NAV_URL.toHttpUrl(), "https://www.bilibili.com/")
        val data = json.optJSONObject("data")
        val isLogin = json.optInt("code", -1) == 0 && data?.optBoolean("isLogin") == true
        return Account(
            isLogin = isLogin,
            mid = if (isLogin) data?.optLong("mid") ?: 0L else 0L,
            uname = if (isLogin) data?.optString("uname").orEmpty() else "",
        )
    }

    /** 退出登录：通知 B 站注销会话，并删除本地凭证与 Cookie（即使网络请求失败也会删除本地数据） */
    suspend fun logout() {
        val csrf = http.cookieJar.get("bili_jct")
        if (csrf != null) {
            try {
                val json = http.postForm(LOGOUT_URL.toHttpUrl(), mapOf("biliCSRF" to csrf), "https://www.bilibili.com/")
                DebugLog.log(TAG, "退出登录返回 code=${json.optInt("code", -1)}")
            } catch (e: Exception) {
                DebugLog.log(TAG, "退出登录请求失败（本地凭证仍会删除）：${e.message}")
            }
        }
        LOGIN_COOKIES.forEach { http.cookieJar.remove(it) }
        store.clear()
    }

    /** 只删除本地凭证（例如 B 站那边已失效） */
    fun clearLocal() {
        LOGIN_COOKIES.forEach { http.cookieJar.remove(it) }
        store.clear()
    }

    private companion object {
        const val TAG = "Login"
        const val REFERER = "https://www.bilibili.com/"
        const val GENERATE_URL = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
        const val POLL_URL = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
        const val LOGOUT_URL = "https://passport.bilibili.com/login/exit/v2"
        const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        val LOGIN_COOKIES = listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")
    }
}
