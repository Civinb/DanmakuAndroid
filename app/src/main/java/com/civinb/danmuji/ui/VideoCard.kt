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
import com.civinb.danmuji.util.tr

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
                    is LinkTarget.Live -> error = tr("这是直播间，请在上面的“直播弹幕”里连接", "This is a live room; connect it in \"Live danmaku\" above")
                    is LinkTarget.Invalid -> error = target.reason
                    is LinkTarget.NeedsRedirect -> error = tr("短链跳转次数过多", "Too many short-link redirects")
                }
            } catch (e: Exception) {
                error = tr("加载失败：${e.message}", "Failed to load: ${e.message}")
            } finally {
                loading = false
            }
        }
    }

    fun start(v: VideoInfo, page: Int) {
        val id = v.bvid.ifEmpty { "av${v.aid}" }
        if (id == "av0") {
            Toast.makeText(context, tr("缺少视频编号", "Missing video ID"), Toast.LENGTH_SHORT).show()
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

    SectionCard(tr("视频弹幕", "Video danmaku")) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(tr("BV 号 / 视频链接 / 分享文字", "BV ID / video link / shared text")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { load(input) }, enabled = !loading) { Text(if (loading) tr("加载中…", "Loading…") else tr("加载", "Load")) }
            OutlinedButton(onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, tr("剪贴板是空的", "Clipboard is empty"), Toast.LENGTH_SHORT).show()
                } else {
                    input = text.trim()
                    load(input)
                }
            }) { Text(tr("粘贴并加载", "Paste & load")) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        info?.let { v ->
            Text(
                v.title.ifEmpty { tr("${v.bvid.ifEmpty { "av${v.aid}" }}（标题获取失败，不影响弹幕）", "${v.bvid.ifEmpty { "av${v.aid}" }} (couldn't get the title; danmaku are unaffected)") },
                style = MaterialTheme.typography.titleSmall,
            )
            if (v.pages.size == 1) {
                Button(onClick = { start(v, 1) }, enabled = overlayGranted) { Text(tr("开始", "Start")) }
            } else {
                HintText(tr("共 ${v.pages.size} 个分P，点一个开始：", "${v.pages.size} parts, tap one to start:"))
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
            tr("进度同步：默认“自动”，读取 B 站 App 公开给系统的播放进度，需要“通知使用权”；", "Progress sync: \"Auto\" by default, reading the playback progress the Bilibili app exposes to the system (needs \"Notification access\"); ") +
                tr("悬浮窗控制条上的 −5/−1/+1/+5 用来微调弹幕时间（偏移）。点“自动”可切到“手动”，自己控制播放/暂停/拖动。", "the −5/−1/+1/+5 buttons on the overlay fine-tune the danmaku timing (offset). Tap \"Auto\" to switch to \"Manual\" and control play/pause/seek yourself."),
        )
        StatusRow(tr("通知使用权（自动同步用）", "Notification access (for auto sync)"), listenerEnabled, if (listenerEnabled) null else tr("去开启", "Enable")) {
            Permissions.open(context, Permissions.notificationListenerSettingsIntent())
        }
        if (!listenerEnabled) {
            HintText(
                tr("本应用不读取任何通知内容，只用它获得读取播放进度的资格。", "This app doesn't read any notification content; it only uses this to be allowed to read playback progress. ") +
                    tr("如果开关是灰色、提示“受限制的设置”：到 应用信息 → 右上角 ⋮ → “允许受限制的设置”，", "If the switch is grayed out with \"Restricted setting\": go to App info → ⋮ (top right) → \"Allow restricted settings\", ") +
                    tr("再回来开启（Android 13+ 对不是从应用商店安装的 App 有此限制）。", "then come back and enable it (Android 13+ restricts apps not installed from an app store)."),
            )
        }
    }
}
