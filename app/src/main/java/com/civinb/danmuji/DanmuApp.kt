package com.civinb.danmuji

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.civinb.danmuji.settings.SettingsRepository

class DanmuApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
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
