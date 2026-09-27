package com.civinb.danmuji.overlay

import com.civinb.danmuji.model.DanmakuItem

/**
 * 线程安全的待显示缓冲区。
 * 弹幕来源（后台线程）不断 [offer]，UI 线程每隔约 150ms [drain] 一次，批量刷新列表，
 * 避免每来一条弹幕就刷新一次界面。
 * 如果 UI 跟不上（极端刷屏），只保留最新的 [maxPending] 条，丢弃更旧的。
 *
 * [updateRepeat]：重复弹幕合并时更新某条的 ×N。若那条还在缓冲区里没显示，直接改缓冲区里的；
 * 否则记下来，交给界面去更新已显示的那一行。
 */
class DanmakuBuffer(private val maxPending: Int = 500) {

    class Batch(val items: List<DanmakuItem>, val repeats: Map<Long, Int>) {
        fun isEmpty() = items.isEmpty() && repeats.isEmpty()
    }

    private val lock = Any()
    private var pending = ArrayList<DanmakuItem>()
    private var repeats = HashMap<Long, Int>()

    fun offer(item: DanmakuItem) {
        synchronized(lock) {
            pending.add(item)
            val overflow = pending.size - maxPending
            if (overflow > 0) pending.subList(0, overflow).clear()
        }
    }

    fun updateRepeat(id: Long, count: Int) {
        synchronized(lock) {
            for (i in pending.indices.reversed()) {
                if (pending[i].id == id) {
                    pending[i] = pending[i].copy(repeatCount = count)
                    return
                }
            }
            repeats[id] = count
        }
    }

    fun drainBatch(): Batch = synchronized(lock) {
        val out = Batch(if (pending.isEmpty()) emptyList() else pending, if (repeats.isEmpty()) emptyMap() else repeats)
        if (pending.isNotEmpty()) pending = ArrayList()
        if (repeats.isNotEmpty()) repeats = HashMap()
        out
    }

    /** 只取新增弹幕（兼容旧调用与测试） */
    fun drain(): List<DanmakuItem> = drainBatch().items

    fun clear() {
        synchronized(lock) {
            pending.clear()
            repeats.clear()
        }
    }
}
