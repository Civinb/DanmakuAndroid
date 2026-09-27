package com.civinb.danmuji.filter

import org.json.JSONArray
import org.json.JSONObject

enum class RuleType { KEYWORD, REGEX, USER }

/** BLOCK：命中就不显示；ONLY：“仅显示”模式下，只显示命中这类规则的弹幕 */
enum class RuleAction { BLOCK, ONLY }

/**
 * 一条过滤规则。
 * USER 规则的 pattern 可以是：UID 数字（直播已登录/豁免房间才有）、用户名（未登录时是打码后的名字）、
 * 或视频弹幕的发送者 hash（第 4 步）。[note] 只用于界面显示，例如用户名。
 */
data class FilterRule(
    val id: Long,
    val type: RuleType,
    val action: RuleAction,
    val pattern: String,
    val enabled: Boolean = true,
    val note: String = "",
)

data class FilterSettings(
    val rules: List<FilterRule> = emptyList(),
    /** 仅显示模式：打开后普通弹幕只显示命中 ONLY 规则的 */
    val onlyMode: Boolean = false,
    /** 合并短时间内的重复弹幕（显示 ×N） */
    val mergeDuplicates: Boolean = false,
    val mergeWindowSec: Int = 10,
)

/** 规则列表的 JSON 存取（存在 DataStore 的一个字符串里）。 */
object FilterJson {

    fun rulesToJson(rules: List<FilterRule>): String {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("type", r.type.name)
                    .put("action", r.action.name)
                    .put("pattern", r.pattern)
                    .put("enabled", r.enabled)
                    .put("note", r.note),
            )
        }
        return arr.toString()
    }

    fun rulesFromJson(json: String?): List<FilterRule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val type = enumOrNull<RuleType>(o.optString("type")) ?: continue
                    val action = enumOrNull<RuleAction>(o.optString("action")) ?: continue
                    val pattern = if (o.isNull("pattern")) "" else o.optString("pattern")
                    if (pattern.isEmpty()) continue
                    add(
                        FilterRule(
                            id = o.optLong("id"),
                            type = type,
                            action = action,
                            pattern = pattern,
                            enabled = o.optBoolean("enabled", true),
                            note = if (o.isNull("note")) "" else o.optString("note"),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
