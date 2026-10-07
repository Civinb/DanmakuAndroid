package com.civinb.danmuji.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.civinb.danmuji.data.bili.await
import com.civinb.danmuji.util.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 下载好的安装包与手机上当前版本的对比结果；null 表示无法判断 */
data class ApkCheck(
    val samePackage: Boolean?,
    val newerVersionCode: Boolean?,
    val sameSignature: Boolean?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latestTag: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val bytes: Long, val total: Long) : UpdateState
    data class Downloaded(val release: ReleaseInfo, val file: File, val check: ApkCheck) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo?) : UpdateState
}

/**
 * 从 GitHub Releases（Civinb/DanmakuAndroid）检查更新、下载 APK、交给系统安装器。
 * - 不需要 GitHub 账号；未登录的 GitHub API 每个 IP 每小时限 60 次。
 * - 只能“下载后由用户在系统安装界面确认安装”，普通应用无法静默安装。
 * - 系统只接受包名相同、签名相同、versionCode 更高的安装包覆盖更新。
 * 不使用 BiliHttp 的客户端，避免把 B 站 Cookie / UA 发给 GitHub。
 */
class UpdateManager(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val dir = File(app.cacheDir, "update")
    private val autoChecked = AtomicBoolean(false)
    private var downloadJob: Job? = null

    val currentVersion: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** 自动检查发现的新版本（主界面弹窗用）；用户处理后清空 */
    private val _prompt = MutableStateFlow<ReleaseInfo?>(null)
    val prompt: StateFlow<ReleaseInfo?> = _prompt

    init {
        // 上次进程留下的安装包（已安装或已放弃）直接清掉
        scope.launch { dir.deleteRecursively() }
    }

    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    /** 打开应用时调用；每个进程只检查一次，旋转屏幕等不会重复 */
    fun autoCheckOnce() {
        if (!autoCheck || !autoChecked.compareAndSet(false, true)) return
        scope.launch {
            val result = runCheck()
            if (result is UpdateState.Available && result.release.tag != prefs.getString(KEY_SKIPPED, null)) {
                _prompt.value = result.release
            }
        }
    }

    /** 手动检查（关于页按钮），会忽略“跳过此版本” */
    fun checkNow() {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        scope.launch { runCheck() }
    }

    fun dismissPrompt() {
        _prompt.value = null
    }

    fun skip(release: ReleaseInfo) {
        prefs.edit().putString(KEY_SKIPPED, release.tag).apply()
        _prompt.value = null
    }

    private suspend fun runCheck(): UpdateState {
        _state.value = UpdateState.Checking
        val result = try {
            val release = fetchLatest()
            when (AppVersion.isNewer(release.tag, currentVersion)) {
                true -> UpdateState.Available(release)
                false -> UpdateState.UpToDate(release.tag)
                null -> UpdateState.Failed("无法识别版本号（标签“${release.tag}”，当前 $currentVersion），标签请用 v1.2.3 格式", release)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: e.javaClass.simpleName, null)
        }
        DebugLog.log(TAG, "检查更新：当前 $currentVersion → ${describe(result)}")
        _state.value = result
        return result
    }

    private suspend fun fetchLatest(): ReleaseInfo {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DanmakuAndroid/$currentVersion")
            .build()
        return client.newCall(request).await().use { resp ->
            when (resp.code) {
                404 -> throw IOException("仓库还没有发布正式版本（Release）")
                403, 429 -> throw IOException("GitHub 访问次数超限（未登录每小时 60 次），请稍后再试")
            }
            if (!resp.isSuccessful) throw IOException("GitHub 返回 HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("GitHub 返回空内容")
            ReleaseParser.parse(JSONObject(body))
        }
    }

    // ---------------- 下载 ----------------

    fun download() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release
            is UpdateState.Downloaded -> s.release
            else -> null
        } ?: return
        val apk = release.apk ?: run {
            _state.value = UpdateState.Failed("这个 Release 没有附带 .apk 文件", release)
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val target = File(dir, "DanmakuAndroid-${release.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
            val part = File(dir, target.name + ".part")
            try {
                dir.deleteRecursively()
                dir.mkdirs()
                _state.value = UpdateState.Downloading(release, 0, apk.size)
                val sha = MessageDigest.getInstance("SHA-256")
                val request = Request.Builder()
                    .url(apk.url)
                    .header("User-Agent", "DanmakuAndroid/$currentVersion")
                    .build()
                client.newCall(request).await().use { resp ->
                    if (!resp.isSuccessful) throw IOException("下载失败：HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("下载失败：空响应")
                    val total = body.contentLength().takeIf { it > 0 } ?: apk.size
                    body.byteStream().use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastEmit = 0L
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                sha.update(buf, 0, n)
                                done += n
                                if (done - lastEmit >= 256 * 1024) {
                                    lastEmit = done
                                    _state.value = UpdateState.Downloading(release, done, total)
                                }
                            }
                        }
                    }
                }
                if (apk.size > 0 && part.length() != apk.size) {
                    throw IOException("文件大小不符（${part.length()} / ${apk.size} 字节），请重试")
                }
                apk.sha256?.let { expected ->
                    val actual = sha.digest().joinToString("") { "%02x".format(it) }
                    if (actual != expected) throw IOException("SHA-256 校验失败，文件可能损坏，请重试")
                }
                if (!part.renameTo(target)) throw IOException("保存安装包失败")
                val check = inspect(target)
                DebugLog.log(TAG, "下载完成 ${target.name}（${target.length()} 字节）$check")
                _state.value = UpdateState.Downloaded(release, target, check)
            } catch (e: CancellationException) {
                part.delete()
                _state.value = UpdateState.Available(release)
                throw e
            } catch (e: Exception) {
                part.delete()
                DebugLog.log(TAG, "下载失败：${e.message}")
                _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName, release)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /**
     * 对比安装包与已安装版本。Android 9～10 上 getPackageArchiveInfo 的 GET_SIGNING_CERTIFICATES
     * 可能不填 signingInfo（见 AgentDeck PR #405），所以同时请求旧的 GET_SIGNATURES，哪个有值用哪个。
     */
    @Suppress("DEPRECATION")
    private fun inspect(file: File): ApkCheck {
        return try {
            val pm = app.packageManager
            var flags = PackageManager.GET_SIGNATURES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) flags = flags or PackageManager.GET_SIGNING_CERTIFICATES
            val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return ApkCheck(false, null, null)
            val installed = pm.getPackageInfo(app.packageName, flags)
            val a = signers(archive)
            val b = signers(installed)
            ApkCheck(
                samePackage = archive.packageName == app.packageName,
                newerVersionCode = PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed),
                sameSignature = if (a.isEmpty() || b.isEmpty()) null else a.any { it in b },
            )
        } catch (e: Exception) {
            DebugLog.log(TAG, "无法检查安装包：${e.message}")
            ApkCheck(null, null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = info.signingInfo
            if (si != null) {
                val certs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                if (!certs.isNullOrEmpty()) return certs.map { it.toCharsString() }.toSet()
            }
        }
        return info.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
    }

    // ---------------- 安装 ----------------

    enum class InstallResult { STARTED, NEED_PERMISSION, FAILED }

    /** 交给系统安装器；没有“安装未知应用”权限时先打开对应设置页 */
    fun install(context: Context, file: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            return try {
                context.startActivity(intent)
                InstallResult.NEED_PERMISSION
            } catch (e: Exception) {
                InstallResult.FAILED
            }
        }
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            InstallResult.STARTED
        } catch (e: Exception) {
            DebugLog.log(TAG, "打开安装器失败：${e.message}")
            InstallResult.FAILED
        }
    }

    private fun describe(s: UpdateState): String = when (s) {
        is UpdateState.Available -> "有新版本 ${s.release.tag}（APK：${s.release.apk?.name ?: "无"}）"
        is UpdateState.UpToDate -> "已是最新（最新 ${s.latestTag}）"
        is UpdateState.Failed -> "失败：${s.message}"
        else -> s.toString()
    }

    companion object {
        const val REPO_URL = "https://github.com/Civinb/DanmakuAndroid"
        const val RELEASES_URL = "$REPO_URL/releases"
        private const val LATEST_URL = "https://api.github.com/repos/Civinb/DanmakuAndroid/releases/latest"
        private const val TAG = "Update"
        private const val PREFS = "update"
        private const val KEY_AUTO = "auto_check"
        private const val KEY_SKIPPED = "skipped_tag"
    }
}
