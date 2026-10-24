package com.osama.parentstv

import android.app.Application
import android.util.Log
import com.osama.parentstv.data.ChannelRepository
import com.osama.parentstv.data.Settings
import com.osama.parentstv.web.AdminServer

class ParentsTvApp : Application() {

    lateinit var channels: ChannelRepository
        private set
    lateinit var settings: Settings
        private set

    /** Set by the player while it is on screen, so the web page can switch channels remotely. */
    @Volatile var remote: RemoteControl? = null

    interface RemoteControl {
        fun play(channelId: String)
        fun currentChannelId(): String?
        /** Player diagnostics (formats, frames rendered); blocks briefly to read on the main thread. */
        fun playerStatus(): org.json.JSONObject
        /** Favorites changed from the web page; refresh the stars in the channel list. */
        fun onFavoritesChanged()
    }

    fun notifyFavoritesChanged() {
        remote?.onFavoritesChanged()
    }

    private var server: AdminServer? = null

    override fun onCreate() {
        super.onCreate()
        channels = ChannelRepository(this)
        settings = Settings(this)
        startAdminServer()
    }

    private fun startAdminServer() {
        server = AdminServer(this, PORT).also {
            runCatching { it.start() }.onFailure { e -> Log.e(TAG, "Admin server failed to start", e) }
        }
    }

    companion object {
        const val TAG = "ParentsTv"
        const val PORT = 8080
    }
}
