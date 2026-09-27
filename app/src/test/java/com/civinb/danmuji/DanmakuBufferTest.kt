package com.civinb.danmuji

import com.civinb.danmuji.model.DanmakuIds
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.overlay.DanmakuBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuBufferTest {

    private fun item(text: String) = DanmakuItem(DanmakuIds.next(), DanmakuKind.DANMAKU, text)

    @Test
    fun drainReturnsAllAndEmpties() {
        val b = DanmakuBuffer(maxPending = 10)
        b.offer(item("a"))
        b.offer(item("b"))
        assertEquals(listOf("a", "b"), b.drain().map { it.text })
        assertTrue(b.drain().isEmpty())
    }

    @Test
    fun overflowKeepsNewest() {
        val b = DanmakuBuffer(maxPending = 3)
        (1..5).forEach { b.offer(item("$it")) }
        assertEquals(listOf("3", "4", "5"), b.drain().map { it.text })
    }
}
