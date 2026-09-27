package com.civinb.danmuji.filter

import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind

/**
 * 过滤引擎（纯逻辑，线程安全：创建后只读）。设置变化时整体重建一个新实例。
 *
 * 规则作用范围：
 *  - 屏蔽规则：作用于普通弹幕、醒目留言、礼物、进场（系统提示除外）
 *  - 仅显示模式：只作用于普通弹幕；醒目留言/礼物/进场仍由各自开关决定
 * 关键词不区分大小写；正则按 Java 正则语法，需要不区分大小写可写 (?i)。
 */
class FilterEngine(settings: FilterSettings) {

    private class Compiled(val rule: FilterRule, val regex: Regex?)

    private val block: List<Compiled>
    private val only: List<Compiled>
    private val onlyMode: Boolean

    /** 编译失败的正则（界面上提示用） */
    val invalidRules: List<FilterRule>

    init {
        val invalid = ArrayList<FilterRule>()
        fun compile(r: FilterRule): Compiled? {
            if (r.type != RuleType.REGEX) return Compiled(r, null)
            return try {
                Compiled(r, Regex(r.pattern))
            } catch (e: Exception) {
                invalid += r
                null
            }
        }
        val enabled = settings.rules.filter { it.enabled && it.pattern.isNotEmpty() }
        block = enabled.filter { it.action == RuleAction.BLOCK }.mapNotNull(::compile)
        only = enabled.filter { it.action == RuleAction.ONLY }.mapNotNull(::compile)
        onlyMode = settings.onlyMode
        invalidRules = invalid
    }

    /** 是否被屏蔽规则命中 */
    fun isBlocked(item: DanmakuItem): Boolean {
        if (item.kind == DanmakuKind.SYSTEM) return false
        return block.any { matches(it, item) }
    }

    /** 最终是否显示 */
    fun shows(item: DanmakuItem): Boolean {
        if (item.kind == DanmakuKind.SYSTEM) return true
        if (isBlocked(item)) return false
        if (onlyMode && item.kind == DanmakuKind.DANMAKU) {
            return only.any { matches(it, item) }
        }
        return true
    }

    private fun matches(c: Compiled, item: DanmakuItem): Boolean {
        val p = c.rule.pattern
        return when (c.rule.type) {
            RuleType.KEYWORD -> item.text.contains(p, ignoreCase = true)
            RuleType.REGEX -> c.regex?.containsMatchIn(item.text) == true
            RuleType.USER ->
                (item.userId > 0 && p == item.userId.toString()) ||
                    (!item.userName.isNullOrEmpty() && p == item.userName) ||
                    (!item.userHash.isNullOrEmpty() && p == item.userHash)
        }
    }

    companion object {
        /** 校验正则，返回错误信息；合法返回 null */
        fun regexError(pattern: String): String? = try {
            Regex(pattern)
            null
        } catch (e: Exception) {
            e.message?.lineSequence()?.firstOrNull() ?: "正则格式错误"
        }
    }
}
