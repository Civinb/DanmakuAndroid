package com.civinb.danmuji.settings

/**
 * 悬浮窗的位置、大小、样式，以及“显示哪些消息”的开关。全部持久化保存。
 * 位置 x/y 以像素保存（屏幕坐标），宽高以 dp 保存。
 * 竖屏和横屏各存一套窗口位置 / 大小：x、y、widthDp… 是竖屏的（沿用旧版本的数据），
 * land 开头的是横屏的；横屏那套还没保存过时为 null，第一次横屏时沿用竖屏的值。
 */
data class OverlaySettings(
    val x: Int = 40,
    val y: Int = 300,
    val widthDp: Int = 260,
    val heightDp: Int = 320,
    val bubbleX: Int = 40,
    val bubbleY: Int = 300,
    val landX: Int? = null,
    val landY: Int? = null,
    val landWidthDp: Int? = null,
    val landHeightDp: Int? = null,
    val landBubbleX: Int? = null,
    val landBubbleY: Int? = null,

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
) {
    /** 某个方向的窗口位置与大小 */
    fun geometry(landscape: Boolean): WindowGeometry = if (landscape) {
        WindowGeometry(
            x = landX ?: x,
            y = landY ?: y,
            widthDp = landWidthDp ?: widthDp,
            heightDp = landHeightDp ?: heightDp,
            bubbleX = landBubbleX ?: bubbleX,
            bubbleY = landBubbleY ?: bubbleY,
        )
    } else {
        WindowGeometry(x, y, widthDp, heightDp, bubbleX, bubbleY)
    }

    fun withPanel(landscape: Boolean, x: Int, y: Int, widthDp: Int, heightDp: Int): OverlaySettings =
        if (landscape) {
            copy(landX = x, landY = y, landWidthDp = widthDp, landHeightDp = heightDp)
        } else {
            copy(x = x, y = y, widthDp = widthDp, heightDp = heightDp)
        }

    fun withBubble(landscape: Boolean, x: Int, y: Int): OverlaySettings =
        if (landscape) copy(landBubbleX = x, landBubbleY = y) else copy(bubbleX = x, bubbleY = y)
}

data class WindowGeometry(
    val x: Int,
    val y: Int,
    val widthDp: Int,
    val heightDp: Int,
    val bubbleX: Int,
    val bubbleY: Int,
)
