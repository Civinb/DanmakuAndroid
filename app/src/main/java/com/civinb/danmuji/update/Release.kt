package com.civinb.danmuji.update

import org.json.JSONObject

/** 比较版本号：只看 x.y.z 数字部分，忽略开头的 v 和 “-beta”“+build” 之类后缀 */
object AppVersion {

    fun parse(raw: String): List<Int>? {
        val core = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        val parts = core.split('.')
        val out = ArrayList<Int>(parts.size)
        for (p in parts) out += p.toIntOrNull() ?: return null
        return out
    }

    /** remote 是否比 local 新；任一方无法识别时返回 null */
    fun isNewer(remote: String, local: String): Boolean? {
        val r = parse(remote) ?: return null
        val l = parse(local) ?: return null
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

data class ReleaseAsset(
    val name: String,
    val url: String,
    /** 字节数，未知为 -1 */
    val size: Long,
    /** GitHub 自 2025-06 起为附件提供的 SHA-256（小写十六进制）；没有时为 null */
    val sha256: String?,
)

data class ReleaseInfo(
    val tag: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    /** 第一个 .apk 附件；Release 里没有 APK 时为 null */
    val apk: ReleaseAsset?,
)

/**
 * 解析 GitHub REST API `GET /repos/{owner}/{repo}/releases/latest` 的响应
 * （该接口返回最新的非草稿、非预发布版本；字段见 docs.github.com/en/rest/releases）。
 */
object ReleaseParser {

    fun parse(json: JSONObject): ReleaseInfo {
        var apk: ReleaseAsset? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = str(a, "name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val digest = str(a, "digest")
                apk = ReleaseAsset(
                    name = name,
                    url = str(a, "browser_download_url"),
                    size = if (a.isNull("size")) -1 else a.optLong("size", -1),
                    sha256 = digest.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                )
                break
            }
        }
        val tag = str(json, "tag_name")
        return ReleaseInfo(
            tag = tag,
            title = str(json, "name").ifBlank { tag },
            notes = str(json, "body"),
            pageUrl = str(json, "html_url"),
            publishedAt = str(json, "published_at"),
            apk = apk,
        )
    }

    /** JSON 里的 null 在 optString 下会变成字符串 "null"，这里统一当成空串 */
    private fun str(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key)
}
