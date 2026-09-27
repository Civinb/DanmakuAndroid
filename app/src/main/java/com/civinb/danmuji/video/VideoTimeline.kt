package com.civinb.danmuji.video

import com.civinb.danmuji.model.DanmakuItem

/** 一个分 P 的全部弹幕，按出现时间排序。分段加载，线程安全。 */
class VideoTimeline {

    private val items = ArrayList<DanmakuItem>()

    val size: Int
        @Synchronized get() = items.size

    @Synchronized
    fun addSegment(list: List<DanmakuItem>) {
        if (list.isEmpty()) return
        items.addAll(list)
        items.sortBy { it.progressMs ?: 0 }
    }

    /** 时间点在 (fromExclusive, toInclusive] 之间的弹幕 */
    @Synchronized
    fun between(fromExclusive: Long, toInclusive: Long): List<DanmakuItem> {
        if (toInclusive <= fromExclusive) return emptyList()
        val out = ArrayList<DanmakuItem>()
        var i = upperBound(fromExclusive)
        while (i < items.size && (items[i].progressMs ?: 0) <= toInclusive) {
            out += items[i]
            i++
        }
        return out
    }

    /** 时间点 ≤ t 的最后 n 条（拖动进度后用来重建列表） */
    @Synchronized
    fun lastBefore(t: Long, n: Int): List<DanmakuItem> {
        val end = upperBound(t)
        return items.subList(maxOf(0, end - n), end).toList()
    }

    /** 第一个 progress > t 的下标 */
    private fun upperBound(t: Long): Int {
        var lo = 0
        var hi = items.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if ((items[mid].progressMs ?: 0) <= t) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

/**
 * 按播放位置从时间轴里取出该显示的弹幕。
 *  - 正常播放：每次返回上次位置之后到当前位置之间的弹幕
 *  - 位置突变（拖动进度、切换视频、偏移大幅调整）：返回 Reset，列表清空并显示该位置之前的最近若干条
 *  - 刚加载完覆盖当前位置的分段：也 Reset 一次，把之前错过的补上
 */
class TimelinePlayer(private val timeline: VideoTimeline) {

    sealed interface Tick {
        data class Emit(val items: List<DanmakuItem>) : Tick
        data class Reset(val items: List<DanmakuItem>) : Tick
        data object Idle : Tick
    }

    private var lastT = -1L
    private var dirty = false

    @Synchronized
    fun tick(t: Long): Tick {
        val prev = lastT
        if (prev < 0 || dirty || t < prev - BACK_TOLERANCE_MS || t > prev + JUMP_MS) {
            dirty = false
            lastT = t
            return Tick.Reset(timeline.lastBefore(t, RESET_COUNT))
        }
        if (t <= prev) return Tick.Idle // 暂停，或外推误差造成的微小回退
        lastT = t
        val items = timeline.between(prev, t)
        return if (items.isEmpty()) Tick.Idle else Tick.Emit(items)
    }

    /** 某个分段加载完成；如果它覆盖了最近显示的时间范围，下一次 tick 重建列表 */
    @Synchronized
    fun onSegmentLoaded(startMs: Long, endMs: Long) {
        val t = lastT
        if (t < 0) return
        if (startMs <= t && endMs > t - RESET_LOOKBACK_MS) dirty = true
    }

    private companion object {
        const val JUMP_MS = 3_000L
        const val BACK_TOLERANCE_MS = 800L
        const val RESET_COUNT = 20
        const val RESET_LOOKBACK_MS = 60_000L
    }
}
