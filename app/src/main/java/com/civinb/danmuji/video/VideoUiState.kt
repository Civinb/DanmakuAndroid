package com.civinb.danmuji.video

/** 悬浮窗视频控制条显示用的状态 */
data class VideoUiState(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val mode: SyncMode,
    val autoStatus: AutoStatus?,
    val offsetMs: Long,
    /** 自动模式下，B 站当前播放的标题看起来和本视频对不上 */
    val titleMismatch: Boolean,
    val sessionTitle: String?,
    val loadedSegments: Int,
    val totalSegments: Int,
    val danmakuCount: Int,
)
