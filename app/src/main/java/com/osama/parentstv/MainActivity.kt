package com.osama.parentstv

import android.app.Activity
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.ui.PlayerView
import com.osama.parentstv.data.Channel
import com.osama.parentstv.data.ChannelRepository
import com.osama.parentstv.player.PlayerController
import com.osama.parentstv.web.NetworkInfo
import com.osama.parentstv.web.QrCode

/**
 * Full-screen player driven only by the remote:
 * CH+/CH- or Up/Down change channel, digits jump to a number, OK opens the list,
 * Info/Menu shows the management address. Back never leaves the app while watching.
 */
class MainActivity : Activity(), PlayerController.Listener, ChannelRepository.Listener, ParentsTvApp.RemoteControl {

    private lateinit var app: ParentsTvApp
    private lateinit var controller: PlayerController

    private lateinit var playerView: PlayerView
    private lateinit var banner: LinearLayout
    private lateinit var bannerNumber: TextView
    private lateinit var bannerName: TextView
    private lateinit var numberEntry: TextView
    private lateinit var status: TextView
    private lateinit var panel: LinearLayout
    private lateinit var list: ListView
    private lateinit var search: EditText
    /** What the channel list shows: all channels, or the search results. */
    private var listItems: List<Channel> = emptyList()
    private var favoriteIds: Set<String> = emptySet()
    private lateinit var manageQr: ImageView
    private lateinit var manageUrl: TextView
    private lateinit var managePin: TextView
    private var qrFor: String? = null
    private lateinit var infoPanel: LinearLayout
    private lateinit var infoUrl: TextView
    private lateinit var infoPin: TextView
    private lateinit var qrBigPanel: LinearLayout
    private lateinit var qrBig: ImageView
    private lateinit var qrBigUrl: TextView
    private lateinit var qrBigPin: TextView
    private lateinit var muteIndicator: TextView
    private lateinit var volumePanel: LinearLayout
    private lateinit var volumeValue: TextView
    private lateinit var volumeBar: ProgressBar
    private lateinit var audio: AudioManager

    private val ui = Handler(Looper.getMainLooper())
    private var channels: List<Channel> = emptyList()
    private var current: Channel? = null
    private var digits = ""

    private val hideBanner = Runnable { banner.visibility = View.GONE }
    private val hideList = Runnable { closeList() }
    private val hideInfo = Runnable { infoPanel.visibility = View.GONE }
    private val commitDigits = Runnable { jumpToTypedNumber() }
    private val hideBigQr = Runnable { qrBigPanel.visibility = View.GONE }
    private val hideVolume = Runnable { volumePanel.visibility = View.GONE }
    // Mute is handled by the system (or the TV firmware, which may never pass the key to us), so
    // the sign follows the actual audio state instead of counting key presses.
    private val refreshMute = object : Runnable {
        override fun run() {
            muteIndicator.visibility = if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) View.VISIBLE else View.GONE
            ui.removeCallbacks(this)
            ui.postDelayed(this, MUTE_POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        app = application as ParentsTvApp

        playerView = findViewById(R.id.player_view)
        banner = findViewById(R.id.banner)
        bannerNumber = findViewById(R.id.banner_number)
        bannerName = findViewById(R.id.banner_name)
        numberEntry = findViewById(R.id.number_entry)
        status = findViewById(R.id.status)
        panel = findViewById(R.id.channel_panel)
        list = findViewById(R.id.channel_list)
        search = findViewById(R.id.channel_search)
        manageQr = findViewById(R.id.manage_qr)
        manageUrl = findViewById(R.id.manage_url)
        managePin = findViewById(R.id.manage_pin)
        infoPanel = findViewById(R.id.info_panel)
        infoUrl = findViewById(R.id.info_url)
        infoPin = findViewById(R.id.info_pin)
        qrBigPanel = findViewById(R.id.qr_big_panel)
        qrBig = findViewById(R.id.qr_big)
        qrBigUrl = findViewById(R.id.qr_big_url)
        qrBigPin = findViewById(R.id.qr_big_pin)
        muteIndicator = findViewById(R.id.mute_indicator)
        volumePanel = findViewById(R.id.volume_panel)
        volumeValue = findViewById(R.id.volume_value)
        volumeBar = findViewById(R.id.volume_bar)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager

        list.adapter = adapter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
                if (panel.visibility == View.VISIBLE) keepListOpen(SEARCH_TIMEOUT_MS)
            }
        })
        // The keyboard's Search/Done key jumps to the first result.
        search.setOnEditorActionListener { _, _, _ ->
            if (listItems.isNotEmpty()) {
                list.requestFocus()
                list.setSelection(0)
            }
            true
        }
        list.setOnItemClickListener { _, _, position, _ ->
            listItems.getOrNull(position)?.let { tune(it) }
            closeList()
        }
    }

    override fun onStart() {
        super.onStart()
        controller = PlayerController(this, this)
        playerView.player = controller.player
        app.channels.addListener(this)
        app.remote = this
        channels = app.channels.playable()
        favoriteIds = app.settings.favorites.toSet()
        applyFilter()

        val start = channels.firstOrNull { it.id == app.settings.lastChannelId } ?: channels.firstOrNull()
        if (start != null) tune(start, remember = false) else showStatus(getString(R.string.status_no_channels))
        refreshMute.run()
    }

    override fun onStop() {
        // Release the decoder when another app is in front; the TV's hardware decoder is shared.
        app.channels.removeListener(this)
        if (app.remote === this) app.remote = null
        playerView.player = null
        controller.release()
        ui.removeCallbacksAndMessages(null)
        super.onStop()
    }

    // ---- Channel switching ------------------------------------------------------------------

    private fun tune(channel: Channel, remember: Boolean = true) {
        if (remember && current != null && current?.id != channel.id) {
            app.settings.previousChannelId = current?.id
        }
        current = channel
        app.settings.lastChannelId = channel.id
        controller.play(channel)
        showBanner(channel)
    }

    private fun step(delta: Int) {
        if (channels.isEmpty()) return
        val idx = channels.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        tune(channels[(idx + delta).mod(channels.size)])
    }

    private fun jumpToTypedNumber() {
        val number = digits.toIntOrNull()
        digits = ""
        numberEntry.visibility = View.GONE
        if (number != null && number in 1..channels.size) tune(channels[number - 1])
    }

    private fun numberOf(channel: Channel?): Int = channels.indexOfFirst { it.id == channel?.id } + 1

    // ---- Remote keys ------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return if (isHandledKey(event.keyCode)) true else super.dispatchKeyEvent(event)
        }
        val code = event.keyCode

        // Volume and mute stay with the system; just update the mute sign right after.
        if (code == KeyEvent.KEYCODE_VOLUME_MUTE || code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ui.postDelayed({ refreshMute.run() }, 300)
            return super.dispatchKeyEvent(event)
        }

        // Any key closes the big QR code.
        if (qrBigPanel.visibility == View.VISIBLE) {
            ui.removeCallbacks(hideBigQr)
            qrBigPanel.visibility = View.GONE
            return true
        }

        // While the search box has focus, every key (digits, Left/Right, OK) belongs to it;
        // Back returns to the results, or closes the list when there are none.
        if (panel.visibility == View.VISIBLE && search.hasFocus()) {
            keepListOpen(SEARCH_TIMEOUT_MS)
            if (code == KeyEvent.KEYCODE_BACK) {
                if (listItems.isNotEmpty()) list.requestFocus() else closeList()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // While the list is open, let the ListView handle navigation and OK.
        if (panel.visibility == View.VISIBLE) {
            when (code) {
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { showBigQr(); return true }
                KeyEvent.KEYCODE_SEARCH -> { search.requestFocus(); return true }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> { closeList(); return true }
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> closeList()
                else -> {
                    keepListOpen(LIST_TIMEOUT_MS)
                    return super.dispatchKeyEvent(event)
                }
            }
        }

        if (code == KeyEvent.KEYCODE_SEARCH) {
            openList()
            search.requestFocus()
            return true
        }

        digitOf(code)?.let { onDigit(it); return true }

        when (code) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> step(+1)
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> step(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (digits.isNotEmpty()) { ui.removeCallbacks(commitDigits); jumpToTypedNumber() } else openList()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> changeVolume(-VOLUME_STEP)
            KeyEvent.KEYCODE_DPAD_RIGHT -> changeVolume(+VOLUME_STEP)
            KeyEvent.KEYCODE_LAST_CHANNEL -> app.channels.get(app.settings.previousChannelId ?: "")
                ?.takeIf { it.enabled }?.let { tune(it) }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> toggleInfo()
            KeyEvent.KEYCODE_BACK -> hideOverlays()
            // Volume, mute, power, home and anything else: leave to the system.
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    private fun isHandledKey(code: Int): Boolean = digitOf(code) != null || code in setOf(
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
    ) && panel.visibility != View.VISIBLE

    private fun digitOf(code: Int): Int? = when (code) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    private fun onDigit(d: Int) {
        if (digits.length >= 3) digits = ""
        digits += d
        numberEntry.text = digits
        numberEntry.visibility = View.VISIBLE
        ui.removeCallbacks(commitDigits)
        // Switch as soon as the number is unambiguous, otherwise after a short pause.
        if ((digits + "0").toInt() > channels.size) jumpToTypedNumber()
        else ui.postDelayed(commitDigits, DIGIT_TIMEOUT_MS)
    }

    // ---- Overlays ---------------------------------------------------------------------------

    private fun showBanner(channel: Channel) {
        bannerNumber.text = numberOf(channel).toString()
        bannerName.text = channel.name
        banner.visibility = View.VISIBLE
        ui.removeCallbacks(hideBanner)
        ui.postDelayed(hideBanner, BANNER_TIMEOUT_MS)
    }

    private fun openList() {
        if (channels.isEmpty()) return
        updateManageHeader()
        search.setText("")
        applyFilter()
        panel.visibility = View.VISIBLE
        val pos = listItems.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        list.setSelection(pos)
        list.setItemChecked(pos, true)
        list.requestFocus()
        keepListOpen(LIST_TIMEOUT_MS)
    }

    private fun closeList() {
        ui.removeCallbacks(hideList)
        if (search.hasFocus()) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        }
        panel.visibility = View.GONE
        search.setText("")
    }

    private fun keepListOpen(timeoutMs: Long) {
        ui.removeCallbacks(hideList)
        ui.postDelayed(hideList, timeoutMs)
    }

    /** Filters the list by name or category; matching ignores case and accents ("cine" finds "Ciné"). */
    private fun applyFilter() {
        val query = normalize(search.text?.toString().orEmpty())
        listItems = if (query.isEmpty()) channels
        else channels.filter { normalize(it.name).contains(query) || normalize(it.group).contains(query) }
        adapter.notifyDataSetChanged()
        list.setItemChecked(listItems.indexOfFirst { it.id == current?.id }, true)
    }

    private fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .trim()

    /** Link, PIN and QR code of the management page; the QR is only redrawn when the address changes. */
    private fun updateManageHeader() {
        val ip = NetworkInfo.localIpv4()
        val url = ip?.let { "http://$it:${ParentsTvApp.PORT}" }
        manageUrl.text = url ?: getString(R.string.manage_offline)
        managePin.text = getString(R.string.info_pin, app.settings.pin)
        if (url != qrFor) {
            qrFor = url
            manageQr.setImageBitmap(url?.let { QrCode.bitmap(it, QR_SIZE_PX) })
            manageQr.visibility = if (url == null) View.GONE else View.VISIBLE
        }
    }

    /** Left/Right volume. Holding the key repeats. Scale is 0..max of the music stream (0..100 on this TV). */
    private fun changeVolume(delta: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val step = (delta * max / 100).let { if (it == 0) delta.coerceIn(-1, 1) else it }
        val level = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + step).coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        if (delta > 0 && audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        val percent = level * 100 / max
        volumeValue.text = getString(R.string.volume_value, percent)
        volumeBar.progress = percent
        volumePanel.visibility = View.VISIBLE
        ui.removeCallbacks(hideVolume)
        ui.postDelayed(hideVolume, VOLUME_TIMEOUT_MS)
        refreshMute.run()
    }

    private fun showBigQr() {
        val ip = NetworkInfo.localIpv4() ?: return
        val url = "http://$ip:${ParentsTvApp.PORT}"
        qrBig.setImageBitmap(QrCode.bitmap(url, QR_BIG_SIZE_PX))
        qrBigUrl.text = url
        qrBigPin.text = getString(R.string.info_pin, app.settings.pin)
        closeList()
        qrBigPanel.visibility = View.VISIBLE
        ui.removeCallbacks(hideBigQr)
        ui.postDelayed(hideBigQr, BIG_QR_TIMEOUT_MS)
    }

    private fun toggleInfo() {
        if (infoPanel.visibility == View.VISIBLE) { infoPanel.visibility = View.GONE; return }
        val ip = NetworkInfo.localIpv4()
        infoUrl.text = if (ip != null) getString(R.string.info_manage, "http://$ip:${ParentsTvApp.PORT}")
        else getString(R.string.info_no_network)
        infoPin.text = getString(R.string.info_pin, app.settings.pin)
        infoPanel.visibility = View.VISIBLE
        ui.removeCallbacks(hideInfo)
        ui.postDelayed(hideInfo, INFO_TIMEOUT_MS)
    }

    private fun hideOverlays() {
        closeList()
        infoPanel.visibility = View.GONE
        banner.visibility = View.GONE
        digits = ""
        numberEntry.visibility = View.GONE
    }

    private fun showStatus(text: String?) {
        status.text = text
        status.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    // ---- Callbacks --------------------------------------------------------------------------

    override fun onStatus(status: PlayerController.Status) {
        showStatus(
            when (status) {
                PlayerController.Status.PLAYING -> null
                PlayerController.Status.LOADING -> getString(R.string.status_loading)
                PlayerController.Status.RECONNECTING -> getString(R.string.status_reconnecting)
            }
        )
    }

    override fun onChannelsChanged(channels: List<Channel>) {
        this.channels = channels.filter { it.enabled }
        applyFilter()
        val cur = current
        val updated = this.channels.firstOrNull { it.id == cur?.id }
        when {
            this.channels.isEmpty() -> {
                current = null
                controller.stop()
                showStatus(getString(R.string.status_no_channels))
            }
            updated == null -> tune(this.channels.first())              // current channel was removed
            updated.urls != cur?.urls -> tune(updated, remember = false) // its links were edited
            else -> current = updated
        }
    }

    override fun play(channelId: String) {
        ui.post { channels.firstOrNull { it.id == channelId }?.let { tune(it) } }
    }

    override fun currentChannelId(): String? = current?.id

    override fun onFavoritesChanged() {
        ui.post {
            favoriteIds = app.settings.favorites.toSet()
            adapter.notifyDataSetChanged()
        }
    }

    override fun playerStatus(): org.json.JSONObject {
        val result = java.util.concurrent.FutureTask { controller.status() }
        ui.post(result)
        return result.get(2, java.util.concurrent.TimeUnit.SECONDS)
    }

    // ---- List adapter -----------------------------------------------------------------------

    private val adapter = object : BaseAdapter() {
        override fun getCount() = listItems.size
        override fun getItem(position: Int) = listItems[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
            val channel = listItems[position]
            // Always the real channel number, also in search results.
            v.findViewById<TextView>(R.id.item_number).text = numberOf(channel).toString()
            v.findViewById<TextView>(R.id.item_name).text = channel.name
            v.findViewById<TextView>(R.id.item_group).text =
                if (channel.id in favoriteIds) "★ ${channel.group}" else channel.group
            return v
        }
    }

    private companion object {
        const val BANNER_TIMEOUT_MS = 3_000L
        const val DIGIT_TIMEOUT_MS = 2_000L
        const val LIST_TIMEOUT_MS = 10_000L
        const val SEARCH_TIMEOUT_MS = 45_000L
        const val INFO_TIMEOUT_MS = 20_000L
        const val QR_SIZE_PX = 240
        const val QR_BIG_SIZE_PX = 600
        const val BIG_QR_TIMEOUT_MS = 60_000L
        const val MUTE_POLL_MS = 3_000L
        const val VOLUME_STEP = 3
        const val VOLUME_TIMEOUT_MS = 2_500L
    }
}
