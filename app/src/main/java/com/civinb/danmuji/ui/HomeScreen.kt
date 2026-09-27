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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    var rate by rememberSaveable { mutableIntStateOf(5) }
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
            Toast.makeText(context, "请输入直播间号或链接", Toast.LENGTH_SHORT).show()
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
        SectionCard("必要权限") {
            StatusRow("悬浮窗权限", overlayGranted, if (overlayGranted) null else "去授权") {
                Permissions.open(context, Permissions.overlaySettingsIntent(context))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                StatusRow("通知权限（显示运行状态和锁定/解锁按钮）", notificationGranted, if (notificationGranted) null else "授权") {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            HintText("通知权限被拒绝时服务仍可运行，但通知栏里不会出现“解锁/停止”按钮。")
        }

        SectionCard("直播弹幕") {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("直播间号 / 直播间链接 / 分享文字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { connect() }, enabled = overlayGranted) {
                    Text(if (running) "切换到此直播间" else "连接")
                }
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text.isNullOrBlank()) {
                        Toast.makeText(context, "剪贴板是空的", Toast.LENGTH_SHORT).show()
                    } else {
                        input = text.trim()
                    }
                }) { Text("粘贴") }
                OutlinedButton(onClick = { OverlayService.stop(context) }, enabled = running) { Text("停止") }
            }
            HintText(
                "也可以在 B 站 App 直播间点“分享”，在系统分享面板里选“弹幕机”直接启动。\n" +
                    "未登录时 B 站会把其他用户的昵称打码（如“张**”），这是 B 站的规则。\n" +
                    "醒目留言 / 礼物 / 进场消息默认不显示，可在“悬浮窗样式”里打开。",
            )
        }

        SectionCard("测试：模拟弹幕") {
            HintText("不连接 B 站，按固定速度产生假弹幕。200 条/秒用于压力测试。")
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(5, 50, 200).forEach { r ->
                    RadioButton(selected = rate == r, onClick = { rate = r })
                    Text("$r/秒", Modifier.padding(end = 8.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { OverlayService.startFake(context, rate) },
                    enabled = overlayGranted,
                ) { Text(if (running) "切换速度" else "启动悬浮窗") }
                OutlinedButton(
                    onClick = { OverlayService.stop(context) },
                    enabled = running,
                ) { Text("停止") }
            }
        }

        SectionCard("视频弹幕") {
            HintText("第 4 步实现。")
        }

        SectionCard("更多") {
            OutlinedButton(onClick = { onNavigate(Screen.STYLE) }, modifier = Modifier.fillMaxWidth()) {
                Text("悬浮窗样式")
            }
            OutlinedButton(onClick = { onNavigate(Screen.GUIDE) }, modifier = Modifier.fillMaxWidth()) {
                Text("权限与后台保活（国产 ROM 必看）")
            }
            OutlinedButton(onClick = { onNavigate(Screen.PROBE) }, modifier = Modifier.fillMaxWidth()) {
                Text("媒体会话探针（验证能否自动同步视频进度）")
            }
            OutlinedButton(onClick = { onNavigate(Screen.LOG) }, modifier = Modifier.fillMaxWidth()) {
                Text("连接日志（出问题时复制给开发者）")
            }
        }
    }
}
