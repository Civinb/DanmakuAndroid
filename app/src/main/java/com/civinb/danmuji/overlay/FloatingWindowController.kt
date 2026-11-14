package com.civinb.danmuji.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.civinb.danmuji.R
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.settings.OverlaySettings
import com.civinb.danmuji.settings.SettingsRepository
import com.civinb.danmuji.settings.WindowGeometry
import com.civinb.danmuji.video.AutoStatus
import com.civinb.danmuji.video.MediaStatus
import com.civinb.danmuji.video.SyncMode
import com.civinb.danmuji.video.VideoUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import com.civinb.danmuji.util.tr

/**
 * 管理三个悬浮窗口：
 *  - 主面板（标题栏 + 弹幕列表 + 缩放手柄）
 *  - 折叠后的小圆球
 *  - 锁定时的小“解锁”按钮
 *
 * 只在主线程调用。
 */
class FloatingWindowController(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: SettingsRepository,
    private val listener: Listener,
) {

    interface Listener {
        fun onCloseRequested()
        fun onWindowStateChanged()

        /** 长按弹幕后点“屏蔽此用户” */
        fun onBlockUser(item: DanmakuItem)

        /** 长按弹幕后点“屏蔽这句” */
        fun onBlockText(item: DanmakuItem)

        // ---- 视频模式控制条 ----
        fun onVideoToggleMode()
        fun onVideoTogglePlay()
        fun onVideoNudge(deltaMs: Long)
        fun onVideoSeek(positionMs: Long)

        /** 黑屏控制条：通过 B 站媒体会话控制播放；返回 false 表示没找到会话 / 不支持 */
        fun onMediaTogglePlay(): Boolean
        fun onMediaSeekBy(deltaMs: Long): Boolean
        fun mediaStatus(): MediaStatus?
    }

    /**
     * Android 11+ 从 Service 里添加悬浮窗，官方推荐用 WindowContext（带显示器信息的上下文），
     * 否则会触发 StrictMode 的 IncorrectContextUseViolation，且获取屏幕尺寸可能不准。
     */
    private val windowContext: Context = createOverlayContext(context)
    private val themed = ContextThemeWrapper(windowContext, R.style.Theme_Danmuji)
    private val wm: WindowManager = windowContext.getSystemService(WindowManager::class.java)
    private val density = windowContext.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(themed).scaledTouchSlop

    private var settings = OverlaySettings()
    private var initialized = false

    /** 当前窗口位置属于横屏还是竖屏那一套 */
    private var landscape = false
    private var destroyed = false

    /** 用户正在拖动/缩放时为 true，此期间不接受来自设置流的位置覆盖。 */
    private var interacting = false
    private val attached = HashSet<View>()

    // ---------- 主面板 ----------
    @SuppressLint("InflateParams")
    private val panelView: View = LayoutInflater.from(themed).inflate(R.layout.overlay_panel, null)
    private val panel: View = panelView.findViewById(R.id.panel)
    private val titleBar: View = panelView.findViewById(R.id.title_bar)
    private val titleText: TextView = panelView.findViewById(R.id.title_text)
    private val btnBlackout: ImageButton = panelView.findViewById(R.id.btn_blackout)
    private val btnLock: ImageButton = panelView.findViewById(R.id.btn_lock)
    private val btnCollapse: ImageButton = panelView.findViewById(R.id.btn_collapse)
    private val btnClose: ImageButton = panelView.findViewById(R.id.btn_close)
    private val recycler: RecyclerView = panelView.findViewById(R.id.danmaku_list)
    private val btnJumpLatest: TextView = panelView.findViewById(R.id.btn_jump_latest)
    private val resizeHandle: View = panelView.findViewById(R.id.resize_handle)
    private val actionBar: View = panelView.findViewById(R.id.action_bar)
    private val actionTitle: TextView = panelView.findViewById(R.id.action_title)
    private val actionBlockUser: TextView = panelView.findViewById(R.id.action_block_user)
    private val actionBlockText: TextView = panelView.findViewById(R.id.action_block_text)
    private val actionCancel: TextView = panelView.findViewById(R.id.action_cancel)
    private var actionTarget: DanmakuItem? = null
    private val videoBar: View = panelView.findViewById(R.id.video_bar)
    private val videoMode: TextView = panelView.findViewById(R.id.video_mode)
    private val videoPlay: ImageButton = panelView.findViewById(R.id.video_play)
    private val videoTime: TextView = panelView.findViewById(R.id.video_time)
    private val videoSeek: SeekBar = panelView.findViewById(R.id.video_seek)
    private val videoHint: TextView = panelView.findViewById(R.id.video_hint)
    private var videoEnabled = false
    private var userSeeking = false
    private var lastVideoPlaying: Boolean? = null
    private val hideActionBar = Runnable { hideActions() }
    private val panelBackground = GradientDrawable().apply { cornerRadius = 8f * density }
    private val panelParams = newParams()

    // ---------- 折叠小球 ----------
    private val bubbleView: TextView = createBubble()
    private val bubbleParams = newParams()

    // ---------- 锁定时的解锁按钮 ----------
    private val unlockView: ImageView = createUnlockButton()
    private val unlockParams = newParams()

    // 黑屏背景（不持久化：每次启动悬浮窗都是关闭状态）
    private var blackout = false
    private val blackoutLayer = BlackoutLayer(
        themed,
        object : BlackoutLayer.Callbacks {
            override fun onExitBlackout() = setBlackout(false)
            override fun onTogglePlay(): Boolean = listener.onMediaTogglePlay()
            override fun onSeekBy(deltaMs: Long): Boolean = listener.onMediaSeekBy(deltaMs)
            override fun mediaStatus(): MediaStatus? = listener.mediaStatus()
        },
    )

    private val adapter = DanmakuAdapter()
    private var followLatest = true
    private var unseenCount = 0
    private var userDragging = false

    val isLocked: Boolean get() = settings.locked
    val isCollapsed: Boolean get() = settings.collapsed

    init {
        setupViews()
    }

    // ================= 对外接口 =================

    /** 设置变化（包括第一次）时调用。第一次调用会把窗口显示出来。 */
    fun applySettings(new: OverlaySettings) {
        val old = settings
        settings = new
        if (!initialized) {
            initialized = true
            landscape = isLandscape()
            bubbleParams.width = dp(BUBBLE_DP)
            bubbleParams.height = dp(BUBBLE_DP)
            unlockParams.width = dp(UNLOCK_DP)
            unlockParams.height = dp(UNLOCK_DP)
            loadGeometry(new.geometry(landscape))
        } else if (!interacting) {
            val o = old.geometry(landscape)
            val n = new.geometry(landscape)
            if (o.widthDp != n.widthDp || o.heightDp != n.heightDp) {
                panelParams.width = dp(n.widthDp)
                panelParams.height = dp(n.heightDp)
            }
            if (o.x != n.x || o.y != n.y) {
                panelParams.x = n.x
                panelParams.y = n.y
            }
            if (o.bubbleX != n.bubbleX || o.bubbleY != n.bubbleY) {
                bubbleParams.x = n.bubbleX
                bubbleParams.y = n.bubbleY
            }
        }
        clamp(panelParams)
        clamp(bubbleParams)
        applyStyle()
        refreshWindows()
    }

    fun appendBatch(batch: List<DanmakuItem>) {
        if (batch.isEmpty()) return
        // 用户正在往上翻看历史时，放宽上限，避免正在看的内容被裁掉
        val limit = if (followLatest) settings.maxItems else settings.maxItems * 3
        adapter.append(batch, limit)
        if (followLatest) {
            scrollToBottom()
        } else {
            unseenCount += batch.size
            updateJumpButton()
        }
    }

    /** 显示/隐藏视频控制条（切到直播时隐藏） */
    fun setVideoMode(enabled: Boolean) {
        videoEnabled = enabled
        lastVideoPlaying = null
        refreshVideoBar()
    }

    fun updateVideoState(s: VideoUiState) {
        if (!videoEnabled) setVideoMode(true)
        val manual = s.mode == SyncMode.MANUAL
        videoMode.text = if (manual) tr("手动", "Manual") else tr("自动", "Auto")
        videoPlay.visibility = if (manual) View.VISIBLE else View.GONE
        if (lastVideoPlaying != s.playing) {
            lastVideoPlaying = s.playing
            videoPlay.setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        }
        videoSeek.visibility = if (manual) View.VISIBLE else View.GONE
        if (!userSeeking) {
            val offset = if (!manual && s.offsetMs != 0L) {
                tr("  偏移", "  offset") + (if (s.offsetMs > 0) "+" else "−") + "%.1fs".format(kotlin.math.abs(s.offsetMs) / 1000.0)
            } else {
                ""
            }
            videoTime.text = "${fmtTime(s.positionMs)} / ${fmtTime(s.durationMs)}$offset"
            if (manual) {
                val maxSec = (s.durationMs / 1000).toInt().coerceAtLeast(1)
                if (videoSeek.max != maxSec) videoSeek.max = maxSec
                videoSeek.progress = (s.positionMs / 1000).toInt().coerceIn(0, maxSec)
            }
        }
        val hints = buildList {
            if (!manual) {
                when (s.autoStatus) {
                    AutoStatus.NO_PERMISSION -> add(tr("自动同步需要“通知使用权”（首页“必要权限”里开启），或点“自动”切到手动", "Auto sync needs \"Notification access\" (enable it under \"Required permissions\" on the home screen), or tap \"Auto\" to switch to manual"))
                    AutoStatus.NO_SESSION -> add(tr("未检测到 B 站正在播放，请在 B 站 App 里播放该视频", "No Bilibili playback detected. Play this video in the Bilibili app"))
                    else -> Unit
                }
                if (s.titleMismatch) add(tr("B站当前播放「${s.sessionTitle.orEmpty().take(24)}」，可能不是这个视频", "Bilibili is playing \"${s.sessionTitle.orEmpty().take(24)}\", which may not be this video"))
            }
            if (s.loadedSegments < s.totalSegments) add(tr("弹幕加载中 ${s.loadedSegments}/${s.totalSegments} 段", "Loading danmaku ${s.loadedSegments}/${s.totalSegments} segments"))
        }
        val hint = hints.joinToString("\n")
        if (videoHint.text.toString() != hint) videoHint.text = hint
        videoHint.visibility = if (hint.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun refreshVideoBar() {
        videoBar.visibility = if (videoEnabled && !settings.locked) View.VISIBLE else View.GONE
    }

    private fun fmtTime(ms: Long): String {
        if (ms <= 0 && ms != 0L) return "--:--"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** 应用一批更新：新增弹幕 + 已显示弹幕的 ×N 变化 */
    fun applyBatch(batch: DanmakuBuffer.Batch) {
        for ((id, count) in batch.repeats) adapter.updateRepeat(id, count)
        appendBatch(batch.items)
    }

    /** 删除已显示的、满足条件的弹幕（例如刚添加了屏蔽规则） */
    fun removeWhere(predicate: (DanmakuItem) -> Boolean) {
        if (adapter.removeWhere(predicate) > 0 && followLatest) scrollToBottom()
    }

    fun clearItems() {
        adapter.clear()
        unseenCount = 0
        setFollow(true)
    }

    fun setTitle(text: String) {
        titleText.text = text
    }

    fun setLocked(locked: Boolean) {
        if (settings.locked == locked) return
        settings = settings.copy(locked = locked)
        if (locked) setFollow(true)
        refreshWindows()
        persist { it.copy(locked = locked) }
        listener.onWindowStateChanged()
    }

    fun setCollapsed(collapsed: Boolean) {
        if (settings.collapsed == collapsed) return
        settings = settings.copy(collapsed = collapsed)
        refreshWindows()
        if (!collapsed && followLatest) scrollToBottom()
        persist { it.copy(collapsed = collapsed) }
        listener.onWindowStateChanged()
    }

    /** 屏幕旋转/尺寸变化后调用：横竖屏切换时换成该方向保存的位置和大小，再把窗口拉回可放置区域内。 */
    fun onScreenChanged() {
        val nowLandscape = isLandscape()
        if (initialized && nowLandscape != landscape) {
            landscape = nowLandscape
            loadGeometry(settings.geometry(nowLandscape))
        }
        clamp(panelParams)
        clamp(bubbleParams)
        refreshWindows()
    }

    val isBlackout: Boolean get() = blackout

    /**
     * 打开 / 关闭黑屏背景。黑色窗口必须在弹幕面板下面：同一应用的悬浮窗按添加顺序叠放，
     * 所以先撤下面板等窗口，加上黑色窗口，再把它们按当前状态加回去。
     */
    fun setBlackout(on: Boolean) {
        if (blackout == on || destroyed) return
        blackout = on
        if (on) {
            detach(unlockView)
            detach(bubbleView)
            detach(panelView)
            attachOrUpdate(blackoutLayer.view, blackoutLayer.params)
            blackoutLayer.onShown()
            refreshWindows()
        } else {
            blackoutLayer.onHidden()
            detach(blackoutLayer.view)
        }
        if (on) btnBlackout.setColorFilter(BLACKOUT_ON_TINT) else btnBlackout.clearColorFilter()
        listener.onWindowStateChanged()
    }

    fun destroy() {
        destroyed = true
        panelView.removeCallbacks(hideActionBar)
        blackoutLayer.onHidden()
        detach(blackoutLayer.view)
        detach(unlockView)
        detach(bubbleView)
        detach(panelView)
    }

    // ================= 初始化 =================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        panel.background = panelBackground

        recycler.layoutManager = LinearLayoutManager(themed).apply { stackFromEnd = true }
        recycler.adapter = adapter
        recycler.itemAnimator = null // 大量追加时关闭动画，保证流畅
        recycler.setHasFixedSize(true)
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userDragging = true
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    userDragging = false
                    // 用户自己滑回了底部 → 恢复自动滚动
                    if (!followLatest && !rv.canScrollVertically(1)) setFollow(true)
                }
            }

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                // 用户手指向上翻（dy < 0）→ 暂停自动滚动
                if (userDragging && dy < 0 && followLatest) setFollow(false)
            }
        })

        btnJumpLatest.setOnClickListener { setFollow(true) }
        adapter.onItemLongClick = { item -> showActions(item) }
        videoMode.setOnClickListener { listener.onVideoToggleMode() }
        videoPlay.setOnClickListener { listener.onVideoTogglePlay() }
        panelView.findViewById<View>(R.id.video_back5).setOnClickListener { listener.onVideoNudge(-5_000) }
        panelView.findViewById<View>(R.id.video_back1).setOnClickListener { listener.onVideoNudge(-1_000) }
        panelView.findViewById<View>(R.id.video_fwd1).setOnClickListener { listener.onVideoNudge(1_000) }
        panelView.findViewById<View>(R.id.video_fwd5).setOnClickListener { listener.onVideoNudge(5_000) }
        videoSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) videoTime.text = "${fmtTime(progress * 1000L)} / ${fmtTime(bar.max * 1000L)}"
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                userSeeking = false
                listener.onVideoSeek(bar.progress * 1000L)
            }
        })
        // 布局里的文字按应用内语言设置重新设置一遍（资源只跟随系统语言）
        actionBlockUser.text = tr("屏蔽此用户", "Block this user")
        actionBlockText.text = tr("屏蔽这句", "Block this text")
        actionCancel.text = tr("取消", "Cancel")
        videoMode.text = tr("自动", "Auto")
        btnBlackout.contentDescription = tr("黑屏背景", "Blackout background")
        btnLock.contentDescription = tr("锁定（触摸穿透）", "Lock (touch pass-through)")
        btnCollapse.contentDescription = tr("折叠", "Collapse")
        btnClose.contentDescription = tr("关闭", "Close")
        resizeHandle.contentDescription = tr("调整大小", "Resize")
        videoPlay.contentDescription = tr("播放/暂停", "Play/Pause")
        actionCancel.setOnClickListener { hideActions() }
        actionBlockUser.setOnClickListener {
            actionTarget?.let { listener.onBlockUser(it) }
            hideActions()
        }
        actionBlockText.setOnClickListener {
            actionTarget?.let { listener.onBlockText(it) }
            hideActions()
        }
        btnBlackout.setOnClickListener { setBlackout(!blackout) }
        btnLock.setOnClickListener { setLocked(true) }
        btnCollapse.setOnClickListener { setCollapsed(true) }
        btnClose.setOnClickListener { listener.onCloseRequested() }

        titleBar.setOnTouchListener(
            DragTouchListener(panelParams, panelView, onClick = null, onFinished = ::savePanelGeometry),
        )
        resizeHandle.setOnTouchListener(ResizeTouchListener())
        bubbleView.setOnTouchListener(
            DragTouchListener(bubbleParams, bubbleView, onClick = { setCollapsed(false) }, onFinished = ::saveBubblePosition),
        )
        unlockView.setOnClickListener { setLocked(false) }
    }

    private fun createBubble(): TextView = TextView(themed).apply {
        text = tr("弹", "D")
        gravity = Gravity.CENTER
        textSize = 16f
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xCC2F6FEB.toInt())
        }
    }

    private fun createUnlockButton(): ImageView = ImageView(themed).apply {
        setImageResource(R.drawable.ic_lock)
        val pad = (6 * density).roundToInt()
        setPadding(pad, pad, pad, pad)
        contentDescription = tr("解锁", "Unlock")
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x99000000.toInt())
        }
    }

    private fun newParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        BASE_FLAGS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 横屏全屏看视频时允许放到刘海一侧
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 坐标原点固定为屏幕左上角，系统栏的避让由 safeArea() 统一处理
            setFitInsetsTypes(0)
        }
    }

    // ================= 窗口状态 =================

    private fun refreshWindows() {
        if (settings.collapsed) {
            detach(unlockView)
            detach(panelView)
            attachOrUpdate(bubbleView, bubbleParams)
            return
        }
        detach(bubbleView)

        val locked = settings.locked
        panelParams.flags = if (locked) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS
        // Android 12+：触摸穿透的悬浮窗整体不透明度必须 ≤ 0.8，否则下层 App 收不到触摸
        panelParams.alpha =
            if (locked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MAX_PASSTHROUGH_ALPHA else 1f
        val chrome = if (locked) View.GONE else View.VISIBLE
        titleBar.visibility = chrome
        resizeHandle.visibility = chrome
        refreshVideoBar()
        if (locked) {
            btnJumpLatest.visibility = View.GONE
            hideActions()
        }

        attachOrUpdate(panelView, panelParams)

        if (locked && settings.showUnlockButton) {
            positionUnlockButton()
            attachOrUpdate(unlockView, unlockParams)
        } else {
            detach(unlockView)
        }
    }

    private fun positionUnlockButton() {
        unlockParams.x = panelParams.x + panelParams.width - unlockParams.width
        unlockParams.y = panelParams.y
        clamp(unlockParams)
    }

    private fun applyStyle() {
        val alpha = (settings.bgAlpha.coerceIn(0f, 1f) * 255).roundToInt()
        panelBackground.setColor(alpha shl 24) // 黑色 + 可调透明度
        adapter.setStyle(
            DanmakuAdapter.Style(
                textSizeSp = settings.textSizeSp,
                textColor = settings.textColor,
                lineSpacingPx = dp(settings.lineSpacingDp),
                showUserName = settings.showUserName,
                showVideoTime = settings.showVideoTime,
            ),
        )
    }

    // ---------------- 长按操作栏 ----------------

    private fun showActions(item: DanmakuItem) {
        if (settings.locked || item.kind == DanmakuKind.SYSTEM) return
        actionTarget = item
        val who = when {
            !item.userName.isNullOrEmpty() -> item.userName
            !item.userHash.isNullOrEmpty() -> tr("视频弹幕发送者", "Video danmaku sender")
            else -> null
        }
        val masked = item.userId == 0L && who != null && who.contains('*')
        actionTitle.text = buildString {
            append(if (who != null) tr("$who：", "$who: ") else "")
            append(item.text.take(60))
            if (masked) append(tr("\n（未登录时昵称被打码，屏蔽会同时屏蔽所有同样打码名字的用户）", "\n(When not logged in, usernames are masked; blocking will also block every user with the same masked name)"))
        }
        val canBlockUser = item.userId > 0 || !item.userName.isNullOrEmpty() || !item.userHash.isNullOrEmpty()
        actionBlockUser.visibility = if (canBlockUser) View.VISIBLE else View.GONE
        btnJumpLatest.visibility = View.GONE
        actionBar.visibility = View.VISIBLE
        panelView.removeCallbacks(hideActionBar)
        panelView.postDelayed(hideActionBar, ACTION_BAR_TIMEOUT_MS)
    }

    private fun hideActions() {
        panelView.removeCallbacks(hideActionBar)
        actionTarget = null
        actionBar.visibility = View.GONE
        if (!followLatest && !settings.locked) updateJumpButton()
    }

    private fun setFollow(follow: Boolean) {
        followLatest = follow
        if (follow) {
            unseenCount = 0
            btnJumpLatest.visibility = View.GONE
            scrollToBottom()
        } else {
            updateJumpButton()
        }
    }

    private fun updateJumpButton() {
        if (settings.locked || actionBar.visibility == View.VISIBLE) return
        btnJumpLatest.text = if (unseenCount > 0) tr("↓ 回到最新（$unseenCount）", "↓ Latest ($unseenCount)") else tr("↓ 回到最新", "↓ Latest")
        btnJumpLatest.visibility = View.VISIBLE
    }

    private fun scrollToBottom() {
        val n = adapter.itemCount
        if (n > 0) recycler.scrollToPosition(n - 1)
    }

    // ================= 持久化 =================

    private fun savePanelGeometry() {
        val land = landscape
        val x = panelParams.x
        val y = panelParams.y
        val w = (panelParams.width / density).roundToInt()
        val h = (panelParams.height / density).roundToInt()
        settings = settings.withPanel(land, x, y, w, h)
        persist { it.withPanel(land, x, y, w, h) }
    }

    private fun saveBubblePosition() {
        val land = landscape
        val x = bubbleParams.x
        val y = bubbleParams.y
        settings = settings.withBubble(land, x, y)
        persist { it.withBubble(land, x, y) }
    }

    /** 把某个方向保存的位置和大小放到窗口参数上（之后由调用方 clamp） */
    private fun loadGeometry(g: WindowGeometry) {
        panelParams.width = dp(g.widthDp)
        panelParams.height = dp(g.heightDp)
        panelParams.x = g.x
        panelParams.y = g.y
        bubbleParams.x = g.bubbleX
        bubbleParams.y = g.bubbleY
    }

    private fun isLandscape(): Boolean {
        val screen = screenSize()
        return screen.x > screen.y
    }

    private fun persist(transform: (OverlaySettings) -> OverlaySettings) {
        scope.launch { repository.update(transform) }
    }

    // ================= WindowManager 工具 =================

    private fun attachOrUpdate(view: View, params: WindowManager.LayoutParams) {
        if (destroyed) return
        try {
            if (view in attached) {
                wm.updateViewLayout(view, params)
            } else {
                wm.addView(view, params)
                attached.add(view)
            }
        } catch (e: RuntimeException) {
            // 没有悬浮窗权限时 addView 会抛 BadTokenException
            Log.w(TAG, "window add/update failed", e)
        }
    }

    private fun safeUpdate(view: View, params: WindowManager.LayoutParams) {
        if (view !in attached) return
        try {
            wm.updateViewLayout(view, params)
        } catch (e: RuntimeException) {
            Log.w(TAG, "updateViewLayout failed", e)
        }
    }

    private fun detach(view: View) {
        if (view !in attached) return
        try {
            wm.removeView(view)
        } catch (e: RuntimeException) {
            Log.w(TAG, "removeView failed", e)
        }
        attached.remove(view)
    }

    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val p = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
            p
        }
    }

    /**
     * 悬浮窗可以放置的区域（屏幕坐标）：扣掉顶部状态栏 / 刘海、底部导航栏 / 手势条，
     * 以及横屏时放在侧边的导航栏。这些区域的触摸会被系统拿走（下拉通知栏、回到桌面），
     * 窗口的标题栏或缩放手柄落在里面就拖不动了。
     * 状态栏暂时隐藏（如 B 站全屏播放）时也照样避让，避免窗口随系统栏显隐跳动。
     * 左右两侧的返回手势区不避让，否则窗口无法贴边。
     */
    private fun safeArea(): Rect {
        val screen = screenSize()
        var top = 0
        var bottom = 0
        var left = 0
        var right = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = wm.currentWindowMetrics.windowInsets
            val bars = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
            )
            val nav = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            val gestures = insets.getInsetsIgnoringVisibility(WindowInsets.Type.mandatorySystemGestures())
            top = maxOf(bars.top, gestures.top)
            bottom = maxOf(nav.bottom, gestures.bottom)
            left = nav.left
            right = nav.right
        }
        // 低版本，或个别系统没有通过 WindowMetrics 报告系统栏时，退回系统资源里的尺寸
        if (top == 0) top = systemDimen("status_bar_height")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && screen.y > screen.x) {
            bottom = systemDimen("navigation_bar_height")
        }
        val rect = Rect(left, top, screen.x - right, screen.y - bottom)
        // 防御：区域异常（例如系统报告的值不合理）时不做避让
        return if (rect.width() < dp(MIN_WIDTH_DP) || rect.height() < dp(MIN_HEIGHT_DP)) {
            Rect(0, 0, screen.x, screen.y)
        } else {
            rect
        }
    }

    private fun systemDimen(name: String): Int {
        val res = Resources.getSystem()
        @SuppressLint("DiscouragedApi")
        val id = res.getIdentifier(name, "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else 0
    }

    private fun clamp(p: WindowManager.LayoutParams) {
        val area = safeArea()
        if (p.width > area.width()) p.width = area.width()
        if (p.height > area.height()) p.height = area.height()
        val w = max(p.width, 0)
        val h = max(p.height, 0)
        p.x = p.x.coerceIn(area.left, max(area.left, area.right - w))
        p.y = p.y.coerceIn(area.top, max(area.top, area.bottom - h))
    }

    private fun dp(v: Float): Int = (v * density).roundToInt()
    private fun dp(v: Int): Int = dp(v.toFloat())

    // ================= 触摸处理 =================

    /** 拖动窗口；移动距离小于 touchSlop 视为点击。 */
    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val target: View,
        private val onClick: (() -> Unit)?,
        private val onFinished: () -> Unit,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    interacting = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) moved = true
                    if (moved) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        clamp(params)
                        safeUpdate(target, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    interacting = false
                    if (moved) {
                        onFinished()
                    } else {
                        v.performClick()
                        onClick?.invoke()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    interacting = false
                    if (moved) onFinished()
                }
                else -> return false
            }
            return true
        }
    }

    /** 右下角手柄：拖动改变宽高。 */
    private inner class ResizeTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startW = 0
        private var startH = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startW = panelParams.width
                    startH = panelParams.height
                    interacting = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val area = safeArea()
                    val minW = dp(MIN_WIDTH_DP)
                    val minH = dp(MIN_HEIGHT_DP)
                    // 只往右下扩展，不能超出可放置区域的右边 / 下边
                    val maxW = max(minW, area.right - panelParams.x)
                    val maxH = max(minH, area.bottom - panelParams.y)
                    panelParams.width = (startW + (event.rawX - downX).roundToInt()).coerceIn(minW, maxW)
                    panelParams.height = (startH + (event.rawY - downY).roundToInt()).coerceIn(minH, maxH)
                    clamp(panelParams)
                    safeUpdate(panelView, panelParams)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    interacting = false
                    savePanelGeometry()
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
                else -> return false
            }
            return true
        }
    }

    private companion object {
        fun createOverlayContext(base: Context): Context {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return base
            val display = base.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                ?: return base
            return base.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }

        const val TAG = "FloatingWindow"
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        const val MAX_PASSTHROUGH_ALPHA = 0.8f
        const val BUBBLE_DP = 44
        const val UNLOCK_DP = 30
        const val MIN_WIDTH_DP = 140
        const val MIN_HEIGHT_DP = 100
        const val ACTION_BAR_TIMEOUT_MS = 8_000L
        const val BLACKOUT_ON_TINT = 0xFF66B3FF.toInt()
    }
}
