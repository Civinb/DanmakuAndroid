package com.civinb.danmuji.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** 记住上次输入的直播间 / 链接。 */
object LastInput {
    private const val PREFS = "ui"
    private const val KEY = "last_input"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun save(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }
}

/** 从 B 站 App 分享进来的文字，首页读取后填入输入框。 */
object ShareInbox {
    /** 分享进来的直播内容 */
    val text = MutableStateFlow<String?>(null)

    /** 分享进来的多P视频（需要在首页选择分P） */
    val video = MutableStateFlow<String?>(null)
}
