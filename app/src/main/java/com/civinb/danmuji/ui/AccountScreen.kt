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
                accountError = "查询失败：${e.message}"
            } finally {
                checking = false
            }
        }
    }

    fun newQr() {
        busy = true
        qrState = "正在生成二维码…"
        scope.launch {
            try {
                val code = login.generate()
                qr = code
                qrBitmap = QrImage.create(code.url)
                qrState = "请用 B 站 App 扫码"
            } catch (e: Exception) {
                qrState = "生成失败：${e.message}"
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
                    LoginApi.QrState.WAIT_SCAN -> qrState = "请用 B 站 App 扫码"
                    LoginApi.QrState.WAIT_CONFIRM -> qrState = "已扫码，请在 B 站 App 上点“确认登录”"
                    LoginApi.QrState.EXPIRED -> {
                        qrState = "二维码已过期，请刷新"
                        return@LaunchedEffect
                    }
                    LoginApi.QrState.SUCCESS -> {
                        qrState = "登录成功"
                        qr = null
                        qrBitmap = null
                        Toast.makeText(context, "登录成功。正在显示的直播/视频需重新连接后生效", Toast.LENGTH_LONG).show()
                        refreshAccount()
                        return@LaunchedEffect
                    }
                }
            } catch (e: Exception) {
                qrState = "查询扫码状态失败：${e.message}"
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
        SectionCard("账号状态") {
            val a = account
            when {
                checking -> Text("查询中…")
                accountError != null -> Text(accountError.orEmpty(), color = MaterialTheme.colorScheme.error)
                a != null && a.isLogin -> Text("已登录：${a.uname}（UID ${a.mid}）")
                loggedInLocally -> Text("本地有登录凭证，但 B 站提示未登录（可能已过期），请退出后重新扫码", color = MaterialTheme.colorScheme.error)
                else -> Text("未登录")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { refreshAccount() }) { Text("刷新") }
                if (loggedInLocally) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            login.logout()
                            Toast.makeText(context, "已退出登录并删除本地凭证", Toast.LENGTH_SHORT).show()
                            refreshAccount()
                        }
                    }) { Text("退出登录") }
                }
            }
            HintText(
                "登录的目的：未登录时部分视频的弹幕接口只返回部分弹幕；直播弹幕昵称未登录会打码（登录后是否完整显示请以实测为准）。" +
                    "不会在本应用输入账号密码；登录凭证用系统密钥库（Android Keystore）加密后只保存在本机，" +
                    "退出登录会通知 B 站注销该会话并删除本地凭证。",
            )
        }

        if (!loggedInLocally) {
            SectionCard("扫码登录") {
                Button(onClick = { newQr() }, enabled = !busy) { Text(if (qr == null) "生成二维码" else "刷新二维码") }
                qrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier
                            .size(240.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                    OutlinedButton(onClick = {
                        val ok = QrImage.saveToGallery(context, bmp)
                        Toast.makeText(
                            context,
                            if (ok) "已保存到相册（Pictures/Danmuji），登录后建议删除" else "保存失败（Android 10 以下不支持），请用另一台设备扫码",
                            Toast.LENGTH_LONG,
                        ).show()
                    }, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { Text("保存二维码到相册") }
                }
                if (qrState.isNotEmpty()) Text(qrState, color = MaterialTheme.colorScheme.primary)
                HintText(
                    "扫码要用 B 站 App，而二维码显示在这台手机上，可以：\n" +
                        "① 点“保存二维码到相册”，再在这台手机 B 站 App 的“扫一扫”里选择相册中的这张图片（B 站 App 是否支持从相册识别，我没有实测）；\n" +
                        "② 或用另一部登录了同一账号的手机 / 平板上的 B 站 App，直接扫描本屏幕。\n" +
                        "二维码过一段时间会过期，过期后点“刷新二维码”。",
                )
            }
        }
    }
}
