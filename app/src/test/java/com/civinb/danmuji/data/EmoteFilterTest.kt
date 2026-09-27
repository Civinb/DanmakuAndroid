package com.civinb.danmuji.data

import com.civinb.danmuji.filter.EmoteFilter
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmoteFilterTest {
    private fun item(text: String, only: Boolean = false, tokens: List<String> = emptyList()) =
        DanmakuItem(1, DanmakuKind.DANMAKU, text, emoteOnly = only, emoteTokens = tokens)

    @Test
    fun stickerDropped() = assertNull(EmoteFilter.apply(item("赞", only = true)))

    @Test
    fun inlineEmoteRemoved() =
        assertEquals("白花300块", EmoteFilter.apply(item("白花300块[热]", tokens = listOf("[热]")))!!.text)

    @Test
    fun onlyEmotesDropped() = assertNull(EmoteFilter.apply(item("[热][dog] ", tokens = listOf("[热]", "[dog]"))))

    @Test
    fun userBracketsKeptWhenNotInEmoteTable() =
        assertEquals("[同传] 你好", EmoteFilter.apply(item("[同传] 你好"))!!.text)

    @Test
    fun unicodeEmojiUntouched() = assertEquals("好耶😀", EmoteFilter.apply(item("好耶😀"))!!.text)
}
