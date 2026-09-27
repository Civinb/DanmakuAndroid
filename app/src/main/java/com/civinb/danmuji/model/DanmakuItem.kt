package com.civinb.danmuji.model

import java.util.concurrent.atomic.AtomicLong

/** 弹幕种类。直播和视频共用这一套模型，UI 层只认这个，不认 B 站的原始格式。 */
enum class DanmakuKind { DANMAKU, SUPER_CHAT, GIFT, ENTER, SYSTEM }

/**
 * 统一的弹幕模型。接口层（data/bili）负责把 B 站的各种消息转换成它。
 *
 * @param id          本地自增 ID（用 [DanmakuIds.next] 生成），不是 B 站的弹幕 ID
 * @param userName    直播弹幕有；未登录时 B 站会打码；视频弹幕没有用户名（为 null）
 * @param userId      直播弹幕的 UID；未登录时为 0
 * @param userHash    视频弹幕的 midHash（发送者 UID 的 CRC32），只用于“屏蔽此发送者”
 * @param color       B 站弹幕颜色 0xRRGGBB
 * @param progressMs  视频弹幕在视频中的时间点（毫秒）；直播弹幕为 null
 * @param price       醒目留言金额（元）/ 礼物总价等，按 kind 解释
 * @param repeatCount 合并重复弹幕后的次数（显示 ×N）
 * @param emoteOnly   整条弹幕就是一个直播间表情（大表情），text 只是表情名
 * @param emoteTokens 文字中出现的直播间表情占位符，如 "[热]"、"[dog]"（来自 B 站下发的 emots 表，不是 Unicode emoji）
 */
data class DanmakuItem(
    val id: Long,
    val kind: DanmakuKind,
    val text: String,
    val userName: String? = null,
    val userId: Long = 0,
    val userHash: String? = null,
    val color: Int = 0xFFFFFF,
    val timestampMs: Long = System.currentTimeMillis(),
    val progressMs: Long? = null,
    val price: Int = 0,
    val repeatCount: Int = 1,
    val emoteOnly: Boolean = false,
    val emoteTokens: List<String> = emptyList(),
)

object DanmakuIds {
    private val counter = AtomicLong(0)
    fun next(): Long = counter.incrementAndGet()
}
