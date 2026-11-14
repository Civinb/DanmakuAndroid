package com.civinb.danmuji.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.civinb.danmuji.util.AppLanguage
import com.civinb.danmuji.util.LanguageManager
import com.civinb.danmuji.util.tr

/** 设置 → 语言：跟随系统 / 中文 / English */
@Composable
fun LanguageScreen() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LanguageManager.current(context)) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(tr("界面语言", "App language")) {
            listOf(
                AppLanguage.SYSTEM to tr("跟随系统", "Follow system"),
                AppLanguage.ZH to "中文",
                AppLanguage.EN to "English",
            ).forEach { (lang, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (selected == lang) return@clickable
                            selected = lang
                            // Android 13+ 由系统重建界面；12 及以下自己重建
                            if (LanguageManager.set(context, lang)) (context as? Activity)?.recreate()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == lang, onClick = null)
                    Text(label, Modifier.padding(start = 8.dp))
                }
            }
            HintText(
                tr("跟随系统时：系统语言是中文就显示中文，其他语言显示英文。", "Follow system: Chinese if the system language is Chinese, otherwise English.") +
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        tr(
                            "\n也可以在系统设置 → 应用 → 本应用 → 语言 里修改，两边是同一个设置。",
                            "\nYou can also change it in system Settings → Apps → this app → Language; it's the same setting.",
                        )
                    } else {
                        ""
                    } +
                    tr(
                        "\n正在显示的悬浮窗里已有的文字不会立即改变，新的提示和下次启动会使用新语言。",
                        "\nText already shown in a running overlay won't change immediately; new messages and the next start use the new language.",
                    ),
            )
        }
    }
}
