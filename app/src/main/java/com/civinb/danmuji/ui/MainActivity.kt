package com.civinb.danmuji.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.civinb.danmuji.service.OverlayService
import com.civinb.danmuji.util.LastInput
import com.civinb.danmuji.util.Permissions
import com.civinb.danmuji.util.ShareInbox

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DanmujiTheme {
                AppRoot()
            }
        }
        // 旋转屏幕等重建时不要重复处理同一个分享
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** 从 B 站 App “分享”进来：有悬浮窗权限就直接启动并退回 B 站，否则填入首页输入框。 */
    private fun handleShare(intent: Intent?) {
        val i = intent ?: return
        if (i.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return
        LastInput.save(this, text)
        ShareInbox.text.value = text
        if (!Permissions.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限，再点“连接”", Toast.LENGTH_LONG).show()
            return
        }
        OverlayService.startInput(this, text)
        Toast.makeText(this, "弹幕机：正在连接…", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}

enum class Screen(val title: String) {
    HOME("弹幕机"),
    STYLE("悬浮窗样式"),
    GUIDE("权限与后台保活"),
    PROBE("媒体会话探针"),
    LOG("连接日志"),
}

@Composable
fun DanmujiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        TextButton(onClick = { screen = Screen.HOME }) { Text("返回") }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (screen) {
                Screen.HOME -> HomeScreen(onNavigate = { screen = it })
                Screen.STYLE -> StyleScreen()
                Screen.GUIDE -> GuideScreen()
                Screen.PROBE -> ProbeScreen()
                Screen.LOG -> LogScreen()
            }
        }
    }
}
