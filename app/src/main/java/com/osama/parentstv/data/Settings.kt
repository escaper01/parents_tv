package com.osama.parentstv.data

import android.content.Context
import org.json.JSONArray

/** Small persistent values: last watched channel, favorites and the web-page PIN. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("parents_tv", Context.MODE_PRIVATE)

    var lastChannelId: String?
        get() = prefs.getString(KEY_LAST, null)
        set(value) = prefs.edit().putString(KEY_LAST, value).apply()

    var previousChannelId: String?
        get() = prefs.getString(KEY_PREV, null)
        set(value) = prefs.edit().putString(KEY_PREV, value).apply()

    /** Required by the web page; shown on the TV in the channel list and with the Info key. */
    val pin: String
        get() = prefs.getString(KEY_PIN, null) ?: DEFAULT_PIN

    /** Favorite channel ids, in the order they were added. Stored on the TV so every phone sees the same list. */
    @get:Synchronized
    @set:Synchronized
    var favorites: List<String>
        get() = prefs.getString(KEY_FAVORITES, null)?.let { raw ->
            runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
        } ?: emptyList()
        set(value) = prefs.edit().putString(KEY_FAVORITES, JSONArray(value.distinct()).toString()).apply()

    /**
     * Whether the player should open by itself at power-on and after standby. Only takes effect
     * once the auto-start accessibility service is enabled on the TV (see [AutoStart]).
     */
    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOSTART, value).apply()

    @Synchronized fun addFavorite(id: String) { if (id !in favorites) favorites = favorites + id }

    @Synchronized fun removeFavorite(id: String) { favorites = favorites - id }

    private companion object {
        const val KEY_LAST = "last_channel"
        const val KEY_PREV = "previous_channel"
        const val KEY_FAVORITES = "favorites"
        const val KEY_AUTOSTART = "auto_start"
        // Older builds stored a random PIN under "pin"; this key starts fresh at the fixed default.
        const val KEY_PIN = "pin_v2"
        const val DEFAULT_PIN = "5048"
    }
}
