package com.civinb.danmuji.data

import com.civinb.danmuji.filter.DuplicateMerger
import com.civinb.danmuji.filter.FilterEngine
import com.civinb.danmuji.filter.FilterJson
import com.civinb.danmuji.filter.FilterRule
import com.civinb.danmuji.filter.FilterSettings
import com.civinb.danmuji.filter.RuleAction
import com.civinb.danmuji.filter.RuleType
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.overlay.DanmakuBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTest {
    private var nextId = 1L
    private fun dm(text: String, name: String? = null, uid: Long = 0, kind: DanmakuKind = DanmakuKind.DANMAKU, hash: String? = null) =
        DanmakuItem(nextId++, kind, text, userName = name, userId = uid, userHash = hash)

    private fun rule(type: RuleType, pattern: String, action: RuleAction = RuleAction.BLOCK, enabled: Boolean = true) =
        FilterRule(nextId++, type, action, pattern, enabled)

    @Test
    fun keywordIgnoresCase() {
        val e = FilterEngine(FilterSettings(listOf(rule(RuleType.KEYWORD, "abc"))))
        assertFalse(e.shows(dm("xxABCxx")))
        assertTrue(e.shows(dm("xyz")))
    }

    @Test
    fun regexRule() {
        val e = FilterEngine(FilterSettings(listOf(rule(RuleType.REGEX, "^\\d+$"))))
        assertFalse(e.shows(dm("66666")))
        assertTrue(e.shows(dm("666 好")))
    }

    @Test
    fun invalidRegexIgnoredAndReported() {
        val bad = rule(RuleType.REGEX, "([")
        val e = FilterEngine(FilterSettings(listOf(bad)))
        assertTrue(e.shows(dm("([")))
        assertEquals(listOf(bad), e.invalidRules)
        assertTrue(FilterEngine.regexError("([") != null)
        assertEquals(null, FilterEngine.regexError("a+"))
    }

    @Test
    fun userRuleByUidNameAndHash() {
        val e = FilterEngine(
            FilterSettings(listOf(rule(RuleType.USER, "123"), rule(RuleType.USER, "张**"), rule(RuleType.USER, "abcd1234"))),
        )
        assertFalse(e.shows(dm("hi", name = "someone", uid = 123)))
        assertFalse(e.shows(dm("hi", name = "张**")))
        assertFalse(e.shows(dm("hi", hash = "abcd1234")))
        assertTrue(e.shows(dm("hi", name = "李**")))
        // UID 为 0（未登录）时不会被 "0" 之类的规则误伤
        assertTrue(FilterEngine(FilterSettings(listOf(rule(RuleType.USER, "0")))).shows(dm("hi", uid = 0)))
    }

    @Test
    fun disabledRuleIgnored() {
        val e = FilterEngine(FilterSettings(listOf(rule(RuleType.KEYWORD, "abc", enabled = false))))
        assertTrue(e.shows(dm("abc")))
    }

    @Test
    fun onlyModeShowsMatchingDanmakuOnly() {
        val e = FilterEngine(
            FilterSettings(listOf(rule(RuleType.KEYWORD, "【同传】", RuleAction.ONLY)), onlyMode = true),
        )
        assertTrue(e.shows(dm("【同传】大家好")))
        assertFalse(e.shows(dm("哈哈哈")))
        // 仅显示模式不影响醒目留言和系统提示
        assertTrue(e.shows(dm("SC 内容", kind = DanmakuKind.SUPER_CHAT)))
        assertTrue(e.shows(dm("已连接", kind = DanmakuKind.SYSTEM)))
    }

    @Test
    fun blockBeatsOnly() {
        val e = FilterEngine(
            FilterSettings(
                listOf(rule(RuleType.KEYWORD, "同传", RuleAction.ONLY), rule(RuleType.KEYWORD, "广告")),
                onlyMode = true,
            ),
        )
        assertFalse(e.shows(dm("同传 广告")))
    }

    @Test
    fun blockAppliesToSuperChatButNotSystem() {
        val e = FilterEngine(FilterSettings(listOf(rule(RuleType.KEYWORD, "abc"))))
        assertFalse(e.shows(dm("abc", kind = DanmakuKind.SUPER_CHAT)))
        assertTrue(e.shows(dm("abc", kind = DanmakuKind.SYSTEM)))
    }

    @Test
    fun jsonRoundTrip() {
        val rules = listOf(rule(RuleType.KEYWORD, "广告"), rule(RuleType.REGEX, "^\\d+$", RuleAction.ONLY, enabled = false))
        assertEquals(rules, FilterJson.rulesFromJson(FilterJson.rulesToJson(rules)))
        assertEquals(emptyList<FilterRule>(), FilterJson.rulesFromJson("not json"))
    }

    @Test
    fun mergerCountsWithinWindow() {
        val m = DuplicateMerger()
        val a = dm("草")
        assertEquals(DuplicateMerger.Result.New(a), m.offer(a, 0, 10_000))
        assertEquals(DuplicateMerger.Result.Repeat(a.id, 2), m.offer(dm(" 草 "), 1_000, 10_000))
        assertEquals(DuplicateMerger.Result.Repeat(a.id, 3), m.offer(dm("草"), 9_000, 10_000))
        // 超过窗口：重新作为新的一行
        val b = dm("草")
        assertEquals(DuplicateMerger.Result.New(b), m.offer(b, 11_000, 10_000))
    }

    @Test
    fun mergerIgnoresNonDanmaku() {
        val m = DuplicateMerger()
        m.offer(dm("x", kind = DanmakuKind.SYSTEM), 0, 10_000)
        val s = dm("x", kind = DanmakuKind.SYSTEM)
        assertEquals(DuplicateMerger.Result.New(s), m.offer(s, 1, 10_000))
    }

    @Test
    fun bufferRepeatUpdatesPendingOrRecords() {
        val b = DanmakuBuffer()
        val a = dm("a")
        b.offer(a)
        b.updateRepeat(a.id, 2)
        b.updateRepeat(999, 5)
        val batch = b.drainBatch()
        assertEquals(2, batch.items.single().repeatCount)
        assertEquals(mapOf(999L to 5), batch.repeats)
        assertTrue(b.drainBatch().isEmpty())
    }
}
