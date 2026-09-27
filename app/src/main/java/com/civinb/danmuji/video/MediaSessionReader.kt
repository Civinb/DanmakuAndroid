package com.civinb.danmuji.video

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.civinb.danmuji.probe.MediaProbeListenerService

/**
 * 读取 B 站 App 发布到系统的媒体会话（需要“通知使用权”，授权对象是 MediaProbeListenerService）。
 * 会话列表每 2 秒刷新一次，其余时间直接读缓存的控制器，降低开销。
 */
class MediaSessionReader(context: Context) {

    private val appContext = context.applicationContext
    private val msm = appContext.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(appContext, MediaProbeListenerService::class.java)
    private var cached: MediaController? = null
    private var lastRefresh = 0L

    @Synchronized
    fun read(): AutoReading {
        val now = SystemClock.elapsedRealtime()
        if (cached == null || now - lastRefresh > REFRESH_MS) {
            val list = try {
                msm?.getActiveSessions(component) ?: emptyList()
            } catch (e: SecurityException) {
                cached = null
                return AutoReading.NoPermission
            }
            cached = list.firstOrNull { isBilibili(it.packageName) }
            lastRefresh = now
        }
        val c = cached ?: return AutoReading.NoSession
        val ps: PlaybackState = c.playbackState ?: return AutoReading.NoSession
        when (ps.state) {
            PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR ->
                return AutoReading.NoSession
        }
        val playing = ps.state == PlaybackState.STATE_PLAYING
        val position = if (playing && ps.lastPositionUpdateTime > 0) {
            ps.position + ((now - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
        } else {
            ps.position
        }
        if (position < 0) return AutoReading.NoSession
        val md: MediaMetadata? = c.metadata
        return AutoReading.Ok(
            positionMs = position,
            playing = playing,
            speed = ps.playbackSpeed,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        )
    }

    private fun isBilibili(pkg: String) = pkg.startsWith("tv.danmaku.bili") || pkg.startsWith("com.bilibili")

    private companion object {
        const val REFRESH_MS = 2_000L
    }
}
