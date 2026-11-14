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
            Screen.STYLE to tr("透明度、字号、颜色；醒目留言 / 礼物 / 进场开关；隐藏直播间表情", "Opacity, text size, color; Super Chat / gift / entry toggles; hide live-room emotes"),
            Screen.FILTER to tr("屏蔽关键词 / 正则 / 用户，仅显示模式，合并重复弹幕", "Block keywords / regex / users, show-only mode, merge duplicate danmaku"),
            Screen.ACCOUNT to tr("可选扫码登录（未登录时部分视频弹幕不全、直播昵称打码）", "Optional QR login (without login some video danmaku are incomplete and live usernames are masked)"),
            Screen.LANGUAGE to tr("跟随系统 / 中文 / English", "Follow system / 中文 / English"),
        ),
    )
}

/** 首页“帮助与关于”入口：权限与后台保活、连接日志、关于与更新 */
@Composable
fun HelpMenuScreen(onNavigate: (Screen) -> Unit) {
    MenuList(
        onNavigate,
        listOf(
            Screen.GUIDE to tr("国产 ROM 必看：自启动、电池优化、后台锁定", "Must-read on Chinese ROMs: auto-start, battery optimization, lock in recents"),
            Screen.LOG to tr("出问题时复制给开发者", "Copy and send to the developer when something goes wrong"),
            Screen.ABOUT to tr("当前版本、GitHub 仓库、检查更新", "Current version, GitHub repository, check for updates"),
        ),
    )
}

@Composable
private fun MenuList(onNavigate: (Screen) -> Unit, items: List<Pair<Screen, String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { (screen, description) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(screen) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(screen.title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
