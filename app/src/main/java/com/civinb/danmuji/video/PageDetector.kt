package com.civinb.danmuji.video

import com.civinb.danmuji.data.bili.video.VideoPage

/**
 * 根据 B 站 App 媒体会话里的“标题”和“时长”，判断它当前播放的是哪个分P。
 *
 * B 站会话标题到底是分P标题还是视频总标题，我没有实测数据，所以两种信号都用：
 *  1. 会话标题与某个分P标题完全相同（且只有一个分P匹配）→ 该分P
 *  2. 会话时长与某个分P时长相差 ≤ 1.5 秒（且只有一个分P匹配）→ 该分P
 * 连续两次得到同一结果才确认，避免切换瞬间的抖动。无法唯一确定时返回 null（保持不变）。
 */
class PageDetector(private val pages: List<VideoPage>) {

    private var candidate: VideoPage? = null
    private var hits = 0

    /** 返回确认后的分P；未确认或无法判断返回 null */
    fun observe(sessionTitle: String?, sessionDurationMs: Long): VideoPage? {
        val match = match(sessionTitle, sessionDurationMs)
        if (match == null) {
            candidate = null
            hits = 0
            return null
        }
        if (match == candidate) {
            hits++
        } else {
            candidate = match
            hits = 1
        }
        return if (hits >= CONFIRM_HITS) match else null
    }

    fun match(sessionTitle: String?, sessionDurationMs: Long): VideoPage? {
        if (pages.size < 2) return null
        val t = sessionTitle?.trim().orEmpty()
        if (t.isNotEmpty()) {
            val byTitle = pages.filter { it.part.trim().isNotEmpty() && it.part.trim() == t }
            if (byTitle.size == 1) return byTitle[0]
        }
        if (sessionDurationMs > 0) {
            val byDuration = pages.filter { kotlin.math.abs(it.durationSec * 1000 - sessionDurationMs) <= DURATION_TOLERANCE_MS }
            if (byDuration.size == 1) return byDuration[0]
        }
        return null
    }

    private companion object {
        const val CONFIRM_HITS = 2
        const val DURATION_TOLERANCE_MS = 1_500L
    }
}
