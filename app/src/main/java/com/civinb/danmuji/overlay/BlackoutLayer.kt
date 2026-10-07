package com.civinb.danmuji.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.civinb.danmuji.R
import com.civinb.danmuji.video.MediaStatus
import kotlin.math.roundToInt

/**
 * 黑屏背景：一个全屏纯黑的悬浮窗口，放在弹幕面板下面、B 站 App 上面。
 * 纯黑窗口必须拦截触摸（Android 12 起，能触摸穿透的悬浮窗不透明度最高 0.8），
 * 所以平时点不到 B 站；点一下黑色区域弹出控制条（播放/暂停、±10 秒、退出黑屏），几秒无操作自动隐藏。
 * 播放控制通过 B 站的媒体会话发送，见 [com.civinb.danmuji.video.MediaRemote]。
 */
class BlackoutLayer(
    private val context: Context,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onExitBlackout()

        /** 返回 false 表示没有找到 B 站的媒体会话 */
        fun onTogglePlay(): Boolean

        /** 返回 false 表示 B 站会话不支持跳转或没有会话 */
        fun onSeekBy(deltaMs: Long): Boolean

        fun mediaStatus(): MediaStatus?
    }

    private val density = context.resources.displayMetrics.density

    val view: FrameLayout = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }

    val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            // 黑屏时屏幕不要自动熄灭（还要看弹幕）
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 悬浮窗默认避开系统栏，这里尽量铺满整个屏幕
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private val info = TextView(context).apply {
        setTextColor(0xDDFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        maxLines = 2
    }
    private val btnBack = textButton("−10 秒") { seek(-SEEK_STEP_MS) }
    private val btnPlay = ImageButton(context).apply {
        setImageResource(R.drawable.ic_play)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)
        background = null
        contentDescription = context.getString(R.string.video_play)
        setOnClickListener { togglePlay() }
    }
    private val btnFwd = textButton("+10 秒") { seek(SEEK_STEP_MS) }
    private val btnExit = textButton("退出黑屏") { callbacks.onExitBlackout() }

    private val controls = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(0xE6202020.toInt())
        }
        // 控制条自己吃掉点击，避免点到控制条空白处时把它隐藏
        isClickable = true
        addView(info, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(btnBack)
            addView(btnPlay, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(btnFwd)
            addView(btnExit)
        }
        addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        visibility = View.GONE
    }

    private var messageUntil = 0L
    private val hideRunnable = Runnable { hideControls() }
    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            view.postDelayed(this, TICK_MS)
        }
    }

    init {
        view.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = dp(48) },
        )
        view.setOnClickListener { if (controls.visibility == View.VISIBLE) hideControls() else showControls() }
    }

    /** 刚进入黑屏时调用：先显示几秒控制条，让人知道怎么退出 */
    fun onShown() {
        showControls()
    }

    /** 窗口移除前调用 */
    fun onHidden() {
        view.removeCallbacks(hideRunnable)
        view.removeCallbacks(tickRunnable)
        controls.visibility = View.GONE
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        view.removeCallbacks(tickRunnable)
        tickRunnable.run()
        scheduleHide()
    }

    private fun hideControls() {
        controls.visibility = View.GONE
        view.removeCallbacks(hideRunnable)
        view.removeCallbacks(tickRunnable)
    }

    private fun scheduleHide() {
        view.removeCallbacks(hideRunnable)
        view.postDelayed(hideRunnable, AUTO_HIDE_MS)
    }

    private fun togglePlay() {
        scheduleHide()
        if (!callbacks.onTogglePlay()) showMessage("未检测到 B 站播放")
        // 给 B 站一点时间更新播放状态
        view.postDelayed({ refreshStatus() }, 300)
    }

    private fun seek(deltaMs: Long) {
        scheduleHide()
        if (!callbacks.onSeekBy(deltaMs)) showMessage("B 站当前播放不支持跳转")
        view.postDelayed({ refreshStatus() }, 300)
    }

    private fun showMessage(text: String) {
        info.text = text
        messageUntil = System.currentTimeMillis() + 2_000
    }

    private fun refreshStatus() {
        val s = callbacks.mediaStatus()
        btnPlay.visibility = if (s != null) View.VISIBLE else View.GONE
        val seekVisible = if (s?.canSeek == true) View.VISIBLE else View.GONE
        btnBack.visibility = seekVisible
        btnFwd.visibility = seekVisible
        if (s != null) btnPlay.setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (System.currentTimeMillis() < messageUntil) return
        info.text = if (s == null) {
            "未检测到 B 站播放（播放控制需要“通知使用权”）"
        } else {
            val time = if (s.durationMs > 0) "${fmt(s.positionMs)} / ${fmt(s.durationMs)}" else fmt(s.positionMs)
            listOfNotNull(s.title?.takeIf { it.isNotBlank() }, time).joinToString("\n")
        }
    }

    private fun textButton(text: String, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        val h = dp(12)
        val v = dp(10)
        setPadding(h, v, h, v)
        setOnClickListener { onClick() }
    }

    private fun fmt(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun dp(v: Int): Int = (v * density).roundToInt()

    private companion object {
        const val SEEK_STEP_MS = 10_000L
        const val AUTO_HIDE_MS = 4_000L
        const val TICK_MS = 500L
    }
}
