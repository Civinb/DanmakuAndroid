package com.civinb.danmuji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.civinb.danmuji.util.Permissions
import com.civinb.danmuji.util.tr

@Composable
fun GuideScreen() {
    val context = LocalContext.current
    var overlay by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    var battery by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }
    var notification by remember { mutableStateOf(Permissions.notificationsGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlay = Permissions.canDrawOverlays(context)
        battery = Permissions.isIgnoringBatteryOptimizations(context)
        notification = Permissions.notificationsGranted(context)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(tr("通用设置（所有手机）", "General (all phones)")) {
            StatusRow(tr("悬浮窗权限", "Overlay permission"), overlay, tr("打开", "Open")) {
                Permissions.open(context, Permissions.overlaySettingsIntent(context))
            }
            StatusRow(tr("通知", "Notifications"), notification, tr("打开", "Open")) {
                Permissions.open(context, Permissions.appNotificationSettingsIntent(context))
            }
            StatusRow(tr("忽略电池优化", "Ignore battery optimization"), battery, if (battery) null else tr("申请", "Request")) {
                Permissions.open(context, Permissions.requestIgnoreBatteryOptimizationsIntent(context))
            }
            OutlinedButton(
                onClick = { Permissions.open(context, Permissions.appDetailsIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("打开本应用的“应用信息”页", "Open this app's \"App info\" page")) }
            HintText(tr("另外建议：在最近任务（多任务）界面里把本应用下拉/长按“锁定”，防止一键清理时被杀。", "Also recommended: in the recent apps screen, pull down / long-press this app and \"Lock\" it so one-tap cleanup won't kill it."))
        }

        SectionCard(tr("小米 MIUI / HyperOS", "Xiaomi MIUI / HyperOS")) {
            GuideText(
                tr("1. 应用信息 → 权限 → “显示悬浮窗”设为允许；如有“后台弹出界面”也一并允许。\n", "1. App info → Permissions → set \"Display pop-up windows\" (overlay) to allowed; also allow \"Display pop-up windows while running in the background\" if present.\n") +
                    tr("2. 应用信息 → 省电策略（或“电量”）→ 选“无限制”。\n", "2. App info → Battery saver → \"No restrictions\".\n") +
                    tr("3. 应用信息 → 打开“自启动”。\n", "3. App info → turn on \"Autostart\".\n") +
                    tr("4. 多任务界面锁定本应用。", "4. Lock this app in the recent apps screen."),
            )
        }

        SectionCard(tr("OPPO / 一加 / realme（ColorOS）", "OPPO / OnePlus / realme (ColorOS)")) {
            GuideText(
                tr("1. 应用信息 → 耗电管理（或“电池”）→ 允许后台运行 / 允许完全后台行为。\n", "1. App info → Battery usage → allow background activity / allow full background behavior.\n") +
                    tr("2. 应用信息 → 打开“自启动”（部分版本在“手机管家 → 权限隐私 → 自启动管理”）。\n", "2. App info → turn on \"Auto launch\" (on some versions under \"Phone Manager → Privacy permissions → Startup manager\").\n") +
                    tr("3. 悬浮窗权限可能在“权限 → 悬浮窗”或“特殊权限”里。", "3. The overlay permission may be under \"Permissions → Floating windows\" or \"Special permissions\"."),
            )
        }

        SectionCard(tr("华为 / 荣耀（HarmonyOS / MagicOS）", "Huawei / Honor (HarmonyOS / MagicOS)")) {
            GuideText(
                tr("1. 设置 → 应用和服务 → 应用启动管理 → 找到本应用，关闭“自动管理”，", "1. Settings → Apps & services → App launch → find this app, turn off \"Manage automatically\", ") +
                    tr("打开“允许自启动 / 允许关联启动 / 允许后台活动”。\n", "and turn on \"Auto-launch / Secondary launch / Run in background\".\n") +
                    tr("2. 看直播时不要开启系统“省电模式”。\n", "2. Don't turn on the system \"Power saving mode\" while watching live streams.\n") +
                    tr("3. 悬浮窗：应用信息 → 权限 → 悬浮窗。", "3. Overlay: App info → Permissions → Floating windows."),
            )
        }

        SectionCard(tr("vivo / iQOO（OriginOS）", "vivo / iQOO (OriginOS)")) {
            GuideText(
                tr("1. i管家 → 应用管理 → 权限管理 → 本应用 → 悬浮窗、自启动。\n", "1. i Manager → App manager → Permission manager → this app → Floating windows, Autostart.\n") +
                    tr("2. 设置 → 电池 → 后台耗电管理 → 本应用 → 允许后台高耗电。", "2. Settings → Battery → Background power consumption management → this app → allow high background power consumption."),
            )
        }

        HintText(tr("菜单名称因系统版本不同可能略有差异。", "Menu names may differ slightly between system versions."))
    }
}

@Composable
private fun GuideText(text: String) {
    Text(text)
}
