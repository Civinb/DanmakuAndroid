package com.civinb.danmuji.video

/** 从系统媒体会话读到的 B 站播放状态 */
sealed interface AutoReading {
    data object NoPermission : AutoReading
    data object NoSession : AutoReading
    data class Ok(
        val positionMs: Long,
        val playing: Boolean,
        val speed: Float,
        val title: String?,
        val durationMs: Long,
    ) : AutoReading
}

enum class SyncMode { AUTO, MANUAL }

enum class AutoStatus { OK, NO_PERMISSION, NO_SESSION }

data class SyncState(
    /** 当前应显示到的弹幕时间点（已加上偏移） */
    val positionMs: Long,
    val playing: Boolean,
    val mode: SyncMode,
    /** 自动模式下的读取状态；手动模式为 null */
    val autoStatus: AutoStatus?,
    val offsetMs: Long,
    val sessionTitle: String?,
    /** 媒体会话报告的时长（毫秒），0 表示未知；用于判断分P */
    val sessionDurationMs: Long = 0,
)

/**
 * 视频播放进度来源（纯逻辑，线程安全）。
 *
 * 自动模式：读取 B 站 App 公开给系统的媒体会话进度（用户实测可用）。“±”按钮调整偏移量。
 * 手动模式：本地时钟，由悬浮窗上的 播放/暂停、拖动、± 控制。
 * 两种模式互相衔接：切到手动时从自动模式最后的位置继续。
 */
class VideoSync(
    private val reader: () -> AutoReading,
    private val now: () -> Long,
) {
    private var mode = SyncMode.AUTO
    private var offsetMs = 0L
    private var durationMs = Long.MAX_VALUE

    private var manualBase = 0L
    private var manualBaseTime = 0L
    private var manualPlaying = false

    private var lastAutoPos = 0L
    private var lastAutoPlaying = false

    /** 开始一个新视频：清零偏移，手动时钟停在 0 */
    @Synchronized
    fun startVideo(durationMs: Long) {
        this.durationMs = if (durationMs > 0) durationMs else Long.MAX_VALUE
        offsetMs = 0
        manualBase = 0
        manualBaseTime = now()
        manualPlaying = false
        lastAutoPos = 0
        lastAutoPlaying = false
    }

    @Synchronized
    fun state(): SyncState {
        if (mode == SyncMode.MANUAL) {
            return SyncState(clamp(manualPosition()), manualPlaying, SyncMode.MANUAL, null, 0, null)
        }
        return when (val r = reader()) {
            is AutoReading.Ok -> {
                lastAutoPos = r.positionMs
                lastAutoPlaying = r.playing
                SyncState(
                    clamp(r.positionMs + offsetMs), r.playing, SyncMode.AUTO, AutoStatus.OK, offsetMs, r.title, r.durationMs,
                )
            }
            AutoReading.NoPermission ->
                SyncState(clamp(lastAutoPos + offsetMs), false, SyncMode.AUTO, AutoStatus.NO_PERMISSION, offsetMs, null)
            AutoReading.NoSession ->
                SyncState(clamp(lastAutoPos + offsetMs), false, SyncMode.AUTO, AutoStatus.NO_SESSION, offsetMs, null)
        }
    }

    @Synchronized
    fun mode(): SyncMode = mode

    @Synchronized
    fun setMode(m: SyncMode) {
        if (m == mode) return
        if (m == SyncMode.MANUAL) {
            manualBase = clamp(lastAutoPos + offsetMs)
            manualBaseTime = now()
            manualPlaying = lastAutoPlaying
        }
        mode = m
    }

    fun toggleMode() = setMode(if (mode() == SyncMode.AUTO) SyncMode.MANUAL else SyncMode.AUTO)

    /** 手动模式：播放/暂停 */
    @Synchronized
    fun togglePlay() {
        if (mode != SyncMode.MANUAL) return
        if (manualPlaying) {
            manualBase = clamp(manualPosition())
            manualPlaying = false
        } else {
            manualBaseTime = now()
            manualPlaying = true
        }
    }

    /** 手动模式：跳到指定位置 */
    @Synchronized
    fun seek(ms: Long) {
        if (mode != SyncMode.MANUAL) return
        manualBase = clamp(ms)
        manualBaseTime = now()
    }

    /** ± 按钮：自动模式调整偏移；手动模式前后跳 */
    @Synchronized
    fun nudge(deltaMs: Long) {
        if (mode == SyncMode.AUTO) {
            offsetMs += deltaMs
        } else {
            manualBase = clamp(manualPosition() + deltaMs)
            manualBaseTime = now()
        }
    }

    private fun manualPosition(): Long =
        if (manualPlaying) manualBase + (now() - manualBaseTime) else manualBase

    private fun clamp(ms: Long): Long = ms.coerceIn(0, durationMs)
}
