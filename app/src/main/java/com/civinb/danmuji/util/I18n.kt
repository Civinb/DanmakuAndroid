package com.civinb.danmuji.util

/**
 * 界面语言（中文 / 英文）。纯 Kotlin，不依赖 Android，所以后台服务、数据层、单元测试里都能用。
 * 当前语言由 [LanguageManager] 根据“跟随系统 / 中文 / English”设置决定；单元测试里默认中文。
 */
object I18n {
    @Volatile
    var english: Boolean = false
}

/** 按当前界面语言选择文字 */
fun tr(zh: String, en: String): String = if (I18n.english) en else zh
