package com.civinb.danmuji.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.civinb.danmuji.DanmuApp
import com.civinb.danmuji.update.ReleaseInfo
import com.civinb.danmuji.update.UpdateManager
import com.civinb.danmuji.update.UpdateState

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val updates = remember { (context.applicationContext as DanmuApp).updates }
    val state by updates.state.collectAsStateWithLifecycle()
    var autoCheck by remember { mutableStateOf(updates.autoCheck) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("弹幕机") {
            Text("当前版本：${updates.currentVersion}")
            Text("项目地址：github.com/Civinb/DanmakuAndroid", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { openUrl(context, UpdateManager.REPO_URL) }) { Text("打开 GitHub 仓库") }
        }

        SectionCard("检查更新") {
            SwitchRow("打开应用时自动检查", autoCheck, "有新版本才会弹出提示") {
                autoCheck = it
                updates.autoCheck = it
            }
            when (val s = state) {
                UpdateState.Idle -> Unit
                UpdateState.Checking -> Text("正在检查…")
                is UpdateState.UpToDate -> Text("已是最新版本（GitHub 最新发布：${s.latestTag}）")
                is UpdateState.Available -> ReleaseBlock(s.release)
                is UpdateState.Downloading -> {
                    ReleaseBlock(s.release)
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("正在下载 ${mb(s.bytes)} / ${mb(s.total)}")
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("正在下载 ${mb(s.bytes)}")
                    }
                }
                is UpdateState.Downloaded -> {
                    ReleaseBlock(s.release)
                    Text("已下载：${s.file.name}（${mb(s.file.length())}）")
                    val warnings = buildList {
                        if (s.check.samePackage == false) add("这个安装包不是本应用（包名不同），不要安装。")
                        if (s.check.sameSignature == false) {
                            add("安装包的签名和手机上当前版本不同，系统会拒绝覆盖安装。需要先卸载当前版本再安装（悬浮窗样式、过滤规则、登录会丢失）。")
                        }
                        if (s.check.newerVersionCode == false) {
                            add("安装包的 versionCode 不高于当前版本，系统会拒绝安装（发布时需要把 versionCode 加 1）。")
                        }
                    }
                    warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                is UpdateState.Failed -> {
                    s.release?.let { ReleaseBlock(it) }
                    Text("失败：${s.message}", color = MaterialTheme.colorScheme.error)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    is UpdateState.Available -> Button(onClick = { updates.download() }, enabled = s.release.apk != null) {
                        Text("下载并安装")
                    }
                    is UpdateState.Downloading -> OutlinedButton(onClick = { updates.cancelDownload() }) { Text("取消下载") }
                    is UpdateState.Downloaded -> Button(onClick = { install(context, updates, s) }) { Text("安装") }
                    is UpdateState.Failed -> if (s.release?.apk != null) {
                        Button(onClick = { updates.download() }) { Text("重新下载") }
                    }
                    else -> Unit
                }
                OutlinedButton(
                    onClick = { updates.checkNow() },
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                ) { Text("检查更新") }
            }
            val release = when (val s = state) {
                is UpdateState.Available -> s.release
                is UpdateState.Downloading -> s.release
                is UpdateState.Downloaded -> s.release
                is UpdateState.Failed -> s.release
                else -> null
            }
            OutlinedButton(onClick = { openUrl(context, release?.pageUrl?.ifBlank { null } ?: UpdateManager.RELEASES_URL) }) {
                Text("在浏览器打开发布页")
            }
            HintText(
                "更新包来自 GitHub 仓库的 Releases（只看正式版，不含预发布）。" +
                    "下载后由系统安装器安装，需要你点“安装”确认；第一次会要求允许本应用“安装未知应用”。\n" +
                    "只有签名相同、versionCode 更高的安装包才能覆盖安装。GitHub 访问慢或失败时，可以用上面的按钮在浏览器里手动下载。",
            )
        }
    }
}

@Composable
private fun ReleaseBlock(release: ReleaseInfo) {
    Text("新版本：${release.title}", style = MaterialTheme.typography.titleSmall)
    val date = release.publishedAt.take(10)
    val size = release.apk?.takeIf { it.size > 0 }?.let { " · ${mb(it.size)}" }.orEmpty()
    if (date.isNotEmpty() || size.isNotEmpty()) Text("发布于 $date$size", style = MaterialTheme.typography.bodySmall)
    if (release.apk == null) Text("这个 Release 没有附带 .apk 文件", color = MaterialTheme.colorScheme.error)
    if (release.notes.isNotBlank()) Text(release.notes.trim().take(1500), style = MaterialTheme.typography.bodySmall)
}

private fun install(context: Context, updates: UpdateManager, s: UpdateState.Downloaded) {
    when (updates.install(context, s.file)) {
        UpdateManager.InstallResult.STARTED -> Unit
        UpdateManager.InstallResult.NEED_PERMISSION ->
            Toast.makeText(context, "请允许“安装未知应用”，返回后再点一次“安装”", Toast.LENGTH_LONG).show()
        UpdateManager.InstallResult.FAILED ->
            Toast.makeText(context, "无法打开系统安装器，请在浏览器打开发布页手动下载", Toast.LENGTH_LONG).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "没有可以打开链接的浏览器", Toast.LENGTH_SHORT).show()
    }
}

private fun mb(bytes: Long): String = "%.1f MB".format(bytes / 1024.0 / 1024.0)
