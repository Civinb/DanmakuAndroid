package com.civinb.danmuji.data

import com.civinb.danmuji.data.bili.video.DmSegDecoder
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.video.AutoReading
import com.civinb.danmuji.video.AutoStatus
import com.civinb.danmuji.video.SyncMode
import com.civinb.danmuji.video.TimelinePlayer
import com.civinb.danmuji.video.VideoSync
import com.civinb.danmuji.video.VideoTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class VideoTest {

    /** 由 Python 官方 protobuf 库按 DanmakuElem 字段号编码生成（含一条 mode=7 高级弹幕） */
    private val seg = Base64.getDecoder().decode("CkMIy4nsj/cjEKyMBRgBIBko////BzIIYTFiMmMzZDQ6DOWJjeaWuemrmOiDvUCA4s+qBkgFYg0xMjM0NTY3ODkwMTIzCjEIAhCwCRgFIBkogID8BzIIZGVhZGJlZWY6DOmhtumDqOW8ueW5lUCB4s+qBkgKYgEyCkUIAxCIJxgHIBko////BzIIMDAwMDAwMDA6HlswLDAsIjEtMSIsNC41LCLpq5jnuqflvLnluZUiXUCC4s+qBkgKWAJiATM=")

    @Test
    fun decodeSegment() {
        val items = DmSegDecoder.decode(seg)
        assertEquals(2, items.size) // mode=7 被丢弃
        val a = items[0]
        assertEquals("前方高能", a.text)
        assertEquals(83_500L, a.progressMs)
        assertEquals("a1b2c3d4", a.userHash)
        assertEquals(0xFFFFFF, a.color)
        assertEquals(1_700_000_000_000L, a.timestampMs)
        assertEquals("顶部弹幕", items[1].text)
        assertEquals(0xFF0000, items[1].color)
        assertTrue(DmSegDecoder.decode(ByteArray(0)).isEmpty())
    }

    private var id = 0L
    private fun at(ms: Long) = DanmakuItem(++id, DanmakuKind.DANMAKU, "t$ms", progressMs = ms)

    @Test
    fun timelineRanges() {
        val tl = VideoTimeline()
        tl.addSegment(listOf(at(3000), at(1000), at(2000)))
        assertEquals(listOf(2000L, 3000L), tl.between(1000, 3000).map { it.progressMs })
        assertEquals(listOf(1000L, 2000L), tl.lastBefore(2500, 5).map { it.progressMs })
        assertEquals(listOf(2000L), tl.lastBefore(2000, 1).map { it.progressMs })
    }

    @Test
    fun playerEmitsAndResets() {
        val tl = VideoTimeline()
        tl.addSegment((1..100).map { at(it * 1000L) })
        val p = TimelinePlayer(tl)
        assertTrue(p.tick(5_000) is TimelinePlayer.Tick.Reset) // 第一次
        assertEquals(listOf(6_000L), (p.tick(6_200) as TimelinePlayer.Tick.Emit).items.map { it.progressMs })
        assertEquals(TimelinePlayer.Tick.Idle, p.tick(6_200)) // 暂停
        assertEquals(TimelinePlayer.Tick.Idle, p.tick(6_000)) // 微小回退忽略
        val r = p.tick(50_000) as TimelinePlayer.Tick.Reset // 向前拖动
        assertEquals(50_000L, r.items.last().progressMs)
        assertTrue(p.tick(10_000) is TimelinePlayer.Tick.Reset) // 向后拖动
    }

    @Test
    fun playerResetsWhenCoveringSegmentLoads() {
        val tl = VideoTimeline()
        val p = TimelinePlayer(tl)
        p.tick(400_000)
        tl.addSegment(listOf(at(399_000)))
        p.onSegmentLoaded(360_000, 720_000)
        val r = p.tick(400_200) as TimelinePlayer.Tick.Reset
        assertEquals(listOf(399_000L), r.items.map { it.progressMs })
        // 远处的分段加载不触发重建
        p.onSegmentLoaded(1_080_000, 1_440_000)
        assertTrue(p.tick(400_400) !is TimelinePlayer.Tick.Reset)
    }

    @Test
    fun syncAutoWithOffsetAndFallback() {
        var reading: AutoReading = AutoReading.Ok(10_000, true, 1f, "某视频", 0)
        var now = 0L
        val s = VideoSync({ reading }, { now })
        s.startVideo(600_000)
        assertEquals(10_000L, s.state().positionMs)
        s.nudge(2_000)
        assertEquals(12_000L, s.state().positionMs)
        assertEquals(AutoStatus.OK, s.state().autoStatus)
        reading = AutoReading.NoSession
        val st = s.state()
        assertEquals(12_000L, st.positionMs)
        assertEquals(false, st.playing)
        assertEquals(AutoStatus.NO_SESSION, st.autoStatus)
        reading = AutoReading.NoPermission
        assertEquals(AutoStatus.NO_PERMISSION, s.state().autoStatus)
    }

    @Test
    fun syncManualClock() {
        var now = 0L
        val s = VideoSync({ AutoReading.Ok(30_000, true, 1f, null, 0) }, { now })
        s.startVideo(60_000)
        s.state() // 自动模式读到 30s
        s.setMode(SyncMode.MANUAL) // 从 30s 继续，保持播放
        now = 5_000
        assertEquals(35_000L, s.state().positionMs)
        s.togglePlay() // 暂停
        now = 9_000
        assertEquals(35_000L, s.state().positionMs)
        s.nudge(-5_000)
        assertEquals(30_000L, s.state().positionMs)
        s.seek(100_000) // 超出时长被限制
        assertEquals(60_000L, s.state().positionMs)
        s.seek(-1)
        assertEquals(0L, s.state().positionMs)
        assertEquals(null, s.state().autoStatus)
    }
}
