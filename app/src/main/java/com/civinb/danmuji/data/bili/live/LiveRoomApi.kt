package com.civinb.danmuji.data.bili.live

import com.civinb.danmuji.data.bili.BiliApiException
import com.civinb.danmuji.data.bili.BiliAuth
import com.civinb.danmuji.data.bili.BiliHttp
import com.civinb.danmuji.model.DanmakuIds
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

data class LiveRoomInfo(
    val roomId: Long,
    val shortId: Long,
    val ownerUid: Long,
    val title: String,
    /** 0 未开播，1 直播中，2 轮播中 */
    val liveStatus: Int,
)

data class DanmuHost(val host: String, val wssPort: Int)

data class DanmuConf(val token: String?, val hosts: List<DanmuHost>, val degraded: Boolean)

/**
 * 直播间相关 HTTP 接口（2026-09 查证，来源见 docs/01-架构与接口方案.md）。
 */
class LiveRoomApi(private val http: BiliHttp, private val auth: BiliAuth) {

    /** room/v1/Room/get_info：短号 → 真实房间号、主播 UID、标题、开播状态。 */
    suspend fun getRoomInfo(anyRoomId: Long): LiveRoomInfo {
        val url = "$LIVE_API/room/v1/Room/get_info".toHttpUrl().newBuilder()
            .addQueryParameter("room_id", anyRoomId.toString())
            .build()
        val data = http.getJson(url, referer = LIVE_REFERER).dataOrThrow()
        return LiveRoomInfo(
            roomId = data.optLong("room_id"),
            shortId = data.optLong("short_id"),
            ownerUid = data.optLong("uid"),
            title = data.optString("title"),
            liveStatus = data.optInt("live_status"),
        )
    }

    /**
     * xlive/web-room/v1/index/getDanmuInfo（需要 WBI 签名）：弹幕服务器列表 + token。
     * 失败时按 blivedm 的做法降级为默认服务器、不带 token（能否收到弹幕不保证）。
     */
    suspend fun getDanmuConf(roomId: Long): DanmuConf {
        val params = auth.sign(
            mapOf(
                "id" to roomId.toString(),
                "type" to "0",
                "web_location" to "444.8",
            ),
        )
        val url = "$LIVE_API/xlive/web-room/v1/index/getDanmuInfo".toHttpUrl().withParams(params)
        val json = http.getJson(url, referer = "https://live.bilibili.com/$roomId")
        val code = json.optInt("code", -1)
        if (code == -352) auth.resetWbiKey()
        val data = json.dataOrThrow()
        val list = data.optJSONArray("host_list")
        val hosts = buildList {
            if (list != null) {
                for (i in 0 until list.length()) {
                    val h = list.optJSONObject(i) ?: continue
                    val host = h.optString("host")
                    if (host.isNotEmpty()) add(DanmuHost(host, h.optInt("wss_port", 443)))
                }
            }
        }
        if (hosts.isEmpty()) throw BiliApiException(-1, "弹幕服务器列表为空")
        return DanmuConf(token = data.optString("token").ifEmpty { null }, hosts = hosts, degraded = false)
    }

    /** 最近的历史弹幕（最多约 10 条），连接前先显示，避免列表空白。PiliPlus 同款接口。 */
    suspend fun getRecentDanmaku(roomId: Long): List<DanmakuItem> {
        val url = "$LIVE_API/xlive/web-room/v1/dM/gethistory".toHttpUrl().newBuilder()
            .addQueryParameter("roomid", roomId.toString())
            .build()
        val data = http.getJson(url, referer = "https://live.bilibili.com/$roomId").dataOrThrow()
        val room = data.optJSONArray("room") ?: return emptyList()
        return buildList {
            for (i in 0 until room.length()) {
                val o = room.optJSONObject(i) ?: continue
                val text = if (o.isNull("text")) "" else o.optString("text")
                if (text.isBlank()) continue
                add(
                    DanmakuItem(
                        id = DanmakuIds.next(),
                        kind = DanmakuKind.DANMAKU,
                        text = text,
                        userName = if (o.isNull("nickname")) null else o.optString("nickname"),
                        userId = o.optLong("uid"),
                    ),
                )
            }
        }
    }

    private fun JSONObject.dataOrThrow(): JSONObject {
        val code = optInt("code", -1)
        if (code != 0) throw BiliApiException(code, optString("message"))
        return optJSONObject("data") ?: throw BiliApiException(code, "缺少 data")
    }

    private fun HttpUrl.withParams(params: Map<String, String>): HttpUrl {
        val b = newBuilder()
        for ((k, v) in params) b.addQueryParameter(k, v)
        return b.build()
    }

    companion object {
        const val LIVE_API = "https://api.live.bilibili.com"
        const val LIVE_REFERER = "https://live.bilibili.com/"

        /** getDanmuInfo 失败时的降级服务器（blivedm DEFAULT_DANMAKU_SERVER_LIST） */
        val FALLBACK = DanmuConf(
            token = null,
            hosts = listOf(DanmuHost("broadcastlv.chat.bilibili.com", 443)),
            degraded = true,
        )
    }
}
