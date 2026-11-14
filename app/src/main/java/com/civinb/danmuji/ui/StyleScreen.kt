package com.civinb.danmuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.civinb.danmuji.DanmuApp
import com.civinb.danmuji.settings.OverlaySettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.civinb.danmuji.util.tr

private val PRESET_COLORS = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFFF59D.toInt(),
    0xFFA5D6A7.toInt(),
    0xFF90CAF9.toInt(),
    0xFFF48FB1.toInt(),
    0xFFFFCC80.toInt(),
)

@Composable
fun StyleScreen() {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as DanmuApp).settingsRepository }
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null as OverlaySettings?)
    val s = settings ?: return

    fun update(transform: (OverlaySettings) -> OverlaySettings) {
        scope.launch { repository.update(transform) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HintText(tr("修改后悬浮窗会立即生效（悬浮窗运行时可以边看边调）。", "Changes apply to the overlay immediately (you can adjust while watching)."))

        SectionCard(tr("外观", "Appearance")) {
            SettingSlider(tr("背景不透明度", "Background opacity"), s.bgAlpha, 0f..1f, 19, { "${(it * 100).roundToInt()}%" }) { v ->
                update { it.copy(bgAlpha = v) }
            }
            SettingSlider(tr("字号", "Text size"), s.textSizeSp, 10f..28f, 17, { "${it.roundToInt()} sp" }) { v ->
                update { it.copy(textSizeSp = v.roundToInt().toFloat()) }
            }
            SettingSlider(tr("行间距", "Line spacing"), s.lineSpacingDp, 0f..16f, 15, { "${it.roundToInt()} dp" }) { v ->
                update { it.copy(lineSpacingDp = v.roundToInt().toFloat()) }
            }
            SettingSlider(tr("最多保留条数", "Max items kept"), s.maxItems.toFloat(), 100f..1000f, 17, { tr("${it.roundToInt()} 条", "${it.roundToInt()}") }) { v ->
                update { it.copy(maxItems = v.roundToInt()) }
            }

            Text(tr("文字颜色", "Text color"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PRESET_COLORS.forEach { color ->
                    val selected = s.textColor == color
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape,
                            )
                            .clickable { update { it.copy(textColor = color) } },
                    )
                }
            }
            SwitchRow(tr("显示用户名", "Show usernames"), s.showUserName, tr("视频弹幕本身不带用户名，此项只对直播生效", "Video danmaku have no usernames; this only affects live")) { v ->
                update { it.copy(showUserName = v) }
            }
        }

        SectionCard(tr("锁定模式", "Lock mode")) {
            HintText(
                tr("锁定后悬浮窗不接收触摸，操作会传到下层 App。", "When locked, the overlay ignores touches and they go to the app underneath. ") +
                    tr("Android 12 及以上系统要求穿透窗口不透明度不超过 80%，所以锁定时整体会略微变淡。", "Android 12+ requires pass-through windows to be at most 80% opaque, so the overlay looks slightly faded when locked."),
            )
            SwitchRow(tr("锁定时显示小解锁按钮", "Show a small unlock button when locked"), s.showUnlockButton, tr("关闭后只能从通知栏解锁", "If off, unlock only from the notification")) { v ->
                update { it.copy(showUnlockButton = v) }
            }
        }

        SectionCard(tr("弹幕内容", "Danmaku content")) {
            SwitchRow(
                tr("隐藏直播间表情", "Hide live-room emotes"),
                s.hideEmotes,
                tr("整条表情弹幕不显示；文字里的 [表情] 去掉，只留文字。Unicode emoji 不受影响。只对之后收到的弹幕生效。", "Emote-only danmaku are hidden; [emote] tags in text are removed, leaving the text. Unicode emoji are unaffected. Applies only to danmaku received afterwards."),
            ) { v -> update { it.copy(hideEmotes = v) } }
            SwitchRow(
                tr("视频弹幕显示时间点", "Show time in video danmaku"),
                s.showVideoTime,
                tr("在每条视频弹幕前显示它在视频中的时间（如 12:34），方便核对同步是否准确。", "Shows each video danmaku's time in the video (e.g. 12:34) before it, to check whether sync is accurate."),
            ) { v -> update { it.copy(showVideoTime = v) } }
        }

        SectionCard(tr("直播附加消息", "Extra live messages")) {
            HintText(tr("默认只显示普通弹幕。", "Only regular danmaku are shown by default."))
            SwitchRow(tr("醒目留言（SC）", "Super Chat (SC)"), s.showSuperChat) { v -> update { it.copy(showSuperChat = v) } }
            SwitchRow(tr("礼物", "Gifts"), s.showGift) { v -> update { it.copy(showGift = v) } }
            SwitchRow(tr("进场与互动（关注、分享等）", "Entries & interactions (follows, shares, etc.)"), s.showEnter) { v -> update { it.copy(showEnter = v) } }
        }

        SectionCard(tr("重置", "Reset")) {
            OutlinedButton(
                onClick = {
                    val d = OverlaySettings()
                    update {
                        it.copy(
                            x = d.x, y = d.y, widthDp = d.widthDp, heightDp = d.heightDp,
                            bubbleX = d.bubbleX, bubbleY = d.bubbleY, collapsed = false, locked = false,
                            landX = null, landY = null, landWidthDp = null, landHeightDp = null,
                            landBubbleX = null, landBubbleY = null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("重置位置和大小（找不到悬浮窗时用）", "Reset position and size (if you can't find the overlay)")) }
            OutlinedButton(
                onClick = {
                    val d = OverlaySettings()
                    update {
                        it.copy(
                            bgAlpha = d.bgAlpha, textSizeSp = d.textSizeSp, textColor = d.textColor,
                            lineSpacingDp = d.lineSpacingDp, showUserName = d.showUserName, maxItems = d.maxItems,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("恢复默认样式", "Restore default style")) }
        }
    }
}
