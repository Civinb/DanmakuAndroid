package com.civinb.danmuji.data.bili.video

import com.civinb.danmuji.data.bili.BiliApiException
import com.civinb.danmuji.data.bili.BiliAuth
import com.civinb.danmuji.data.bili.BiliHttp
import com.civinb.danmuji.data.bili.BiliHttpException
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.util.DebugLog
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import com.civinb.danmuji.util.tr

/**
 * 视频相关接口（2026-09 查证）：
 *  - 视频信息：见 [getVideoInfo]
 *  - 弹幕分段：/x/v2/dm/wbi/web/seg.so（WBI 签名），失败回退 /x/v2/dm/web/seg.so（BiliNest 1.4.9 同样做法）
 *    参数 type=1、oid=cid、segment_index 从 1 开始，每段 6 分钟；HTTP 304 表示该段没有弹幕
 */
class VideoApi(private val http: BiliHttp, private val auth: BiliAuth) {

    /**
     * 获取视频信息。按顺序尝试，全部记录到连接日志，便于判断哪个接口被风控：
     *  1. /x/web-interface/wbi/view（WBI 签名；PiliPlus 当前用法）
     *  2. /x/web-interface/view（不签名；BiliNest 用法）
     *  3. /x/player/pagelist（只有分P列表、没有标题；PiliPlus、yt-dlp 都在用）
     */
    suspend fun getVideoInfo(bvid: String?, aid: Long?): VideoInfo {
        val params = when {
            !bvid.isNullOrEmpty() -> mapOf("bvid" to bvid)
            aid != null && aid > 0 -> mapOf("aid" to aid.toString())
            else -> throw IllegalArgumentException(tr("缺少 BV 号或 av 号", "Missing BV or av ID"))
        }
        auth.ensureBuvid()

        // 1 + 2：视频详情
        for (name in listOf("wbi/view", "view")) {
            try {
                val url = if (name == "wbi/view") {
                    WBI_VIEW_URL.toHttpUrl().withParams(auth.sign(params))
                } else {
                    VIEW_URL.toHttpUrl().withParams(params)
                }
                val json = http.getJson(url, REFERER)
                val code = json.optInt("code", -1)
                if (code == -352 || code == -412) {
                    DebugLog.log(TAG, tr("$name 返回 code=$code（风控），尝试下一个接口", "$name returned code=$code (risk control), trying the next API"))
                    continue
                }
                val info = parseView(json.dataOrThrow())
                DebugLog.log(TAG, tr("$name 成功", "$name succeeded"))
                return info
            } catch (e: BiliHttpException) {
                DebugLog.log(TAG, tr("$name 返回 HTTP ${e.status}，尝试下一个接口", "$name returned HTTP ${e.status}, trying the next API"))
            } catch (e: BiliApiException) {
                // 视频不存在（-404）、不可见（62002）等明确错误，换接口也没用
                if (e.code != -352 && e.code != -412) throw e
            }
        }

        // 3：只拿分P列表
        try {
            val json = http.getJson(PAGELIST_URL.toHttpUrl().withParams(params), REFERER)
            val code = json.optInt("code", -1)
            if (code != 0) throw BiliApiException(code, json.optString("message"))
            val pages = parsePages(json.optJSONArray("data"))
            if (pages.isEmpty()) throw BiliApiException(-1, tr("视频没有分P信息", "Video has no part info"))
            DebugLog.log(TAG, tr("pagelist 成功（无标题）", "pagelist succeeded (no titles)"))
            return VideoInfo(bvid = bvid.orEmpty(), aid = aid ?: 0, title = "", pages = pages)
        } catch (e: BiliHttpException) {
            DebugLog.log(TAG, tr("pagelist 返回 HTTP ${e.status}", "pagelist returned HTTP ${e.status}"))
            throw e
        } catch (e: BiliApiException) {
            DebugLog.log(TAG, tr("pagelist 失败：${e.message}", "pagelist failed: ${e.message}"))
            throw e
        }
    }

    private fun parseView(data: JSONObject): VideoInfo {
        val pages = parsePages(data.optJSONArray("pages"))
        if (pages.isEmpty()) throw BiliApiException(-1, tr("视频没有分P信息", "Video has no part info"))
        return VideoInfo(
            bvid = data.optString("bvid"),
            aid = data.optLong("aid"),
            title = data.optString("title"),
            pages = pages,
        )
    }

    private fun parsePages(arr: JSONArray?): List<VideoPage> = buildList {
        if (arr == null) return@buildList
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            add(
                VideoPage(
                    cid = p.optLong("cid"),
                    page = p.optInt("page", i + 1),
                    part = if (p.isNull("part")) "" else p.optString("part"),
                    durationSec = p.optLong("duration"),
                ),
            )
        }
    }

    /** 下载一段（6 分钟）弹幕。segmentIndex 从 1 开始。 */
    suspend fun getSegment(cid: Long, segmentIndex: Int): List<DanmakuItem> {
        val base = mapOf(
            "type" to "1",
            "oid" to cid.toString(),
            "segment_index" to segmentIndex.toString(),
        )
        try {
            val (status, bytes) = http.getBytes(SEG_WBI_URL.toHttpUrl().withParams(auth.sign(base)), REFERER)
            if (status == 304) return emptyList()
            if (status in 200..299) return DmSegDecoder.decode(bytes)
            DebugLog.log(TAG, tr("seg.so(WBI) 第 $segmentIndex 段 HTTP $status，改用旧接口", "seg.so(WBI) segment $segmentIndex HTTP $status, falling back to the old API"))
        } catch (e: IOException) {
            DebugLog.log(TAG, tr("seg.so(WBI) 第 $segmentIndex 段失败：${e.message}，改用旧接口", "seg.so(WBI) segment $segmentIndex failed: ${e.message}, falling back to the old API"))
        }
        val (status, bytes) = http.getBytes(SEG_URL.toHttpUrl().withParams(base), REFERER)
        if (status == 304) return emptyList()
        if (status !in 200..299) throw BiliHttpException(status)
        return DmSegDecoder.decode(bytes)
    }

    private fun JSONObject.dataOrThrow(): JSONObject {
        val code = optInt("code", -1)
        if (code != 0) throw BiliApiException(code, optString("message"))
        return optJSONObject("data") ?: throw BiliApiException(code, tr("缺少 data", "Missing data"))
    }

    private fun HttpUrl.withParams(params: Map<String, String>): HttpUrl {
        val b = newBuilder()
        for ((k, v) in params) b.addQueryParameter(k, v)
        return b.build()
    }

    companion object {
        private const val TAG = "Video"
        private const val REFERER = "https://www.bilibili.com/"
        private const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view"
        private const val WBI_VIEW_URL = "https://api.bilibili.com/x/web-interface/wbi/view"
        private const val PAGELIST_URL = "https://api.bilibili.com/x/player/pagelist"
        private const val SEG_WBI_URL = "https://api.bilibili.com/x/v2/dm/wbi/web/seg.so"
        private const val SEG_URL = "https://api.bilibili.com/x/v2/dm/web/seg.so"

        /** 每段 6 分钟 */
        const val SEGMENT_MS = 6 * 60 * 1000L
    }
}
