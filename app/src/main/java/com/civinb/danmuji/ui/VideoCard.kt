package com.civinb.danmuji.ui

import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.civinb.danmuji.DanmuApp
import com.civinb.danmuji.data.bili.link.LinkTarget
import com.civinb.danmuji.data.bili.video.VideoInfo
import com.civinb.danmuji.service.OverlayService
import com.civinb.danmuji.util.Permissions
import kotlinx.coroutines.launch

/**
 * 首页“视频弹幕”卡片：输入 BV 号 / 链接 → 加载视频信息 → 选择分P → 启动。
 * 启动时把“BV号 + ?p=N”交给服务，和直播一样走统一的输入解析流程。
 */
@Composable
fun VideoCard(overlayGranted: Boolean, initialInput: String?, onInputConsumed: () -> Unit) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as DanmuApp }
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var listenerEnabled by remember { mutableStateOf(Permissions.isNotificationListenerEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        listenerEnabled = Permissions.isNotificationListenerEnabled(context)
    }

    fun load(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        loading = true
        error = null
        info = null
        scope.launch {
            try {
                when (val target = app.bili.linkResolver.resolve(t)) {
                    is LinkTarget.Video -> info = app.bili.videoApi.getVideoInfo(target.bvid, target.aid)
                    is LinkTarget.Live -> error = "这是直播间，请在上面的“直播弹幕”里连接"
                    is LinkTarget.Invalid -> error = target.reason
                    is LinkTarget.NeedsRedirect -> error = "短链跳转次数过多"
                }
            } catch (e: Exception) {
                error = "加载失败：${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun start(v: VideoInfo, page: Int) {
        val id = v.bvid.ifEmpty { "av${v.aid}" }
        if (id == "av0") {
            Toast.makeText(context, "缺少视频编号", Toast.LENGTH_SHORT).show()
            return
        }
        OverlayService.startInput(context, "https://www.bilibili.com/video/$id?p=$page")
    }

    // 从 B 站 App 分享进来的视频链接：填入并自动加载
    LaunchedEffect(initialInput) {
        if (!initialInput.isNullOrBlank()) {
            input = initialInput
            onInputConsumed()
            load(initialInput)
        }
    }

    SectionCard("视频弹幕") {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("BV 号 / 视频链接 / 分享文字") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { load(input) }, enabled = !loading) { Text(if (loading) "加载中…" else "加载") }
            OutlinedButton(onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, "剪贴板是空的", Toast.LENGTH_SHORT).show()
                } else {
                    input = text.trim()
                    load(input)
                }
            }) { Text("粘贴并加载") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        info?.let { v ->
            Text(
                v.title.ifEmpty { "${v.bvid.ifEmpty { "av${v.aid}" }}（标题获取失败，不影响弹幕）" },
                style = MaterialTheme.typography.titleSmall,
            )
            if (v.pages.size == 1) {
                Button(onClick = { start(v, 1) }, enabled = overlayGranted) { Text("开始") }
            } else {
                HintText("共 ${v.pages.size} 个分P，点一个开始：")
                HorizontalDivider()
                Column {
                    v.pages.forEach { p ->
                        Text(
                            "P${p.page}  ${p.part}  (${p.durationSec / 60}:${"%02d".format(p.durationSec % 60)})",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = overlayGranted) { start(v, p.page) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
        HintText(
            "进度同步：默认“自动”，读取 B 站 App 公开给系统的播放进度，需要“通知使用权”；" +
                "悬浮窗控制条上的 −5/−1/+1/+5 用来微调弹幕时间（偏移）。点“自动”可切到“手动”，自己控制播放/暂停/拖动。",
        )
        StatusRow("通知使用权（自动同步用）", listenerEnabled, if (listenerEnabled) null else "去开启") {
            Permissions.open(context, Permissions.notificationListenerSettingsIntent())
        }
        if (!listenerEnabled) {
            HintText(
                "本应用不读取任何通知内容，只用它获得读取播放进度的资格。" +
                    "如果开关是灰色、提示“受限制的设置”：到 应用信息 → 右上角 ⋮ → “允许受限制的设置”，" +
                    "再回来开启（Android 13+ 对不是从应用商店安装的 App 有此限制）。",
            )
        }
    }
}
