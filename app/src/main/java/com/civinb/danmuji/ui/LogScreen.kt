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
import com.civinb.danmuji.util.tr

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
        HintText(tr("不含 Cookie 等敏感信息。", "Contains no cookies or other sensitive data."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("danmuji log", lines.joinToString("\n")))
                Toast.makeText(context, tr("已复制 ${lines.size} 行", "Copied ${lines.size} lines"), Toast.LENGTH_SHORT).show()
            }) { Text(tr("复制", "Copy")) }
            OutlinedButton(onClick = { DebugLog.clear() }) { Text(tr("清空", "Clear")) }
        }
        if (lines.isEmpty()) {
            HintText(tr("暂无日志", "No logs yet"))
        } else {
            // 最新的在上面
            lines.asReversed().forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
