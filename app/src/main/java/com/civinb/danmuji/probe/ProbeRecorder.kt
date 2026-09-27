package com.civinb.danmuji.probe

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 探针的共享状态。探针页面和悬浮窗服务都调用 [poll]，结果和事件日志放在同一处，
 * 这样你在 B 站 App 里操作时（探针页面不可见），只要悬浮窗开着，事件照样被记录。
 */
object ProbeRecorder {

    private var probe: MediaSessionProbe? = null

    private val _sessions = MutableStateFlow<List<MediaSessionProbe.SessionInfo>>(emptyList())
    val sessions: StateFlow<List<MediaSessionProbe.SessionInfo>> = _sessions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 最新的在最前面 */
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /** 是否在悬浮窗标题栏实时显示探针结果（同时在后台持续记录日志） */
    val showInOverlay = MutableStateFlow(false)

    @Synchronized
    fun poll(context: Context) {
        val p = probe ?: MediaSessionProbe(context.applicationContext).also { probe = it }
        val r = p.poll()
        _sessions.value = r.sessions
        _error.value = r.error
        if (r.events.isNotEmpty()) {
            _log.value = (r.events.asReversed() + _log.value).take(MAX_LOG)
        }
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun overlaySummary(): String {
        _error.value?.let { return "探针：$it" }
        val all = _sessions.value
        val s = all.firstOrNull { it.isBilibili } ?: all.firstOrNull() ?: return "探针：无媒体会话"
        val name = if (s.isBilibili) "B站" else s.packageName
        return "探针：$name ${s.stateName} ${MediaSessionProbe.fmt(s.estimatedPositionMs)} ×${s.speed}"
    }

    private const val MAX_LOG = 300
}
