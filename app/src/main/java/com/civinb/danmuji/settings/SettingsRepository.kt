package com.civinb.danmuji.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.overlayDataStore: DataStore<Preferences> by preferencesDataStore(name = "overlay_settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.overlayDataStore

    val settings: Flow<OverlaySettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toOverlaySettings() }
        .distinctUntilChanged()

    suspend fun current(): OverlaySettings = settings.first()

    suspend fun update(transform: (OverlaySettings) -> OverlaySettings) {
        store.edit { prefs ->
            val next = transform(prefs.toOverlaySettings())
            next.writeTo(prefs)
        }
    }
}

private object Keys {
    val X = intPreferencesKey("x")
    val Y = intPreferencesKey("y")
    val WIDTH = intPreferencesKey("width_dp")
    val HEIGHT = intPreferencesKey("height_dp")
    val BUBBLE_X = intPreferencesKey("bubble_x")
    val BUBBLE_Y = intPreferencesKey("bubble_y")
    val BG_ALPHA = floatPreferencesKey("bg_alpha")
    val TEXT_SIZE = floatPreferencesKey("text_size_sp")
    val TEXT_COLOR = intPreferencesKey("text_color")
    val LINE_SPACING = floatPreferencesKey("line_spacing_dp")
    val SHOW_USER_NAME = booleanPreferencesKey("show_user_name")
    val MAX_ITEMS = intPreferencesKey("max_items")
    val LOCKED = booleanPreferencesKey("locked")
    val COLLAPSED = booleanPreferencesKey("collapsed")
    val SHOW_UNLOCK_BUTTON = booleanPreferencesKey("show_unlock_button")
    val SHOW_SC = booleanPreferencesKey("show_super_chat")
    val SHOW_GIFT = booleanPreferencesKey("show_gift")
    val SHOW_ENTER = booleanPreferencesKey("show_enter")
    val HIDE_EMOTES = booleanPreferencesKey("hide_emotes")
    val SHOW_VIDEO_TIME = booleanPreferencesKey("show_video_time")
}

private fun Preferences.toOverlaySettings(): OverlaySettings {
    val d = OverlaySettings()
    return OverlaySettings(
        x = this[Keys.X] ?: d.x,
        y = this[Keys.Y] ?: d.y,
        widthDp = this[Keys.WIDTH] ?: d.widthDp,
        heightDp = this[Keys.HEIGHT] ?: d.heightDp,
        bubbleX = this[Keys.BUBBLE_X] ?: d.bubbleX,
        bubbleY = this[Keys.BUBBLE_Y] ?: d.bubbleY,
        bgAlpha = this[Keys.BG_ALPHA] ?: d.bgAlpha,
        textSizeSp = this[Keys.TEXT_SIZE] ?: d.textSizeSp,
        textColor = this[Keys.TEXT_COLOR] ?: d.textColor,
        lineSpacingDp = this[Keys.LINE_SPACING] ?: d.lineSpacingDp,
        showUserName = this[Keys.SHOW_USER_NAME] ?: d.showUserName,
        maxItems = this[Keys.MAX_ITEMS] ?: d.maxItems,
        locked = this[Keys.LOCKED] ?: d.locked,
        collapsed = this[Keys.COLLAPSED] ?: d.collapsed,
        showUnlockButton = this[Keys.SHOW_UNLOCK_BUTTON] ?: d.showUnlockButton,
        showSuperChat = this[Keys.SHOW_SC] ?: d.showSuperChat,
        showGift = this[Keys.SHOW_GIFT] ?: d.showGift,
        showEnter = this[Keys.SHOW_ENTER] ?: d.showEnter,
        hideEmotes = this[Keys.HIDE_EMOTES] ?: d.hideEmotes,
        showVideoTime = this[Keys.SHOW_VIDEO_TIME] ?: d.showVideoTime,
    )
}

private fun OverlaySettings.writeTo(p: MutablePreferences) {
    p[Keys.X] = x
    p[Keys.Y] = y
    p[Keys.WIDTH] = widthDp
    p[Keys.HEIGHT] = heightDp
    p[Keys.BUBBLE_X] = bubbleX
    p[Keys.BUBBLE_Y] = bubbleY
    p[Keys.BG_ALPHA] = bgAlpha
    p[Keys.TEXT_SIZE] = textSizeSp
    p[Keys.TEXT_COLOR] = textColor
    p[Keys.LINE_SPACING] = lineSpacingDp
    p[Keys.SHOW_USER_NAME] = showUserName
    p[Keys.MAX_ITEMS] = maxItems
    p[Keys.LOCKED] = locked
    p[Keys.COLLAPSED] = collapsed
    p[Keys.SHOW_UNLOCK_BUTTON] = showUnlockButton
    p[Keys.SHOW_SC] = showSuperChat
    p[Keys.SHOW_GIFT] = showGift
    p[Keys.SHOW_ENTER] = showEnter
    p[Keys.HIDE_EMOTES] = hideEmotes
    p[Keys.SHOW_VIDEO_TIME] = showVideoTime
}
