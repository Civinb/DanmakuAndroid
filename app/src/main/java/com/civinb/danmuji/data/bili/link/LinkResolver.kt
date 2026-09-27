package com.civinb.danmuji.data.bili.link

import com.civinb.danmuji.data.bili.BiliHttp
import com.civinb.danmuji.util.DebugLog

/** 在 [LinkParser] 基础上处理短链：请求一次读取 Location（PiliPlus 同样做法），最多跟随 3 次。 */
class LinkResolver(private val http: BiliHttp) {

    suspend fun resolve(input: String): LinkTarget {
        var target = LinkParser.parse(input)
        repeat(3) {
            val t = target
            if (t !is LinkTarget.NeedsRedirect) return t
            val location = try {
                http.redirectLocation(t.url)
            } catch (e: Exception) {
                DebugLog.log("Link", "短链解析失败：${e.message}")
                return LinkTarget.Invalid("短链解析失败，请检查网络：${e.message}")
            }
            DebugLog.log("Link", "短链跳转到：${location ?: "（无跳转）"}")
            target = if (location == null) {
                LinkTarget.Invalid("短链没有跳转地址：${t.url}")
            } else {
                LinkParser.parse(location)
            }
        }
        return target
    }
}
