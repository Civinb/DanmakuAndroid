package com.civinb.danmuji.data

import com.civinb.danmuji.data.bili.video.VideoPage
import com.civinb.danmuji.video.PageDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageDetectorTest {
    private val pages = listOf(
        VideoPage(11, 1, "第一集", 600),
        VideoPage(22, 2, "第二集", 720),
        VideoPage(33, 3, "第三集", 720),
    )

    @Test
    fun matchesByPartTitle() {
        val d = PageDetector(pages)
        assertNull(d.observe("第二集", 0)) // 第一次不确认
        assertEquals(22L, d.observe("第二集", 0)?.cid)
    }

    @Test
    fun matchesByDurationWhenTitleIsVideoTitle() {
        val d = PageDetector(pages)
        d.observe("整个视频的总标题", 600_400)
        assertEquals(11L, d.observe("整个视频的总标题", 600_400)?.cid)
    }

    @Test
    fun ambiguousDurationGivesNull() {
        val d = PageDetector(pages)
        d.observe("总标题", 720_000)
        assertNull(d.observe("总标题", 720_000)) // P2、P3 时长相同，无法判断
    }

    @Test
    fun flappingResetsConfirmation() {
        val d = PageDetector(pages)
        d.observe("第二集", 0)
        assertNull(d.observe("第三集", 0))
        assertEquals(33L, d.observe("第三集", 0)?.cid)
    }

    @Test
    fun singlePageNeverSwitches() {
        val d = PageDetector(listOf(VideoPage(1, 1, "x", 10)))
        d.observe("x", 10_000)
        assertNull(d.observe("x", 10_000))
    }
}
