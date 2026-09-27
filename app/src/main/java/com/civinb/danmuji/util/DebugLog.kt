package com.civinb.danmuji.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内的连接日志（最近 300 行）。出问题时用户可以在“连接日志”页复制给开发者。
 * 注意：不要往这里写 Cookie、token 之类的敏感内容。
 */
object DebugLog {
    private const val MAX_LINES = 300
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    /** 可替换为 android.util.Log，方便单元测试时不依赖 Android。 */
    @Volatile
    var sink: ((tag: String, msg: String) -> Unit)? = null

    @Synchronized
    fun log(tag: String, msg: String) {
        val line = "${format.format(Date())} [$tag] $msg"
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        sink?.invoke(tag, msg)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
