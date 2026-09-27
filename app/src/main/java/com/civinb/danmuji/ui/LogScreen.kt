package com.civinb.danmuji.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.civinb.danmuji.util.DebugLog

@Composable
fun LogScreen() {
    val context = LocalContext.current
    val lines by DebugLog.lines.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HintText("记录连接过程（不含 Cookie 等敏感信息）。连不上或经常断线时，点“复制”发给开发者。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("danmuji log", lines.joinToString("\n")))
                Toast.makeText(context, "已复制 ${lines.size} 行", Toast.LENGTH_SHORT).show()
            }) { Text("复制") }
            OutlinedButton(onClick = { DebugLog.clear() }) { Text("清空") }
        }
        if (lines.isEmpty()) {
            HintText("暂无日志")
        } else {
            // 最新的在上面
            lines.asReversed().forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
