package com.civinb.danmuji.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.civinb.danmuji.filter.FilterJson
import com.civinb.danmuji.filter.FilterRule
import com.civinb.danmuji.filter.FilterSettings
import com.civinb.danmuji.filter.RuleAction
import com.civinb.danmuji.filter.RuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.filterDataStore: DataStore<Preferences> by preferencesDataStore(name = "filter_settings")

/** 过滤规则持久化（与悬浮窗样式分开存）。 */
class FilterRepository(context: Context) {

    private val store = context.applicationContext.filterDataStore

    val settings: Flow<FilterSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toFilterSettings() }
        .distinctUntilChanged()

    suspend fun update(transform: (FilterSettings) -> FilterSettings) {
        store.edit { p ->
            val next = transform(p.toFilterSettings())
            p[RULES] = FilterJson.rulesToJson(next.rules)
            p[ONLY_MODE] = next.onlyMode
            p[MERGE] = next.mergeDuplicates
            p[MERGE_WINDOW] = next.mergeWindowSec
        }
    }

    /** 添加规则；同类型、同动作、同内容的规则已存在时不重复添加。 */
    suspend fun addRule(type: RuleType, action: RuleAction, pattern: String, note: String = "") {
        val p = pattern.trim()
        if (p.isEmpty()) return
        update { s ->
            if (s.rules.any { it.type == type && it.action == action && it.pattern == p }) {
                s
            } else {
                s.copy(rules = s.rules + FilterRule(System.currentTimeMillis(), type, action, p, true, note))
            }
        }
    }

    private fun Preferences.toFilterSettings(): FilterSettings {
        val d = FilterSettings()
        return FilterSettings(
            rules = FilterJson.rulesFromJson(this[RULES]),
            onlyMode = this[ONLY_MODE] ?: d.onlyMode,
            mergeDuplicates = this[MERGE] ?: d.mergeDuplicates,
            mergeWindowSec = this[MERGE_WINDOW] ?: d.mergeWindowSec,
        )
    }

    private companion object {
        val RULES = stringPreferencesKey("rules_json")
        val ONLY_MODE = booleanPreferencesKey("only_mode")
        val MERGE = booleanPreferencesKey("merge_duplicates")
        val MERGE_WINDOW = intPreferencesKey("merge_window_sec")
    }
}
