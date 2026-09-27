package com.civinb.danmuji.source

import com.civinb.danmuji.model.DanmakuIds
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * 模拟弹幕源：不连 B 站，按固定速率产生弹幕，用来测试悬浮窗交互和大流量下的流畅度。
 */
class FakeDanmakuSource(private val ratePerSecond: Int) : DanmakuSource {

    override fun events(): Flow<SourceEvent> = flow {
        emit(SourceEvent.Status("模拟弹幕源 · $ratePerSecond 条/秒"))
        val random = Random(System.currentTimeMillis())
        val intervalMs = (1000L / ratePerSecond.coerceAtLeast(1)).coerceAtLeast(1L)
        while (true) {
            delay(intervalMs)
            emit(SourceEvent.Item(makeItem(random)))
        }
    }

    private fun makeItem(random: Random): DanmakuItem {
        val name = NAMES[random.nextInt(NAMES.size)]
        val roll = random.nextInt(100)
        return when {
            roll < 3 -> DanmakuItem(
                id = DanmakuIds.next(),
                kind = DanmakuKind.SUPER_CHAT,
                text = "这是一条醒目留言测试，主播辛苦了！",
                userName = name,
                price = listOf(30, 50, 100, 500)[random.nextInt(4)],
            )
            roll < 8 -> DanmakuItem(
                id = DanmakuIds.next(),
                kind = DanmakuKind.GIFT,
                text = "赠送 小心心 ×${random.nextInt(1, 10)}",
                userName = name,
            )
            roll < 15 -> DanmakuItem(
                id = DanmakuIds.next(),
                kind = DanmakuKind.ENTER,
                text = "进入直播间",
                userName = name,
            )
            else -> DanmakuItem(
                id = DanmakuIds.next(),
                kind = DanmakuKind.DANMAKU,
                text = TEXTS[random.nextInt(TEXTS.size)],
                userName = name,
            )
        }
    }

    private companion object {
        val NAMES = listOf("路过的观众", "晚安玛卡巴卡", "一只橘猫", "同传man", "今天也要加油", "abc123", "长名字测试长名字测试长名字测试")
        val TEXTS = listOf(
            "哈哈哈哈哈",
            "来了来了",
            "【同传】我们今天来玩一个新游戏",
            "这波操作可以",
            "？？？",
            "这是一条很长很长的弹幕，用来测试自动换行是否正常，看看在窄窗口里会不会被截断或者重叠。",
            "666",
            "晚上好",
            "【同传】谢谢大家的礼物",
            "草",
        )
    }
}
