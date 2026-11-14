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
    var listenerEnabled by remember { mutableStateOf(Permissions.isNotificationListenerEnabled(context)) }

    // 从系统设置页返回时刷新权限状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayGranted = Permissions.canDrawOverlays(context)
        notificationGranted = Permissions.notificationsGranted(context)
        listenerEnabled = Permissions.isNotificationListenerEnabled(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }

    val running by OverlayService.running.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(LastInput.get(context)) }

    // 从 B 站 App 分享进来的内容填入输入框
    val shared by ShareInbox.text.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let {
            input = it
            ShareInbox.text.value = null
        }
    }

    fun connect() {
        val text = input.trim()
        if (text.isEmpty()) {
            Toast.makeText(context, tr("请输入直播间号、BV 号或链接", "Enter a room number, BV ID or link"), Toast.LENGTH_SHORT).show()
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
                StatusRow(tr("通知权限", "Notifications"), notificationGranted, if (notificationGranted) null else tr("授权", "Allow")) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            StatusRow(tr("通知使用权（视频自动同步需要）", "Notification access (needed for video auto sync)"), listenerEnabled, if (listenerEnabled) null else tr("去开启", "Enable")) {
                Permissions.open(context, Permissions.notificationListenerSettingsIntent())
            }
            if (!listenerEnabled) {
                HintText(tr("开关是灰色时：应用信息 → 右上角 ⋮ → 允许受限制的设置", "If the switch is grayed out: App info → ⋮ (top right) → Allow restricted settings"))
            }
        }

        SectionCard(tr("视频/直播弹幕", "Video/live danmaku")) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(tr("直播间号 / BV 号 / 链接 / 分享文字", "Room number / BV ID / link / shared text")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { connect() }, enabled = overlayGranted) {
                    Text(if (running) tr("切换", "Switch") else tr("开始", "Start"))
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
            HintText(tr("推荐在 B 站 App 的直播间或视频页点“分享 → 更多”，选“弹幕机”直接启动。", "Recommended: in a Bilibili live room or video page, tap \"Share → More\" and pick this app to start directly."))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onNavigate(Screen.SETTINGS) }, modifier = Modifier.weight(1f)) {
                Text(tr("设置", "Settings"))
            }
            OutlinedButton(onClick = { onNavigate(Screen.HELP) }, modifier = Modifier.weight(1f)) {
                Text(tr("帮助与关于", "Help & about"))
            }
        }
    }
}
