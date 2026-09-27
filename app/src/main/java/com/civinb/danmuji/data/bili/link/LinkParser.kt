package com.civinb.danmuji.data.bili.link

/** 用户输入（房间号 / 链接 / B 站 App 分享出来的整段文字）解析出的目标。 */
sealed interface LinkTarget {
    data class Live(val roomId: Long) : LinkTarget

    /** 视频：bvid 或 aid 二选一；page 为 URL 中的 ?p= 分 P（从 1 开始） */
    data class Video(val bvid: String?, val aid: Long?, val page: Int?) : LinkTarget

    /** 需要先跟随跳转才能知道目标的短链（如 b23.tv） */
    data class NeedsRedirect(val url: String) : LinkTarget

    data class Invalid(val reason: String) : LinkTarget
}

/**
 * 纯文本解析，不联网。
 * 直播链接规则参考 PiliPlus `lib/utils/app_scheme.dart`：live.bilibili.com 路径中的第一段数字即房间号；
 * b23.tv 短链需要请求一次读取 Location 跳转地址（见 [LinkResolver]）。
 */
object LinkParser {

    private val URL_REGEX = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""")
    private val LIVE_REGEX = Regex("""live\.bilibili\.com/(?:[A-Za-z0-9_]+/)*?(\d+)""")
    private val BV_REGEX = Regex("""(BV[0-9A-Za-z]{10})""")
    private val AV_REGEX = Regex("""(?:^|[/?&=\s])av(\d+)""", RegexOption.IGNORE_CASE)
    private val PAGE_REGEX = Regex("""[?&]p=(\d+)""")
    private val SHORT_HOSTS = listOf("b23.tv")

    fun parse(input: String): LinkTarget {
        val text = input.trim()
        if (text.isEmpty()) return LinkTarget.Invalid("请输入直播间号或链接")

        // 纯数字：直播间号
        if (text.all { it.isDigit() }) {
            val id = text.toLongOrNull()
            return if (id != null && id > 0) LinkTarget.Live(id) else LinkTarget.Invalid("直播间号不正确")
        }

        // 不带 http 前缀的直播链接也识别
        LIVE_REGEX.find(text)?.let { m ->
            m.groupValues[1].toLongOrNull()?.let { return LinkTarget.Live(it) }
        }

        BV_REGEX.find(text)?.let { m ->
            return LinkTarget.Video(bvid = m.groupValues[1], aid = null, page = pageOf(text))
        }
        AV_REGEX.find(text)?.let { m ->
            m.groupValues[1].toLongOrNull()?.let { return LinkTarget.Video(bvid = null, aid = it, page = pageOf(text)) }
        }

        val urls = URL_REGEX.findAll(text).map { it.value }.toList()
        urls.firstOrNull { url -> SHORT_HOSTS.any { host -> hostOf(url)?.endsWith(host) == true } }
            ?.let { return LinkTarget.NeedsRedirect(it) }

        return if (urls.isNotEmpty()) {
            LinkTarget.Invalid("无法识别这个链接：${urls.first()}")
        } else {
            LinkTarget.Invalid("没有找到直播间号或链接")
        }
    }

    private fun pageOf(text: String): Int? = PAGE_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun hostOf(url: String): String? =
        url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
            .ifEmpty { null }
}
