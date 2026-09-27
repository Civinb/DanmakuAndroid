package com.civinb.danmuji.data

import com.civinb.danmuji.data.bili.Wbi
import org.junit.Assert.assertEquals
import org.junit.Test

/** 测试向量来自 bilibili-API-collect 存档 docs/misc/sign/wbi.md，并用 Python 版算法复算确认。 */
class WbiTest {
    private val mixin = Wbi.mixinKey("7cd084941338484aae1ad9425b84077c", "4932caff0ff746eab6f01bf08b70ac45")

    @Test
    fun mixinKey() {
        assertEquals("ea1db124af3c7062474693fa704f4ff8", mixin)
    }

    @Test
    fun keyFromUrl() {
        assertEquals(
            "7cd084941338484aae1ad9425b84077c",
            Wbi.keyFromUrl("https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png"),
        )
    }

    @Test
    fun signDocVector() {
        val signed = Wbi.sign(mapOf("foo" to "114", "bar" to "514", "zab" to "1919810"), mixin, 1702204169)
        assertEquals("8f6f2b5b3d485fe1886cec6a0be8c5d4", signed["w_rid"])
        assertEquals("1702204169", signed["wts"])
    }

    @Test
    fun signGetDanmuInfoParams() {
        val signed = Wbi.sign(mapOf("id" to "21396545", "type" to "0", "web_location" to "444.8"), mixin, 1758800000)
        assertEquals("3e5842ea385167af4f4f614fbb5ae232", signed["w_rid"])
    }
}
