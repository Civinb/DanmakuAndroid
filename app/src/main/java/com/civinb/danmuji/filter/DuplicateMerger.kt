package com.civinb.danmuji.filter

import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind

/**
 * 合并短时间内的重复弹幕：窗口期内内容相同（忽略首尾空白、大小写、连续空白）的普通弹幕，
 * 不再新增一行，而是把第一条的次数 +1（显示 ×N）。
 * 非线程安全：只在弹幕来源的收集协程里使用。
 */
class DuplicateMerger {

    sealed interface Result {
        data class New(val item: DanmakuItem) : Result
        data class Repeat(val targetId: Long, val count: Int) : Result
    }

    private class Entry(val id: Long, var count: Int, val firstSeenMs: Long)

    private val recent = LinkedHashMap<String, Entry>()

    fun offer(item: DanmakuItem, nowMs: Long, windowMs: Long): Result {
        if (item.kind != DanmakuKind.DANMAKU) return Result.New(item)
        evict(nowMs, windowMs)
        val key = normalize(item.text)
        if (key.isEmpty()) return Result.New(item)
        val e = recent[key]
        if (e != null) {
            e.count++
            return Result.Repeat(e.id, e.count)
        }
        recent[key] = Entry(item.id, 1, nowMs)
        if (recent.size > MAX_ENTRIES) {
            val it = recent.entries.iterator()
            it.next()
            it.remove()
        }
        return Result.New(item)
    }

    fun clear() = recent.clear()

    /** 窗口从第一次出现开始计算，超过窗口就作为新的一行重新开始计数 */
    private fun evict(nowMs: Long, windowMs: Long) {
        val it = recent.entries.iterator()
        while (it.hasNext()) {
            if (nowMs - it.next().value.firstSeenMs > windowMs) it.remove() else break
        }
    }

    private fun normalize(s: String): String = s.trim().lowercase().replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MAX_ENTRIES = 2000
    }
}
