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
        SectionCard("通用设置（所有手机）") {
            StatusRow("悬浮窗权限", overlay, "打开") {
                Permissions.open(context, Permissions.overlaySettingsIntent(context))
            }
            StatusRow("通知", notification, "打开") {
                Permissions.open(context, Permissions.appNotificationSettingsIntent(context))
            }
            StatusRow("忽略电池优化", battery, if (battery) null else "申请") {
                Permissions.open(context, Permissions.requestIgnoreBatteryOptimizationsIntent(context))
            }
            OutlinedButton(
                onClick = { Permissions.open(context, Permissions.appDetailsIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("打开本应用的“应用信息”页") }
            HintText("另外建议：在最近任务（多任务）界面里把本应用下拉/长按“锁定”，防止一键清理时被杀。")
        }

        SectionCard("小米 MIUI / HyperOS") {
            GuideText(
                "1. 应用信息 → 权限 → “显示悬浮窗”设为允许；如有“后台弹出界面”也一并允许。\n" +
                    "2. 应用信息 → 省电策略（或“电量”）→ 选“无限制”。\n" +
                    "3. 应用信息 → 打开“自启动”。\n" +
                    "4. 多任务界面锁定本应用。",
            )
        }

        SectionCard("OPPO / 一加 / realme（ColorOS）") {
            GuideText(
                "1. 应用信息 → 耗电管理（或“电池”）→ 允许后台运行 / 允许完全后台行为。\n" +
                    "2. 应用信息 → 打开“自启动”（部分版本在“手机管家 → 权限隐私 → 自启动管理”）。\n" +
                    "3. 悬浮窗权限可能在“权限 → 悬浮窗”或“特殊权限”里。",
            )
        }

        SectionCard("华为 / 荣耀（HarmonyOS / MagicOS）") {
            GuideText(
                "1. 设置 → 应用和服务 → 应用启动管理 → 找到本应用，关闭“自动管理”，" +
                    "打开“允许自启动 / 允许关联启动 / 允许后台活动”。\n" +
                    "2. 看直播时不要开启系统“省电模式”。\n" +
                    "3. 悬浮窗：应用信息 → 权限 → 悬浮窗。",
            )
        }

        SectionCard("vivo / iQOO（OriginOS）") {
            GuideText(
                "1. i管家 → 应用管理 → 权限管理 → 本应用 → 悬浮窗、自启动。\n" +
                    "2. 设置 → 电池 → 后台耗电管理 → 本应用 → 允许后台高耗电。",
            )
        }

        HintText(
            "以上菜单名称会随系统版本变化，这里只写了大致位置，我没有逐个机型核实。" +
                "如果你的手机找不到对应项，告诉我机型和系统版本，我再调整说明。",
        )
    }
}

@Composable
private fun GuideText(text: String) {
    Text(text)
}
