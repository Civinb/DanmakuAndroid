package com.civinb.danmuji.overlay

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind

/**
 * 弹幕列表适配器。只做追加和从头部裁剪，不做 DiffUtil（弹幕只增不改，DiffUtil 反而浪费）。
 */
class DanmakuAdapter : RecyclerView.Adapter<DanmakuAdapter.Holder>() {

    data class Style(
        val textSizeSp: Float = 14f,
        val textColor: Int = Color.WHITE,
        val lineSpacingPx: Int = 8,
        val showUserName: Boolean = true,
    )

    class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

    private val items = ArrayList<DanmakuItem>()
    private var style = Style()

    @SuppressLint("NotifyDataSetChanged")
    fun setStyle(newStyle: Style) {
        if (newStyle == style) return
        style = newStyle
        notifyDataSetChanged()
    }

    /**
     * 追加一批弹幕；超过 [limit] 条时从头部删除最旧的。
     */
    fun append(batch: List<DanmakuItem>, limit: Int) {
        if (batch.isEmpty()) return
        val start = items.size
        items.addAll(batch)
        notifyItemRangeInserted(start, batch.size)
        val overflow = items.size - limit.coerceAtLeast(1)
        if (overflow > 0) {
            items.subList(0, overflow).clear()
            notifyItemRangeRemoved(0, overflow)
        }
    }

    fun clear() {
        val n = items.size
        if (n > 0) {
            items.clear()
            notifyItemRangeRemoved(0, n)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            // 阴影让白字在亮色画面上也看得清
            setShadowLayer(3f, 1f, 1f, 0xCC000000.toInt())
        }
        return Holder(tv)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val tv = holder.text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
        tv.setTextColor(style.textColor)
        val half = style.lineSpacingPx / 2
        tv.setPadding(0, half, 0, style.lineSpacingPx - half)
        tv.text = format(item)
    }

    private fun format(item: DanmakuItem): CharSequence {
        val sb = SpannableStringBuilder()
        when (item.kind) {
            DanmakuKind.SUPER_CHAT -> sb.appendColored("【SC ¥${item.price}】", COLOR_SC)
            DanmakuKind.GIFT -> sb.appendColored("【礼物】", COLOR_GIFT)
            DanmakuKind.ENTER -> sb.appendColored("【进场】", COLOR_ENTER)
            DanmakuKind.SYSTEM -> sb.appendColored("【系统】", COLOR_SYSTEM)
            DanmakuKind.DANMAKU -> Unit
        }
        val name = item.userName
        if (style.showUserName && !name.isNullOrEmpty()) {
            sb.appendColored("$name：", COLOR_NAME)
        }
        when (item.kind) {
            DanmakuKind.ENTER -> sb.appendColored(item.text, COLOR_ENTER)
            DanmakuKind.SYSTEM -> sb.appendColored(item.text, COLOR_SYSTEM)
            else -> sb.append(item.text)
        }
        if (item.repeatCount > 1) {
            sb.appendColored("  ×${item.repeatCount}", COLOR_REPEAT)
        }
        return sb
    }

    private fun SpannableStringBuilder.appendColored(text: String, color: Int) {
        val start = length
        append(text)
        setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private companion object {
        val COLOR_NAME = 0xFF9FD3FF.toInt()
        val COLOR_SC = 0xFFFFD54F.toInt()
        val COLOR_GIFT = 0xFFFFB74D.toInt()
        val COLOR_ENTER = 0xFFB0BEC5.toInt()
        val COLOR_SYSTEM = 0xFF80CBC4.toInt()
        val COLOR_REPEAT = 0xFFFF8A80.toInt()
    }
}
