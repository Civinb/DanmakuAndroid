package com.civinb.danmuji.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** 应用语言设置 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ZH("zh-CN"),
    EN("en"),
}

/**
 * 管理应用语言：
 *  - Android 13+：用系统的“按应用设置语言”（LocaleManager），应用内的选择和系统设置里的“应用语言”是同一个设置；
 *  - Android 12 及以下：保存在本应用的设置里。
 * 结果写入 [I18n.english]：设置为中文、或跟随系统且系统是中文时用中文，其他情况都用英文。
 */
object LanguageManager {

    private const val PREFS = "language"
    private const val KEY = "app_language"

    fun current(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) return AppLanguage.SYSTEM
            return if (locales[0].language == "zh") AppLanguage.ZH else AppLanguage.EN
        }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return AppLanguage.entries.firstOrNull { it.name == saved } ?: AppLanguage.SYSTEM
    }

    /** 根据当前设置刷新 [I18n.english]；应用启动、界面创建时调用 */
    fun apply(context: Context) {
        I18n.english = when (current(context)) {
            AppLanguage.ZH -> false
            AppLanguage.EN -> true
            AppLanguage.SYSTEM -> systemLocale(context).language != "zh"
        }
    }

    /**
     * 修改语言。Android 13+ 由系统保存并自动重建界面；
     * Android 12 及以下保存后返回 true，调用方需要自己重建 Activity。
     */
    fun set(context: Context, language: AppLanguage): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = if (language == AppLanguage.SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = list
            apply(context)
            return false
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).apply()
        apply(context)
        return true
    }

    private fun systemLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sys = context.getSystemService(LocaleManager::class.java)?.systemLocales
            if (sys != null && !sys.isEmpty) return sys[0]
        }
        return Resources.getSystem().configuration.locales[0]
    }
}
