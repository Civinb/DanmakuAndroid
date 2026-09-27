package com.civinb.danmuji.data

import com.civinb.danmuji.data.bili.link.LinkParser
import com.civinb.danmuji.data.bili.link.LinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {
    @Test
    fun roomNumber() = assertEquals(LinkTarget.Live(21396545), LinkParser.parse(" 21396545 "))

    @Test
    fun liveUrl() = assertEquals(
        LinkTarget.Live(22603245),
        LinkParser.parse("https://live.bilibili.com/22603245?broadcast_type=0&is_room_feed=1"),
    )

    @Test
    fun liveH5Url() = assertEquals(LinkTarget.Live(123), LinkParser.parse("https://live.bilibili.com/h5/123"))

    @Test
    fun liveUrlWithoutScheme() = assertEquals(LinkTarget.Live(456), LinkParser.parse("live.bilibili.com/456"))

    @Test
    fun shareTextWithShortLink() = assertEquals(
        LinkTarget.NeedsRedirect("https://b23.tv/AbCdEfG"),
        LinkParser.parse("【某某的直播间】今天也在直播 https://b23.tv/AbCdEfG"),
    )

    @Test
    fun videoBv() = assertEquals(
        LinkTarget.Video("BV1GJ411x7h7", null, 2),
        LinkParser.parse("https://www.bilibili.com/video/BV1GJ411x7h7/?p=2"),
    )

    @Test
    fun videoAv() = assertEquals(LinkTarget.Video(null, 170001, null), LinkParser.parse("av170001"))

    @Test
    fun invalid() = assertTrue(LinkParser.parse("hello") is LinkTarget.Invalid)
}
