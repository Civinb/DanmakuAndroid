package com.civinb.danmuji.data.bili

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * WBI 签名（纯算法，无网络）。
 * 算法依据：blivedm `blivedm/clients/web.py` 的 _WbiSigner、yt-dlp `extractor/bilibili.py` 的 _sign_wbi，
 * 以及 bilibili-API-collect 存档（docs/misc/sign/wbi.md）中的测试向量。
 */
object Wbi {

    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52,
    )

    /** 从 nav 接口返回的 `img_url` / `sub_url` 中取文件名（不含扩展名）。 */
    fun keyFromUrl(url: String): String = url.substringAfterLast('/').substringBefore('.')

    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val sb = StringBuilder()
        for (i in MIXIN_KEY_ENC_TAB) {
            if (i < raw.length) sb.append(raw[i])
        }
        return sb.toString().take(32)
    }

    /**
     * 返回加上 `wts` 和 `w_rid` 之后的参数表。
     * 参数值中的 `!'()*` 字符按官方规则在签名前去除。
     */
    fun sign(params: Map<String, String>, mixinKey: String, wts: Long): Map<String, String> {
        val withTs = LinkedHashMap(params)
        withTs["wts"] = wts.toString()
        val filtered = withTs.mapValues { (_, v) -> v.filterNot { it in "!'()*" } }
        val query = filtered.toSortedMap().entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
        val wRid = md5Hex(query + mixinKey)
        val out = LinkedHashMap(filtered)
        out["w_rid"] = wRid
        return out
    }

    /** 与浏览器 encodeURIComponent 一致（空格编码为 %20，保留 ~）。 */
    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%7E", "~")

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
