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
        HintText("修改后悬浮窗会立即生效（悬浮窗运行时可以边看边调）。")

        SectionCard("外观") {
            SettingSlider("背景不透明度", s.bgAlpha, 0f..1f, 19, { "${(it * 100).roundToInt()}%" }) { v ->
                update { it.copy(bgAlpha = v) }
            }
            SettingSlider("字号", s.textSizeSp, 10f..28f, 17, { "${it.roundToInt()} sp" }) { v ->
                update { it.copy(textSizeSp = v.roundToInt().toFloat()) }
            }
            SettingSlider("行间距", s.lineSpacingDp, 0f..16f, 15, { "${it.roundToInt()} dp" }) { v ->
                update { it.copy(lineSpacingDp = v.roundToInt().toFloat()) }
            }
            SettingSlider("最多保留条数", s.maxItems.toFloat(), 100f..1000f, 17, { "${it.roundToInt()} 条" }) { v ->
                update { it.copy(maxItems = v.roundToInt()) }
            }

            Text("文字颜色")
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
            SwitchRow("显示用户名", s.showUserName, "视频弹幕本身不带用户名，此项只对直播生效") { v ->
                update { it.copy(showUserName = v) }
            }
        }

        SectionCard("锁定模式") {
            HintText(
                "锁定后悬浮窗不接收触摸，操作会传到下层 App。" +
                    "Android 12 及以上系统要求穿透窗口不透明度不超过 80%，所以锁定时整体会略微变淡。",
            )
            SwitchRow("锁定时显示小解锁按钮", s.showUnlockButton, "关闭后只能从通知栏解锁") { v ->
                update { it.copy(showUnlockButton = v) }
            }
        }

        SectionCard("直播附加消息") {
            HintText("默认只显示普通弹幕。")
            SwitchRow("醒目留言（SC）", s.showSuperChat) { v -> update { it.copy(showSuperChat = v) } }
            SwitchRow("礼物", s.showGift) { v -> update { it.copy(showGift = v) } }
            SwitchRow("进场消息", s.showEnter) { v -> update { it.copy(showEnter = v) } }
        }

        SectionCard("重置") {
            OutlinedButton(
                onClick = {
                    val d = OverlaySettings()
                    update {
                        it.copy(
                            x = d.x, y = d.y, widthDp = d.widthDp, heightDp = d.heightDp,
                            bubbleX = d.bubbleX, bubbleY = d.bubbleY, collapsed = false, locked = false,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("重置位置和大小（找不到悬浮窗时用）") }
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
            ) { Text("恢复默认样式") }
        }
    }
}
