package com.civinb.danmuji.video

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock

/** 黑屏控制条上显示的 B 站播放状态 */
data class MediaStatus(
    val title: String?,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    /** B 站的会话是否声明支持跳转（PlaybackState.ACTION_SEEK_TO） */
    val canSeek: Boolean,
)

/**
 * 通过 B 站 App 的媒体会话发送播放 / 暂停 / 跳转命令（MediaController.TransportControls）。
 * 依赖“通知使用权”，与自动同步共用同一个授权。B 站是否响应这些命令取决于 B 站 App，未实测。
 */
class MediaRemote(private val reader: MediaSessionReader) {

    fun status(): MediaStatus? {
        val c = reader.controller() ?: return null
        val ps = c.playbackState ?: return null
        val playing = ps.state == PlaybackState.STATE_PLAYING
        val md = c.metadata
        return MediaStatus(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            positionMs = position(ps),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            playing = playing,
            canSeek = (ps.actions and PlaybackState.ACTION_SEEK_TO) != 0L,
        )
    }

    /** 返回是否找到了 B 站会话 */
    fun togglePlay(): Boolean {
        val c = reader.controller() ?: return false
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
        return true
    }

    /** 返回是否已发送跳转命令（会话不支持跳转时返回 false） */
    fun seekBy(deltaMs: Long): Boolean {
        val c = reader.controller() ?: return false
        val ps = c.playbackState ?: return false
        if ((ps.actions and PlaybackState.ACTION_SEEK_TO) == 0L) return false
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var target = (position(ps) + deltaMs).coerceAtLeast(0)
        if (duration > 0) target = target.coerceAtMost(duration)
        c.transportControls.seekTo(target)
        return true
    }

    private fun position(ps: PlaybackState): Long {
        val now = SystemClock.elapsedRealtime()
        return if (ps.state == PlaybackState.STATE_PLAYING && ps.lastPositionUpdateTime > 0) {
            ps.position + ((now - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
        } else {
            ps.position
        }.coerceAtLeast(0)
    }
}
