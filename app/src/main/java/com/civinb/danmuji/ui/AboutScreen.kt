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
import com.civinb.danmuji.util.tr

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
        SectionCard(tr("弹幕机", "Danmaku Viewer")) {
            Text(tr("当前版本：${updates.currentVersion}", "Current version: ${updates.currentVersion}"))
            Text(tr("项目地址：github.com/Civinb/DanmakuAndroid", "Project: github.com/Civinb/DanmakuAndroid"), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { openUrl(context, UpdateManager.REPO_URL) }) { Text(tr("打开 GitHub 仓库", "Open GitHub repository")) }
        }

        SectionCard(tr("检查更新", "Updates")) {
            SwitchRow(tr("打开应用时自动检查", "Check automatically when the app opens"), autoCheck) {
                autoCheck = it
                updates.autoCheck = it
            }
            when (val s = state) {
                UpdateState.Idle -> Unit
                UpdateState.Checking -> Text(tr("正在检查…", "Checking…"))
                is UpdateState.UpToDate -> Text(tr("已是最新版本（GitHub 最新发布：${s.latestTag}）", "Up to date (latest GitHub release: ${s.latestTag})"))
                is UpdateState.Available -> ReleaseBlock(s.release)
                is UpdateState.Downloading -> {
                    ReleaseBlock(s.release)
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(tr("正在下载 ${mb(s.bytes)} / ${mb(s.total)}", "Downloading ${mb(s.bytes)} / ${mb(s.total)}"))
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(tr("正在下载 ${mb(s.bytes)}", "Downloading ${mb(s.bytes)}"))
                    }
                }
                is UpdateState.Downloaded -> {
                    ReleaseBlock(s.release)
                    Text(tr("已下载：${s.file.name}（${mb(s.file.length())}）", "Downloaded: ${s.file.name} (${mb(s.file.length())})"))
                    val warnings = buildList {
                        if (s.check.samePackage == false) add(tr("这个安装包不是本应用（包名不同），不要安装。", "This package isn't this app (different package name). Don't install it."))
                        if (s.check.sameSignature == false) {
                            add(tr("安装包的签名和手机上当前版本不同，系统会拒绝覆盖安装。需要先卸载当前版本再安装（悬浮窗样式、过滤规则、登录会丢失）。", "The package's signature differs from the installed version, so the system will refuse to update. Uninstall the current version first (overlay style, filters and login will be lost)."))
                        }
                        if (s.check.newerVersionCode == false) {
                            add(tr("安装包的 versionCode 不高于当前版本，系统会拒绝安装（发布时需要把 versionCode 加 1）。", "The package's versionCode isn't higher than the current one, so the system will refuse to install it (increase versionCode by 1 when releasing)."))
                        }
                    }
                    warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                is UpdateState.Failed -> {
                    s.release?.let { ReleaseBlock(it) }
                    Text(tr("失败：${s.message}", "Failed: ${s.message}"), color = MaterialTheme.colorScheme.error)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    is UpdateState.Available -> Button(onClick = { updates.download() }, enabled = s.release.apk != null) {
                        Text(tr("下载并安装", "Download & install"))
                    }
                    is UpdateState.Downloading -> OutlinedButton(onClick = { updates.cancelDownload() }) { Text(tr("取消下载", "Cancel download")) }
                    is UpdateState.Downloaded -> Button(onClick = { install(context, updates, s) }) { Text(tr("安装", "Install")) }
                    is UpdateState.Failed -> if (s.release?.apk != null) {
                        Button(onClick = { updates.download() }) { Text(tr("重新下载", "Download again")) }
                    }
                    else -> Unit
                }
                OutlinedButton(
                    onClick = { updates.checkNow() },
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                ) { Text(tr("检查更新", "Check for updates")) }
            }
            val release = when (val s = state) {
                is UpdateState.Available -> s.release
                is UpdateState.Downloading -> s.release
                is UpdateState.Downloaded -> s.release
                is UpdateState.Failed -> s.release
                else -> null
            }
            OutlinedButton(onClick = { openUrl(context, release?.pageUrl?.ifBlank { null } ?: UpdateManager.RELEASES_URL) }) {
                Text(tr("在浏览器打开发布页", "Open release page in browser"))
            }
            HintText(tr("第一次安装需要允许本应用“安装未知应用”。下载慢时可在浏览器打开发布页下载。", "The first install requires allowing this app to \"Install unknown apps\". If downloading is slow, open the release page in a browser."))
        }
    }
}

@Composable
private fun ReleaseBlock(release: ReleaseInfo) {
    Text(tr("新版本：${release.title}", "New version: ${release.title}"), style = MaterialTheme.typography.titleSmall)
    val date = release.publishedAt.take(10)
    val size = release.apk?.takeIf { it.size > 0 }?.let { " · ${mb(it.size)}" }.orEmpty()
    if (date.isNotEmpty() || size.isNotEmpty()) Text(tr("发布于 $date$size", "Released $date$size"), style = MaterialTheme.typography.bodySmall)
    if (release.apk == null) Text(tr("这个 Release 没有附带 .apk 文件", "This release has no .apk attached"), color = MaterialTheme.colorScheme.error)
    if (release.notes.isNotBlank()) Text(release.notes.trim().take(1500), style = MaterialTheme.typography.bodySmall)
}

private fun install(context: Context, updates: UpdateManager, s: UpdateState.Downloaded) {
    when (updates.install(context, s.file)) {
        UpdateManager.InstallResult.STARTED -> Unit
        UpdateManager.InstallResult.NEED_PERMISSION ->
            Toast.makeText(context, tr("请允许“安装未知应用”，返回后再点一次“安装”", "Please allow \"Install unknown apps\", then come back and tap \"Install\" again"), Toast.LENGTH_LONG).show()
        UpdateManager.InstallResult.FAILED ->
            Toast.makeText(context, tr("无法打开系统安装器，请在浏览器打开发布页手动下载", "Can't open the system installer; open the release page in a browser and download manually"), Toast.LENGTH_LONG).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, tr("没有可以打开链接的浏览器", "No browser available to open the link"), Toast.LENGTH_SHORT).show()
    }
}

private fun mb(bytes: Long): String = "%.1f MB".format(bytes / 1024.0 / 1024.0)
