package com.civinb.danmuji.settings

/**
 * 悬浮窗的位置、大小、样式，以及“显示哪些消息”的开关。全部持久化保存。
 * 位置 x/y 以像素保存（屏幕坐标），宽高以 dp 保存。
 */
data class OverlaySettings(
    val x: Int = 40,
    val y: Int = 300,
    val widthDp: Int = 260,
    val heightDp: Int = 320,
    val bubbleX: Int = 40,
    val bubbleY: Int = 300,

    val bgAlpha: Float = 0.45f,
    val textSizeSp: Float = 14f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val lineSpacingDp: Float = 4f,
    val showUserName: Boolean = true,
    val maxItems: Int = 300,

    val locked: Boolean = false,
    val collapsed: Boolean = false,
    val showUnlockButton: Boolean = true,

    // 直播附加消息，默认只显示普通弹幕
    val showSuperChat: Boolean = false,
    val showGift: Boolean = false,
    val showEnter: Boolean = false,

    /** 隐藏 B 站直播间表情（整条表情弹幕不显示，文字里的 [表情] 去掉） */
    val hideEmotes: Boolean = true,

    /** 视频弹幕前显示它在视频中的时间点（便于手动对齐） */
    val showVideoTime: Boolean = true,
)
