package com.civinb.danmuji.ui

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.civinb.danmuji.DanmuApp
import com.civinb.danmuji.data.bili.auth.LoginApi
import com.civinb.danmuji.util.QrImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.civinb.danmuji.util.tr

@Composable
fun AccountScreen() {
    val context = LocalContext.current
    val login = remember { (context.applicationContext as DanmuApp).bili.login }
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf<LoginApi.Account?>(null) }
    var checking by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<LoginApi.QrCode?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrState by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun refreshAccount() {
        checking = true
        accountError = null
        scope.launch {
            try {
                account = login.account()
            } catch (e: Exception) {
                accountError = tr("查询失败：${e.message}", "Query failed: ${e.message}")
            } finally {
                checking = false
            }
        }
    }

    fun newQr() {
        busy = true
        qrState = tr("正在生成二维码…", "Generating QR code…")
        scope.launch {
            try {
                val code = login.generate()
                qr = code
                qrBitmap = QrImage.create(code.url)
                qrState = tr("请用 B 站 App 扫码", "Scan with the Bilibili app")
            } catch (e: Exception) {
                qrState = tr("生成失败：${e.message}", "Failed to generate: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshAccount() }

    // 每 2 秒轮询一次扫码状态
    LaunchedEffect(qr?.key) {
        val key = qr?.key ?: return@LaunchedEffect
        while (true) {
            delay(2_000)
            try {
                when (login.poll(key)) {
                    LoginApi.QrState.WAIT_SCAN -> qrState = tr("请用 B 站 App 扫码", "Scan with the Bilibili app")
                    LoginApi.QrState.WAIT_CONFIRM -> qrState = tr("已扫码，请在 B 站 App 上点“确认登录”", "Scanned. Tap \"Confirm login\" in the Bilibili app")
                    LoginApi.QrState.EXPIRED -> {
                        qrState = tr("二维码已过期，请刷新", "QR code expired, please refresh")
                        return@LaunchedEffect
                    }
                    LoginApi.QrState.SUCCESS -> {
                        qrState = tr("登录成功", "Logged in")
                        qr = null
                        qrBitmap = null
                        Toast.makeText(context, tr("登录成功。正在显示的直播/视频需重新连接后生效", "Logged in. Reconnect any live room/video currently shown for it to take effect"), Toast.LENGTH_LONG).show()
                        refreshAccount()
                        return@LaunchedEffect
                    }
                }
            } catch (e: Exception) {
                qrState = tr("查询扫码状态失败：${e.message}", "Failed to check scan status: ${e.message}")
            }
        }
    }

    val loggedInLocally = login.isLoggedIn()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(tr("账号状态", "Account status")) {
            val a = account
            when {
                checking -> Text(tr("查询中…", "Checking…"))
                accountError != null -> Text(accountError.orEmpty(), color = MaterialTheme.colorScheme.error)
                a != null && a.isLogin -> Text(tr("已登录：${a.uname}（UID ${a.mid}）", "Logged in: ${a.uname} (UID ${a.mid})"))
                loggedInLocally -> Text(tr("本地有登录凭证，但 B 站提示未登录（可能已过期），请退出后重新扫码", "Local credentials exist but Bilibili says not logged in (may have expired). Log out and scan again"), color = MaterialTheme.colorScheme.error)
                login.isExpired() -> Text(tr("登录已过期，请重新扫码登录", "Login expired, please scan the QR code again"), color = MaterialTheme.colorScheme.error)
                else -> Text(tr("未登录", "Not logged in"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { refreshAccount() }) { Text(tr("刷新", "Refresh")) }
                if (loggedInLocally) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            login.logout()
                            Toast.makeText(context, tr("已退出登录并删除本地凭证", "Logged out and local credentials deleted"), Toast.LENGTH_SHORT).show()
                            refreshAccount()
                        }
                    }) { Text(tr("退出登录", "Log out")) }
                }
            }
            HintText(
                tr("登录的目的：未登录时部分视频的弹幕接口只返回部分弹幕；直播弹幕昵称未登录会打码（登录后是否完整显示请以实测为准）。", "Why log in: without login, the danmaku API returns only part of the danmaku for some videos, and live usernames are masked (whether login fully unmasks them is to be confirmed in practice). ") +
                    tr("不会在本应用输入账号密码；登录凭证用系统密钥库（Android Keystore）加密后只保存在本机，", "Your password is never entered in this app; login credentials are encrypted with the system keystore (Android Keystore) and stored only on this device. ") +
                    tr("退出登录会通知 B 站注销该会话并删除本地凭证。", "Logging out tells Bilibili to end the session and deletes the local credentials."),
            )
        }

        if (!loggedInLocally) {
            SectionCard(tr("扫码登录", "QR login")) {
                Button(onClick = { newQr() }, enabled = !busy) { Text(if (qr == null) tr("生成二维码", "Generate QR code") else tr("刷新二维码", "Refresh QR code")) }
                qrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = tr("登录二维码", "Login QR code"),
                        modifier = Modifier
                            .size(240.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                    OutlinedButton(onClick = {
                        val ok = QrImage.saveToGallery(context, bmp)
                        Toast.makeText(
                            context,
                            if (ok) tr("已保存到相册（Pictures/Danmuji），登录后建议删除", "Saved to gallery (Pictures/Danmuji); consider deleting it after logging in") else tr("保存失败（Android 10 以下不支持），请用另一台设备扫码", "Save failed (not supported below Android 10); scan with another device"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { Text(tr("保存二维码到相册", "Save QR code to gallery")) }
                }
                if (qrState.isNotEmpty()) Text(qrState, color = MaterialTheme.colorScheme.primary)
                HintText(
                    tr("扫码要用 B 站 App，而二维码显示在这台手机上，可以：\n", "Scanning needs the Bilibili app, but the QR code is on this phone. You can:\n") +
                        tr("① 点“保存二维码到相册”，再在这台手机 B 站 App 的“扫一扫”里选择相册中的这张图片（B 站 App 是否支持从相册识别，我没有实测）；\n", "① Tap \"Save QR code to gallery\", then in this phone's Bilibili app use \"Scan\" and pick the image from the gallery (I haven't verified that the Bilibili app supports scanning from the gallery);\n") +
                        tr("② 或用另一部登录了同一账号的手机 / 平板上的 B 站 App，直接扫描本屏幕。\n", "② Or scan this screen with the Bilibili app on another phone / tablet logged in to the same account.\n") +
                        tr("二维码过一段时间会过期，过期后点“刷新二维码”。", "The QR code expires after a while; tap \"Refresh QR code\" when it does."),
                )
            }
        }
    }
}
