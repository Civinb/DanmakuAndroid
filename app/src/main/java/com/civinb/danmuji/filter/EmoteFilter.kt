package com.civinb.danmuji.filter

import com.civinb.danmuji.model.DanmakuItem

/**
 * “隐藏直播间表情”：
 *  - 整条弹幕就是一个大表情 → 整条不显示；
 *  - 文字里夹着 B 站表情占位符（如 "[热]"）→ 去掉占位符，剩下的文字照常显示；去掉后为空则整条不显示。
 * 只处理 B 站下发的表情表里的占位符，用户自己打的方括号文字和 Unicode emoji 不受影响。
 */
object EmoteFilter {

    /** 返回处理后的弹幕；返回 null 表示整条不显示。 */
    fun apply(item: DanmakuItem): DanmakuItem? {
        if (item.emoteOnly) return null
        if (item.emoteTokens.isEmpty()) return item
        var text = item.text
        for (token in item.emoteTokens) {
            text = text.replace(token, "")
        }
        text = text.trim()
        return if (text.isEmpty()) null else item.copy(text = text, emoteTokens = emptyList())
    }
}
