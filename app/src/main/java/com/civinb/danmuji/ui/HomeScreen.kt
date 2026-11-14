package com.civinb.danmuji.ui

import android.Manifest
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.civinb.danmuji.service.OverlayService
import com.civinb.danmuji.util.LastInput
import com.civinb.danmuji.util.Permissions
import com.civinb.danmuji.util.ShareInbox
import com.civinb.danmuji.util.tr

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    var notificationGranted by remember { mutableStateOf(Permissions.notificationsGranted(context)) }

    // 从系统设置页返回时刷新权限状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayGranted = Permissions.canDrawOverlays(context)
        notificationGranted = Permissions.notificationsGranted(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }

    val running by OverlayService.running.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(LastInput.get(context)) }

    // 从 B 站 App 分享进来的内容填入输入框
    val shared by ShareInbox.text.collectAsStateWithLifecycle()
    val sharedVideo by ShareInbox.video.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let {
            input = it
            ShareInbox.text.value = null
        }
    }

    fun connect() {
        val text = input.trim()
        if (text.isEmpty()) {
            Toast.makeText(context, tr("请输入直播间号或链接", "Enter a live room number or link"), Toast.LENGTH_SHORT).show()
            return
        }
        LastInput.save(context, text)
        OverlayService.startInput(context, text)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(tr("必要权限", "Required permissions")) {
            StatusRow(tr("悬浮窗权限", "Overlay permission"), overlayGranted, if (overlayGranted) null else tr("去授权", "Grant")) {
                Permissions.open(context, Permissions.overlaySettingsIntent(context))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                StatusRow(tr("通知权限（显示运行状态和锁定/解锁按钮）", "Notifications (shows status and lock/unlock buttons)"), notificationGranted, if (notificationGranted) null else tr("授权", "Allow")) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            HintText(tr("通知权限被拒绝时服务仍可运行，但通知栏里不会出现“解锁/停止”按钮。", "If notifications are denied the service still runs, but the notification won't have the Unlock/Stop buttons."))
        }

        SectionCard(tr("直播弹幕", "Live danmaku")) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(tr("直播间号 / 直播间链接 / 分享文字", "Room number / room link / shared text")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { connect() }, enabled = overlayGranted) {
                    Text(if (running) tr("切换到此直播间", "Switch to this room") else tr("连接", "Connect"))
                }
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text.isNullOrBlank()) {
                        Toast.makeText(context, tr("剪贴板是空的", "Clipboard is empty"), Toast.LENGTH_SHORT).show()
                    } else {
                        input = text.trim()
                    }
                }) { Text(tr("粘贴", "Paste")) }
                OutlinedButton(onClick = { OverlayService.stop(context) }, enabled = running) { Text(tr("停止", "Stop")) }
            }
            HintText(
                tr("也可以在 B 站 App 直播间点“分享”，在系统分享面板里选“弹幕机”直接启动。\n", "You can also tap \"Share\" in a Bilibili live room and pick this app in the system share sheet to start directly.\n") +
                    tr("未登录时 B 站会把其他用户的昵称打码（如“张**”），这是 B 站的规则。\n", "When not logged in, Bilibili masks other users' names (e.g. \"张**\"); this is Bilibili's rule.\n") +
                    tr("醒目留言 / 礼物 / 进场消息默认不显示，可在“设置 → 悬浮窗样式”里打开。", "Super Chats / gifts / entry messages are hidden by default; turn them on in \"Settings → Overlay style\"."),
            )
        }

        VideoCard(
            overlayGranted = overlayGranted,
            initialInput = sharedVideo,
            onInputConsumed = { ShareInbox.video.value = null },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onNavigate(Screen.SETTINGS) }, modifier = Modifier.weight(1f)) {
                Text(tr("设置", "Settings"))
            }
            OutlinedButton(onClick = { onNavigate(Screen.HELP) }, modifier = Modifier.weight(1f)) {
                Text(tr("帮助与关于", "Help & about"))
            }
        }
        HintText(tr("设置：悬浮窗样式、过滤规则、B 站账号　帮助与关于：权限与后台保活、连接日志、检查更新", "Settings: overlay style, filters, Bilibili account    Help & about: permissions & background, connection log, updates"))
    }
}
