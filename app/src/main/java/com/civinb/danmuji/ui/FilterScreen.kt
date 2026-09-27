package com.civinb.danmuji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.civinb.danmuji.DanmuApp
import com.civinb.danmuji.filter.FilterEngine
import com.civinb.danmuji.filter.FilterRule
import com.civinb.danmuji.filter.FilterSettings
import com.civinb.danmuji.filter.RuleAction
import com.civinb.danmuji.filter.RuleType
import com.civinb.danmuji.service.OverlayService
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FilterScreen() {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as DanmuApp).filterRepository }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null as FilterSettings?)
    val filtered by OverlayService.filteredCount.collectAsStateWithLifecycle()
    val s = settings ?: return

    fun update(transform: (FilterSettings) -> FilterSettings) {
        scope.launch { repo.update(transform) }
    }

    fun add(type: RuleType, action: RuleAction, pattern: String) {
        scope.launch { repo.addRule(type, action, pattern) }
    }

    val blockRules = s.rules.filter { it.action == RuleAction.BLOCK }
    val onlyRules = s.rules.filter { it.action == RuleAction.ONLY }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HintText(
            "规则修改后立即生效。新增屏蔽规则时，悬浮窗里已显示的匹配弹幕也会被移除。" +
                "也可以在悬浮窗里长按某条弹幕，选择“屏蔽此用户 / 屏蔽这句”。本次连接已过滤 $filtered 条。",
        )

        SectionCard("屏蔽规则（${blockRules.size}）") {
            HintText(
                "关键词：包含即屏蔽，不区分大小写。\n" +
                    "正则：Java 正则语法，需要不区分大小写可在开头加 (?i)。\n" +
                    "用户：填用户名或 UID。注意未登录时 B 站把昵称打码（如“张**”）、UID 为 0，" +
                    "按打码名字屏蔽会同时屏蔽所有同样打码的用户。",
            )
            RuleEditor(onAdd = { type, pattern -> add(type, RuleAction.BLOCK, pattern) })
            RuleList(
                rules = blockRules,
                onToggle = { rule, on -> update { st -> st.copy(rules = st.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) } },
                onDelete = { rule -> update { st -> st.copy(rules = st.rules.filterNot { it.id == rule.id }) } },
            )
        }

        SectionCard("仅显示模式") {
            SwitchRow(
                "只显示命中下面规则的弹幕",
                s.onlyMode,
                "例如只看同传：添加关键词“【同传】”，或正则 ^【.*】。只作用于普通弹幕，醒目留言/礼物/进场仍由各自开关决定。",
            ) { v -> update { it.copy(onlyMode = v) } }
            if (s.onlyMode && onlyRules.none { it.enabled }) {
                Text("当前没有启用的“仅显示”规则，普通弹幕将全部不显示。", color = MaterialTheme.colorScheme.error)
            }
            RuleEditor(onAdd = { type, pattern -> add(type, RuleAction.ONLY, pattern) })
            RuleList(
                rules = onlyRules,
                onToggle = { rule, on -> update { st -> st.copy(rules = st.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) } },
                onDelete = { rule -> update { st -> st.copy(rules = st.rules.filterNot { it.id == rule.id }) } },
            )
        }

        SectionCard("合并重复弹幕") {
            SwitchRow(
                "合并短时间内的相同弹幕",
                s.mergeDuplicates,
                "时间窗口内内容相同的弹幕只显示一行，末尾显示 ×N（忽略大小写和首尾空格）。",
            ) { v -> update { it.copy(mergeDuplicates = v) } }
            SettingSlider("时间窗口", s.mergeWindowSec.toFloat(), 3f..60f, 56, { "${it.roundToInt()} 秒" }) { v ->
                update { it.copy(mergeWindowSec = v.roundToInt()) }
            }
        }
    }
}

/** 添加规则：选择类型 + 输入内容。正则会先校验。 */
@Composable
private fun RuleEditor(onAdd: (RuleType, String) -> Unit) {
    var type by rememberSaveable { mutableStateOf(RuleType.KEYWORD) }
    var text by rememberSaveable { mutableStateOf("") }
    val regexError = if (type == RuleType.REGEX && text.isNotEmpty()) FilterEngine.regexError(text) else null

    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(RuleType.KEYWORD to "关键词", RuleType.REGEX to "正则", RuleType.USER to "用户").forEach { (t, label) ->
            RadioButton(selected = type == t, onClick = { type = t })
            Text(label, Modifier.padding(end = 6.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            isError = regexError != null,
            label = {
                Text(
                    when (type) {
                        RuleType.KEYWORD -> "关键词"
                        RuleType.REGEX -> "正则表达式"
                        RuleType.USER -> "用户名或 UID"
                    },
                )
            },
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                onAdd(type, text.trim())
                text = ""
            },
            enabled = text.isNotBlank() && regexError == null,
        ) { Text("添加") }
    }
    if (regexError != null) {
        Text("正则有误：$regexError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RuleList(
    rules: List<FilterRule>,
    onToggle: (FilterRule, Boolean) -> Unit,
    onDelete: (FilterRule) -> Unit,
) {
    if (rules.isEmpty()) {
        HintText("暂无规则")
        return
    }
    HorizontalDivider()
    rules.forEach { rule ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.pattern, maxLines = 2)
                val kind = when (rule.type) {
                    RuleType.KEYWORD -> "关键词"
                    RuleType.REGEX -> "正则"
                    RuleType.USER -> "用户"
                }
                HintText(if (rule.note.isNotEmpty()) "$kind · ${rule.note}" else kind)
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) })
            TextButton(onClick = { onDelete(rule) }) { Text("删除") }
        }
    }
}
