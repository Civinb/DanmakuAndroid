package com.civinb.danmuji.probe

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 媒体会话探针：读取系统里所有活跃的媒体会话（其他 App 发布的播放状态），
 * 用来验证 B 站 App 是否公开了播放进度。
 */
class MediaSessionProbe(private val context: Context) {

    data class SessionInfo(
        val key: String,
        val packageName: String,
        val isBilibili: Boolean,
        val state: Int,
        val stateName: String,
        val rawPositionMs: Long,
        val estimatedPositionMs: Long,
        val speed: Float,
        val lastUpdateAgoMs: Long,
        val title: String?,
        val durationMs: Long,
    )

    data class Result(
        val sessions: List<SessionInfo>,
        val error: String?,
        val events: List<String>,
    )

    private val previous = HashMap<String, SessionInfo>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun poll(): Result {
        val msm = context.getSystemService(MediaSessionManager::class.java)
            ?: return Result(emptyList(), "无法获取 MediaSessionManager", emptyList())
        val controllers: List<MediaController> = try {
            msm.getActiveSessions(ComponentName(context, MediaProbeListenerService::class.java))
        } catch (e: SecurityException) {
            return Result(emptyList(), "没有通知使用权（${e.message}）", emptyList())
        }

        val now = SystemClock.elapsedRealtime()
        val sessions = controllers.map { toInfo(it, now) }
        val events = diff(sessions)
        previous.clear()
        sessions.forEach { previous[it.key] = it }
        return Result(sessions, null, events)
    }

    private fun toInfo(c: MediaController, now: Long): SessionInfo {
        val ps: PlaybackState? = c.playbackState
        val state = ps?.state ?: PlaybackState.STATE_NONE
        val raw = ps?.position ?: -1L
        val speed = ps?.playbackSpeed ?: 0f
        val lastUpdate = ps?.lastPositionUpdateTime ?: 0L
        // PlaybackState 只在状态变化时更新 position，播放中的实时位置要自己按时间外推
        val estimated = if (ps != null && state == PlaybackState.STATE_PLAYING && lastUpdate > 0 && raw >= 0) {
            raw + ((now - lastUpdate) * speed).toLong()
        } else {
            raw
        }
        val md: MediaMetadata? = c.metadata
        val pkg = c.packageName
        return SessionInfo(
            key = "$pkg#${c.sessionToken.hashCode()}",
            packageName = pkg,
            isBilibili = pkg.startsWith("tv.danmaku.bili") || pkg.startsWith("com.bilibili"),
            state = state,
            stateName = stateName(state),
            rawPositionMs = raw,
            estimatedPositionMs = estimated,
            speed = speed,
            lastUpdateAgoMs = if (lastUpdate > 0) now - lastUpdate else -1,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        )
    }

    /** 与上一次轮询比较，产生可读的事件日志：出现/消失/状态变化/位置跳变。 */
    private fun diff(current: List<SessionInfo>): List<String> {
        val t = timeFormat.format(Date())
        val out = ArrayList<String>()
        val currentKeys = current.map { it.key }.toSet()
        for (s in current) {
            val old = previous[s.key]
            if (old == null) {
                out += "$t 新会话 ${s.packageName}（${s.stateName}，${fmt(s.estimatedPositionMs)}，标题：${s.title ?: "无"}）"
                continue
            }
            if (old.state != s.state) {
                out += "$t ${s.packageName} 状态 ${old.stateName} → ${s.stateName}，位置 ${fmt(s.estimatedPositionMs)}"
            }
            if (old.rawPositionMs != s.rawPositionMs) {
                val jump = s.estimatedPositionMs - old.estimatedPositionMs
                if (abs(jump) > 1500) {
                    out += "$t ${s.packageName} 位置跳变 ${fmt(old.estimatedPositionMs)} → ${fmt(s.estimatedPositionMs)}"
                } else {
                    out += "$t ${s.packageName} 位置刷新 ${fmt(s.rawPositionMs)}"
                }
            }
            if (old.speed != s.speed) {
                out += "$t ${s.packageName} 倍速 ${old.speed} → ${s.speed}"
            }
            if (old.title != s.title) {
                out += "$t ${s.packageName} 标题变为：${s.title ?: "无"}"
            }
        }
        for ((key, old) in previous) {
            if (key !in currentKeys) out += "$t 会话消失 ${old.packageName}"
        }
        return out
    }

    companion object {
        fun fmt(ms: Long): String {
            if (ms < 0) return "--:--"
            val totalSec = ms / 1000
            val tenth = (ms % 1000) / 100
            return "%d:%02d.%d".format(totalSec / 60, totalSec % 60, tenth)
        }

        fun stateName(state: Int): String = when (state) {
            PlaybackState.STATE_NONE -> "NONE"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
            PlaybackState.STATE_REWINDING -> "REWINDING"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_ERROR -> "ERROR"
            PlaybackState.STATE_CONNECTING -> "CONNECTING"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIP_PREV"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIP_NEXT"
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIP_QUEUE"
            else -> "UNKNOWN($state)"
        }
    }
}
