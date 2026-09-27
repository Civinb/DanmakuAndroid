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
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.civinb.danmuji.DanmuApp
import com.civinb.danmuji.filter.EmoteFilter
import com.civinb.danmuji.R
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.overlay.DanmakuBuffer
import com.civinb.danmuji.overlay.FloatingWindowController
import com.civinb.danmuji.probe.ProbeRecorder
import com.civinb.danmuji.settings.OverlaySettings
import com.civinb.danmuji.settings.SettingsRepository
import com.civinb.danmuji.source.DanmakuSource
import com.civinb.danmuji.source.FakeDanmakuSource
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
import kotlinx.coroutines.flow.collectLatest
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
    private var controller: FloatingWindowController? = null
    private val buffer = DanmakuBuffer()
    private var sourceJob: Job? = null
    private var settingsJob: Job? = null
    private var flushJob: Job? = null
    private var probeJob: Job? = null

    @Volatile
    private var currentSettings = OverlaySettings()
    private var statusText = "准备中"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as DanmuApp).settingsRepository
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
                ensureOverlay()
                startSource(createSource(intent))
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
        })
        controller = c
        _running.value = true

        settingsJob = scope.launch {
            repository.settings.collect { s ->
                currentSettings = s
                c.applySettings(s)
                updateNotification()
            }
        }
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                val batch = buffer.drain()
                if (batch.isNotEmpty()) c.appendBatch(batch)
            }
        }
        // 媒体会话探针：打开后在标题栏实时显示，并在后台持续记录事件
        probeJob = scope.launch {
            ProbeRecorder.showInOverlay.collectLatest { on ->
                if (!on) {
                    c.setTitle(statusText)
                    return@collectLatest
                }
                while (true) {
                    withContext(Dispatchers.Default) { ProbeRecorder.poll(this@OverlayService) }
                    c.setTitle(ProbeRecorder.overlaySummary())
                    delay(500)
                }
            }
        }
    }

    // ---------------- 弹幕来源 ----------------

    private fun createSource(intent: Intent?): DanmakuSource {
        val input = intent?.getStringExtra(EXTRA_INPUT)
        if (!input.isNullOrBlank()) {
            val app = application as DanmuApp
            return InputDanmakuSource(input, app.bili, app.network)
        }
        val rate = intent?.getIntExtra(EXTRA_RATE, 5) ?: 5
        return FakeDanmakuSource(rate)
    }

    private fun startSource(source: DanmakuSource) {
        sourceJob?.cancel()
        buffer.clear()
        controller?.clearItems()
        sourceJob = scope.launch(Dispatchers.Default) {
            try {
                source.events().collect { event ->
                    when (event) {
                        is SourceEvent.Item -> transform(event.item)?.let { buffer.offer(it) }
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
        if (!ProbeRecorder.showInOverlay.value) controller?.setTitle(text)
        updateNotification()
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
        probeJob?.cancel()
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
        const val EXTRA_RATE = "rate"
        const val EXTRA_INPUT = "input"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        /** 启动（或切换来源）。调用前应已确认有悬浮窗权限。 */
        fun startFake(context: Context, ratePerSecond: Int) {
            val intent = Intent(context, OverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RATE, ratePerSecond)
            ContextCompat.startForegroundService(context, intent)
        }

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
