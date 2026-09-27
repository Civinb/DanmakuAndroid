package com.civinb.danmuji.overlay

import com.civinb.danmuji.model.DanmakuItem

/**
 * 线程安全的待显示缓冲区。
 * 弹幕源（后台线程）不断 [offer]，UI 线程每隔约 150ms [drain] 一次，批量刷新列表，
 * 避免每来一条弹幕就刷新一次界面。
 * 如果 UI 跟不上（极端刷屏），只保留最新的 [maxPending] 条，丢弃更旧的。
 */
class DanmakuBuffer(private val maxPending: Int = 500) {

    private val lock = Any()
    private var pending = ArrayList<DanmakuItem>()

    fun offer(item: DanmakuItem) {
        synchronized(lock) {
            pending.add(item)
            val overflow = pending.size - maxPending
            if (overflow > 0) pending.subList(0, overflow).clear()
        }
    }

    fun drain(): List<DanmakuItem> = synchronized(lock) {
        if (pending.isEmpty()) {
            emptyList()
        } else {
            val out = pending
            pending = ArrayList()
            out
        }
    }

    fun clear() {
        synchronized(lock) { pending.clear() }
    }
}
