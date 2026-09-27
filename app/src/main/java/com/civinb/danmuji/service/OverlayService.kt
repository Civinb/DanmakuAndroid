package com.civinb.danmuji.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.civinb.danmuji.DanmuApp
import com.civinb.danmuji.filter.DuplicateMerger
import com.civinb.danmuji.filter.EmoteFilter
import com.civinb.danmuji.filter.FilterEngine
import com.civinb.danmuji.filter.FilterSettings
import com.civinb.danmuji.filter.RuleAction
import com.civinb.danmuji.filter.RuleType
import com.civinb.danmuji.R
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.overlay.DanmakuBuffer
import com.civinb.danmuji.overlay.FloatingWindowController
import com.civinb.danmuji.settings.FilterRepository
import com.civinb.danmuji.settings.OverlaySettings
import com.civinb.danmuji.settings.SettingsRepository
import com.civinb.danmuji.source.DanmakuSource
import com.civinb.danmuji.source.InputDanmakuSource
import com.civinb.danmuji.source.SourceEvent
import com.civinb.danmuji.ui.MainActivity
import com.civinb.danmuji.util.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 前台服务：持有悬浮窗和弹幕来源，保证切到 B 站 App 后连接不断。
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> DebugLog.log("Service", "未捕获异常：$e") },
    )
    private lateinit var repository: SettingsRepository
    private lateinit var filterRepository: FilterRepository
    private var controller: FloatingWindowController? = null
    private val buffer = DanmakuBuffer()
    private var sourceJob: Job? = null
    private var settingsJob: Job? = null
    private var flushJob: Job? = null
    private var filterJob: Job? = null

    @Volatile
    private var currentSettings = OverlaySettings()

    @Volatile
    private var filterSettings = FilterSettings()

    @Volatile
    private var filterEngine = FilterEngine(FilterSettings())
    private var statusText = "准备中"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as DanmuApp).settingsRepository
        filterRepository = (application as DanmuApp).filterRepository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_LOCK, ACTION_TOGGLE_COLLAPSE -> {
                val c = controller
                if (c == null) {
                    // 服务已不在运行（例如进程被系统回收后点了残留通知），直接结束
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (intent?.action == ACTION_TOGGLE_LOCK) c.setLocked(!c.isLocked) else c.setCollapsed(!c.isCollapsed)
            }
            else -> {
                // ACTION_START：通过 startForegroundService 启动，必须尽快调用 startForeground
                startForegroundCompat()
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "没有悬浮窗权限，请先在首页授权", Toast.LENGTH_LONG).show()
                    stopEverything()
                    return START_NOT_STICKY
                }
                val source = createSource(intent)
                if (source == null) {
                    // 没有输入（不应出现）：已在显示就保持原样，否则直接结束
                    if (sourceJob == null) stopEverything()
                    return START_NOT_STICKY
                }
                ensureOverlay()
                startSource(source)
            }
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onScreenChanged()
    }

    override fun onDestroy() {
        _running.value = false
        scope.cancel()
        controller?.destroy()
        controller = null
        super.onDestroy()
    }

    // ---------------- 悬浮窗 ----------------

    private fun ensureOverlay() {
        if (controller != null) return
        val c = FloatingWindowController(this, scope, repository, object : FloatingWindowController.Listener {
            override fun onCloseRequested() = stopEverything()
            override fun onWindowStateChanged() = updateNotification()
            override fun onBlockUser(item: DanmakuItem) = blockUser(item)
            override fun onBlockText(item: DanmakuItem) = blockText(item)
            override fun onVideoToggleMode() = (application as DanmuApp).videoSync.toggleMode()
            override fun onVideoTogglePlay() = (application as DanmuApp).videoSync.togglePlay()
            override fun onVideoNudge(deltaMs: Long) = (application as DanmuApp).videoSync.nudge(deltaMs)
            override fun onVideoSeek(positionMs: Long) = (application as DanmuApp).videoSync.seek(positionMs)
        })
        controller = c
        c.setTitle(statusText)
        _running.value = true

        settingsJob = scope.launch {
            repository.settings.collect { s ->
                currentSettings = s
                c.applySettings(s)
                updateNotification()
            }
        }
        filterJob = scope.launch {
            filterRepository.settings.collect { fs ->
                val engine = FilterEngine(fs)
                filterSettings = fs
                filterEngine = engine
                if (engine.invalidRules.isNotEmpty()) {
                    DebugLog.log("Filter", "有 ${engine.invalidRules.size} 条正则无效，已忽略")
                }
                // 新增的屏蔽规则对已显示的弹幕也生效
                c.removeWhere { engine.isBlocked(it) }
            }
        }
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                val batch = buffer.drainBatch()
                if (!batch.isEmpty()) c.applyBatch(batch)
            }
        }
    }

    // ---------------- 弹幕来源 ----------------

    private fun createSource(intent: Intent?): DanmakuSource? {
        val input = intent?.getStringExtra(EXTRA_INPUT)
        if (!input.isNullOrBlank()) {
            val app = application as DanmuApp
            return InputDanmakuSource(input, app.bili, app.network, app.videoSync)
        }
        return null
    }

    private fun startSource(source: DanmakuSource) {
        sourceJob?.cancel()
        buffer.clear()
        controller?.clearItems()
        _filteredCount.value = 0
        controller?.setVideoMode(false)
        sourceJob = scope.launch(Dispatchers.Default) {
            val merger = DuplicateMerger()
            try {
                source.events().collect { event ->
                    when (event) {
                        is SourceEvent.Item -> handleItem(event.item, merger)
                        SourceEvent.Clear -> {
                            buffer.clear()
                            merger.clear()
                            withContext(Dispatchers.Main) { controller?.clearItems() }
                        }
                        is SourceEvent.VideoState -> withContext(Dispatchers.Main) {
                            controller?.updateVideoState(event.state)
                        }
                        is SourceEvent.Status -> withContext(Dispatchers.Main) { showStatus(event.text) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 来源内部未处理的异常不能让整个应用崩溃
                DebugLog.log("Service", "弹幕来源异常：$e")
                withContext(Dispatchers.Main) { showStatus("出错：${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    private fun showStatus(text: String) {
        statusText = text
        controller?.setTitle(text)
        updateNotification()
    }

    /** 在弹幕来源的收集协程（后台线程）里调用：开关 → 表情 → 过滤规则 → 合并重复 → 缓冲区 */
    private fun handleItem(item: DanmakuItem, merger: DuplicateMerger) {
        val shown = transform(item) ?: return
        if (!filterEngine.shows(shown)) {
            _filteredCount.update { it + 1 }
            return
        }
        val fs = filterSettings
        if (fs.mergeDuplicates) {
            when (val r = merger.offer(shown, SystemClock.elapsedRealtime(), fs.mergeWindowSec * 1000L)) {
                is DuplicateMerger.Result.New -> buffer.offer(r.item)
                is DuplicateMerger.Result.Repeat -> buffer.updateRepeat(r.targetId, r.count)
            }
        } else {
            buffer.offer(shown)
        }
    }

    private fun blockUser(item: DanmakuItem) {
        scope.launch {
            val (pattern, note) = when {
                item.userId > 0 -> item.userId.toString() to item.userName.orEmpty()
                !item.userName.isNullOrEmpty() -> item.userName.orEmpty() to ""
                !item.userHash.isNullOrEmpty() -> item.userHash.orEmpty() to "视频弹幕发送者"
                else -> return@launch
            }
            filterRepository.addRule(RuleType.USER, RuleAction.BLOCK, pattern, note)
            Toast.makeText(this@OverlayService, "已屏蔽用户：${item.userName ?: note}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun blockText(item: DanmakuItem) {
        scope.launch {
            filterRepository.addRule(RuleType.KEYWORD, RuleAction.BLOCK, item.text)
            Toast.makeText(this@OverlayService, "已屏蔽：${item.text.take(20)}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 按设置决定是否显示、以及显示成什么样；返回 null 表示不显示。 */
    private fun transform(item: DanmakuItem): DanmakuItem? {
        if (!shouldShow(item)) return null
        if (currentSettings.hideEmotes && item.kind == DanmakuKind.DANMAKU) return EmoteFilter.apply(item)
        return item
    }

    private fun shouldShow(item: DanmakuItem): Boolean {
        val s = currentSettings
        return when (item.kind) {
            DanmakuKind.SUPER_CHAT -> s.showSuperChat
            DanmakuKind.GIFT -> s.showGift
            DanmakuKind.ENTER -> s.showEnter
            DanmakuKind.DANMAKU, DanmakuKind.SYSTEM -> true
        }
    }

    // ---------------- 通知 ----------------

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val c = controller
        val locked = c?.isLocked == true
        val collapsed = c?.isCollapsed == true
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, DanmuApp.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_danmaku)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(0, if (locked) "解锁" else "锁定", servicePendingIntent(ACTION_TOGGLE_LOCK, 1))
            .addAction(0, if (collapsed) "展开" else "折叠", servicePendingIntent(ACTION_TOGGLE_COLLAPSE, 2))
            .addAction(0, "停止", servicePendingIntent(ACTION_STOP, 3))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stopEverything() {
        sourceJob?.cancel()
        settingsJob?.cancel()
        flushJob?.cancel()
        filterJob?.cancel()
        controller?.destroy()
        controller = null
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val FLUSH_INTERVAL_MS = 150L

        const val ACTION_START = "com.civinb.danmuji.action.START"
        const val ACTION_STOP = "com.civinb.danmuji.action.STOP"
        const val ACTION_TOGGLE_LOCK = "com.civinb.danmuji.action.TOGGLE_LOCK"
        const val ACTION_TOGGLE_COLLAPSE = "com.civinb.danmuji.action.TOGGLE_COLLAPSE"
        const val EXTRA_INPUT = "input"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        /** 本次连接中被过滤规则挡掉的弹幕数（过滤页面显示） */
        private val _filteredCount = MutableStateFlow(0)
        val filteredCount: StateFlow<Int> = _filteredCount

        /** 按用户输入（房间号 / 链接 / 分享文字）启动或切换来源。 */
        fun startInput(context: Context, input: String) {
            val intent = Intent(context, OverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_INPUT, input)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
