package com.civinb.danmuji

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.SystemClock
import android.util.Log
import com.civinb.danmuji.data.bili.BiliClient
import com.civinb.danmuji.settings.FilterRepository
import com.civinb.danmuji.settings.SettingsRepository
import com.civinb.danmuji.update.UpdateManager
import com.civinb.danmuji.util.DebugLog
import com.civinb.danmuji.util.NetworkMonitor
import com.civinb.danmuji.video.MediaSessionReader
import com.civinb.danmuji.video.VideoSync

class DanmuApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var filterRepository: FilterRepository
        private set
    lateinit var bili: BiliClient
        private set
    lateinit var network: NetworkMonitor
        private set
    lateinit var videoSync: VideoSync
        private set
    lateinit var updates: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        DebugLog.sink = { tag, msg -> Log.d("Danmuji/$tag", msg) }
        settingsRepository = SettingsRepository(this)
        filterRepository = FilterRepository(this)
        bili = BiliClient(this)
        network = NetworkMonitor(this)
        val sessionReader = MediaSessionReader(this)
        videoSync = VideoSync(reader = { sessionReader.read() }, now = { SystemClock.elapsedRealtime() })
        updates = UpdateManager(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_OVERLAY,
            getString(R.string.channel_overlay),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "悬浮弹幕运行状态与快捷操作"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_OVERLAY = "overlay"
    }
}
