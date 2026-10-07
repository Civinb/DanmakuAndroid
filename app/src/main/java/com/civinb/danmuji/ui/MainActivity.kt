package com.civinb.danmuji.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.civinb.danmuji.DanmuApp
import kotlinx.coroutines.flow.Flow
import com.civinb.danmuji.data.bili.link.LinkParser
import com.civinb.danmuji.data.bili.link.LinkTarget
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
        if (LinkParser.parse(text) is LinkTarget.Video) {
            ShareInbox.video.value = text
        } else {
            LastInput.save(this, text)
            ShareInbox.text.value = text
        }
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
    FILTER("过滤规则"),
    ACCOUNT("B 站账号"),
    GUIDE("权限与后台保活"),
    LOG("连接日志"),
    ABOUT("关于与更新"),
}

@Composable
fun DanmujiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val updates = remember { (context.applicationContext as DanmuApp).updates }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    // 预见式返回：子页面跟随返回手势缩小、平移，露出下面的首页；松手完成返回，滑回去则取消
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backActive by remember { mutableStateOf(false) }
    PredictiveBackHandler(enabled = screen != Screen.HOME) { progress: Flow<BackEventCompat> ->
        backActive = true
        try {
            progress.collect { e ->
                backProgress = e.progress
                backEdge = e.swipeEdge
            }
            screen = Screen.HOME
        } finally {
            // 完成或取消（CancellationException）都要复位
            backActive = false
            backProgress = 0f
        }
    }

    // 打开应用时自动检查更新（每个进程一次）
    LaunchedEffect(Unit) { updates.autoCheckOnce() }
    val prompt by updates.prompt.collectAsStateWithLifecycle()
    prompt?.let { release ->
        AlertDialog(
            onDismissRequest = { updates.dismissPrompt() },
            title = { Text("发现新版本 ${release.tag}") },
            text = {
                Text(
                    (if (release.notes.isNotBlank()) release.notes.trim().take(400) + "\n\n" else "") +
                        "当前版本 ${updates.currentVersion}",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    updates.dismissPrompt()
                    screen = Screen.ABOUT
                }) { Text("查看") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { updates.skip(release) }) { Text("跳过此版本") }
                    TextButton(onClick = { updates.dismissPrompt() }) { Text("以后再说") }
                }
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (backActive && screen != Screen.HOME) {
            // 手势进行中：在下面预先显示首页
            ScreenScaffold(Screen.HOME, onNavigate = {})
        }
        ScreenScaffold(
            screen = screen,
            onNavigate = { screen = it },
            modifier = Modifier.graphicsLayer {
                val p = backProgress
                if (p > 0f) {
                    val scale = 1f - 0.1f * p
                    scaleX = scale
                    scaleY = scale
                    val direction = if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                    translationX = direction * size.width * 0.05f * p
                    shape = RoundedCornerShape((32f * p).dp)
                    clip = true
                    shadowElevation = 8f * p * density
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(screen: Screen, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        TextButton(onClick = { onNavigate(Screen.HOME) }) { Text("返回") }
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
                Screen.HOME -> HomeScreen(onNavigate = onNavigate)
                Screen.STYLE -> StyleScreen()
                Screen.FILTER -> FilterScreen()
                Screen.ACCOUNT -> AccountScreen()
                Screen.GUIDE -> GuideScreen()
                Screen.LOG -> LogScreen()
                Screen.ABOUT -> AboutScreen()
            }
        }
    }
}
