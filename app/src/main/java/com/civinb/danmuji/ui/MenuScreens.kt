package com.civinb.danmuji.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.civinb.danmuji.util.tr

/** 首页“设置”入口：悬浮窗样式、过滤规则、B 站账号、语言 */
@Composable
fun SettingsMenuScreen(onNavigate: (Screen) -> Unit) {
    MenuList(
        onNavigate,
        listOf(
            Screen.STYLE,
            Screen.FILTER,
            Screen.ACCOUNT,
            Screen.LANGUAGE,
        ),
    )
}

/** 首页“帮助与关于”入口：权限与后台保活、连接日志、关于与更新 */
@Composable
fun HelpMenuScreen(onNavigate: (Screen) -> Unit) {
    MenuList(
        onNavigate,
        listOf(
            Screen.GUIDE,
            Screen.LOG,
            Screen.ABOUT,
        ),
    )
}

@Composable
private fun MenuList(onNavigate: (Screen) -> Unit, items: List<Screen>) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { screen ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(screen) },
            ) {
                Text(screen.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
