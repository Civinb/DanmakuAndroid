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
import com.civinb.danmuji.util.tr

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
        HintText(tr("悬浮窗里长按弹幕也可以屏蔽。本次已过滤 $filtered 条。", "You can also long-press a danmaku in the overlay to block it. Filtered $filtered so far."))

        SectionCard(tr("屏蔽规则（${blockRules.size}）", "Block rules (${blockRules.size})")) {
            HintText(tr("未登录时昵称被打码，按用户屏蔽会误伤同名的人。", "Without login names are masked, so blocking a user may also block others with the same masked name."))
            RuleEditor(onAdd = { type, pattern -> add(type, RuleAction.BLOCK, pattern) })
            RuleList(
                rules = blockRules,
                onToggle = { rule, on -> update { st -> st.copy(rules = st.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) } },
                onDelete = { rule -> update { st -> st.copy(rules = st.rules.filterNot { it.id == rule.id }) } },
            )
        }

        SectionCard(tr("仅显示模式", "Show-only mode")) {
            SwitchRow(
                tr("只显示命中下面规则的弹幕", "Only show danmaku matching the rules below"),
                s.onlyMode,
            ) { v -> update { it.copy(onlyMode = v) } }
            if (s.onlyMode && onlyRules.none { it.enabled }) {
                Text(tr("当前没有启用的“仅显示”规则，普通弹幕将全部不显示。", "No show-only rules are enabled, so no regular danmaku will be shown."), color = MaterialTheme.colorScheme.error)
            }
            RuleEditor(onAdd = { type, pattern -> add(type, RuleAction.ONLY, pattern) })
            RuleList(
                rules = onlyRules,
                onToggle = { rule, on -> update { st -> st.copy(rules = st.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) } },
                onDelete = { rule -> update { st -> st.copy(rules = st.rules.filterNot { it.id == rule.id }) } },
            )
        }

        SectionCard(tr("合并重复弹幕", "Merge duplicates")) {
            SwitchRow(
                tr("合并短时间内的相同弹幕", "Merge identical danmaku within a short time"),
                s.mergeDuplicates,
            ) { v -> update { it.copy(mergeDuplicates = v) } }
            SettingSlider(tr("时间窗口", "Time window"), s.mergeWindowSec.toFloat(), 3f..60f, 56, { tr("${it.roundToInt()} 秒", "${it.roundToInt()} s") }) { v ->
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
        listOf(RuleType.KEYWORD to tr("关键词", "Keyword"), RuleType.REGEX to tr("正则", "Regex"), RuleType.USER to tr("用户", "User")).forEach { (t, label) ->
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
                        RuleType.KEYWORD -> tr("关键词", "Keyword")
                        RuleType.REGEX -> tr("正则表达式", "Regular expression")
                        RuleType.USER -> tr("用户名或 UID", "Username or UID")
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
        ) { Text(tr("添加", "Add")) }
    }
    if (regexError != null) {
        Text(tr("正则有误：$regexError", "Invalid regex: $regexError"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RuleList(
    rules: List<FilterRule>,
    onToggle: (FilterRule, Boolean) -> Unit,
    onDelete: (FilterRule) -> Unit,
) {
    if (rules.isEmpty()) {
        HintText(tr("暂无规则", "No rules"))
        return
    }
    HorizontalDivider()
    rules.forEach { rule ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.pattern, maxLines = 2)
                val kind = when (rule.type) {
                    RuleType.KEYWORD -> tr("关键词", "Keyword")
                    RuleType.REGEX -> tr("正则", "Regex")
                    RuleType.USER -> tr("用户", "User")
                }
                HintText(if (rule.note.isNotEmpty()) "$kind · ${rule.note}" else kind)
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) })
            TextButton(onClick = { onDelete(rule) }) { Text(tr("删除", "Delete")) }
        }
    }
}
