package com.civinb.danmuji.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.civinb.danmuji.probe.MediaSessionProbe
import com.civinb.danmuji.probe.ProbeRecorder
import com.civinb.danmuji.service.OverlayService
import com.civinb.danmuji.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ProbeScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Permissions.isNotificationListenerEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = Permissions.isNotificationListenerEnabled(context)
    }

    val sessions by ProbeRecorder.sessions.collectAsStateWithLifecycle()
    val error by ProbeRecorder.error.collectAsStateWithLifecycle()
    val log by ProbeRecorder.log.collectAsStateWithLifecycle()
    val showInOverlay by ProbeRecorder.showInOverlay.collectAsStateWithLifecycle()
    val overlayRunning by OverlayService.running.collectAsStateWithLifecycle()

    // 本页面可见时每 0.5 秒采样一次；离开页面自动停止
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            withContext(Dispatchers.Default) { ProbeRecorder.poll(context) }
            delay(500)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("这是做什么的") {
            Text(
                "验证 B 站 App 播放视频时，是否把“播放进度”公开给系统（MediaSession）。" +
                    "如果公开了，第 4 步就能让弹幕自动跟随你在 B 站 App 里的播放/暂停/拖动；" +
                    "如果没有，就只能手动同步。",
            )
            HintText("需要“通知使用权”。本应用不读取任何通知内容，只用它获得读取媒体会话的资格。")
        }

        SectionCard("1. 授予通知使用权") {
            StatusRow("通知使用权", enabled, if (enabled) "管理" else "去开启") {
                Permissions.open(context, Permissions.notificationListenerSettingsIntent())
            }
            HintText(
                "如果开关是灰色、提示“受限制的设置”：到 应用信息 → 右上角 ⋮ → “允许受限制的设置”，" +
                    "再回来开启（Android 13+ 对不是从应用商店安装的 App 有此限制）。",
            )
        }

        SectionCard("2. 在悬浮窗里实时显示") {
            SwitchRow(
                "悬浮窗标题栏显示探针结果",
                showInOverlay,
                if (overlayRunning) "悬浮窗运行中" else "需要先在首页启动悬浮窗（5 条/秒即可）",
            ) { ProbeRecorder.showInOverlay.value = it }
            HintText("打开后，你在 B 站 App 里看视频时，悬浮窗标题栏会实时显示读到的状态和进度，同时在后台记录事件日志。")
        }

        SectionCard("3. 测试步骤") {
            Text(
                "① 首页启动悬浮窗，打开上面的开关。\n" +
                    "② 去 B 站 App 播放一个视频，看悬浮窗标题栏：位置是否在走、和 B 站进度条是否一致。\n" +
                    "③ 依次：暂停 → 等 3 秒 → 拖动进度条 → 继续播放 → 切换倍速 → 全屏/退出全屏 → 换一个视频。\n" +
                    "④ 在 B 站 App 设置里分别打开/关闭“后台播放”再试一次。\n" +
                    "⑤ 回到本页面点“复制结果”，把内容发给我。",
            )
        }

        SectionCard("4. 当前媒体会话") {
            when {
                !enabled -> HintText("请先开启通知使用权。")
                error != null -> Text("错误：$error", color = MaterialTheme.colorScheme.error)
                sessions.isEmpty() -> HintText("系统里目前没有任何活跃的媒体会话。")
                else -> sessions.forEach { s -> SessionBlock(s) }
            }
        }

        SectionCard("5. 事件日志（最新在上）") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copyResult(context, sessions, log) }) { Text("复制结果") }
                OutlinedButton(onClick = { ProbeRecorder.clearLog() }) { Text("清空日志") }
            }
            if (log.isEmpty()) {
                HintText("暂无事件")
            } else {
                log.take(80).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun SessionBlock(s: MediaSessionProbe.SessionInfo) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            (if (s.isBilibili) "【B站】" else "") + s.packageName,
            style = MaterialTheme.typography.titleSmall,
            color = if (s.isBilibili) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(describe(s), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

private fun describe(s: MediaSessionProbe.SessionInfo): String =
    "状态: ${s.stateName}  倍速: ${s.speed}\n" +
        "推算位置: ${MediaSessionProbe.fmt(s.estimatedPositionMs)}  原始位置: ${MediaSessionProbe.fmt(s.rawPositionMs)}\n" +
        "原始位置更新于: ${if (s.lastUpdateAgoMs >= 0) "${s.lastUpdateAgoMs / 1000} 秒前" else "未知"}\n" +
        "时长: ${MediaSessionProbe.fmt(s.durationMs)}  标题: ${s.title ?: "无"}"

private fun copyResult(context: Context, sessions: List<MediaSessionProbe.SessionInfo>, log: List<String>) {
    val text = buildString {
        appendLine("=== 当前会话 ===")
        if (sessions.isEmpty()) appendLine("（无）")
        sessions.forEach {
            appendLine(it.packageName)
            appendLine(describe(it))
            appendLine()
        }
        appendLine("=== 事件日志（最新在上）===")
        log.forEach { appendLine(it) }
    }
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("media probe", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
