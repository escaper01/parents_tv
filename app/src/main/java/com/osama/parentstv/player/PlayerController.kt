package com.osama.parentstv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.osama.parentstv.data.Channel

/**
 * Plays one channel at a time. When a stream fails it moves to the channel's next URL; once all
 * URLs have failed it waits and starts over, forever, so a temporary outage heals by itself.
 */
@OptIn(UnstableApi::class)
class PlayerController(context: Context, private val listener: Listener) {

    interface Listener {
        fun onStatus(status: Status)
    }

    enum class Status { LOADING, PLAYING, RECONNECTING }

    private val main = Handler(Looper.getMainLooper())

    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)

    // Satellite restreams often carry H.264 without access-unit delimiters and with non-IDR
    // keyframes; without these flags the player parses the packets but never outputs a frame.
    private val hls = HlsMediaSource.Factory(http).setExtractorFactory(
        DefaultHlsExtractorFactory(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            /* exposeCea608WhenMissingDeclarations= */ true,
        )
    )
    private val other = DefaultMediaSourceFactory(http)

    // Always the best variant the TV can decode, instead of starting low and adapting upwards.
    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(buildUponParameters().setForceHighestSupportedBitrate(true))
    }

    // Start showing video after 1 s of buffer instead of the default 2.5 s: faster channel
    // switches and a faster first picture at power-on, at little risk on a fast connection.
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs= */ 15_000,
            /* maxBufferMs= */ 50_000,
            /* bufferForPlaybackMs= */ 1_000,
            /* bufferForPlaybackAfterRebufferMs= */ 2_500,
        )
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .build()

    private var channel: Channel? = null
    private var urlIndex = 0
    private var failedRounds = 0
    private val retry = Runnable { startUrl() }
    // Some servers answer but never deliver playable data; without this the screen would say
    // "Loading" forever and the fallback URLs would never be tried.
    private val stallWatchdog = Runnable { onStreamFailed("stalled for ${STALL_TIMEOUT_MS / 1000}s") }

    init {
        player.addAnalyticsListener(EventLogger(TAG))
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        listener.onStatus(Status.LOADING)
                        armWatchdog()
                    }
                    Player.STATE_READY -> {
                        failedRounds = 0
                        main.removeCallbacks(stallWatchdog)
                        listener.onStatus(Status.PLAYING)
                    }
                    // A live stream "ending" means the server stopped sending; treat it as a failure.
                    Player.STATE_ENDED -> onStreamFailed("stream ended")
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onStreamFailed(error.errorCodeName)
            }
        })
    }

    fun play(channel: Channel) {
        main.removeCallbacks(retry)
        this.channel = channel
        urlIndex = 0
        failedRounds = 0
        startUrl()
    }

    fun stop() {
        main.removeCallbacks(retry)
        main.removeCallbacks(stallWatchdog)
        player.stop()
    }

    /** What is actually on screen right now; must be called on the main thread. */
    fun status(): org.json.JSONObject {
        val v = player.videoFormat
        val a = player.audioFormat
        return org.json.JSONObject()
            .put("channel", channel?.id ?: org.json.JSONObject.NULL)
            .put("url", channel?.urls?.getOrNull(urlIndex) ?: org.json.JSONObject.NULL)
            .put("playing", player.isPlaying)
            .put("video", v?.let { "${it.sampleMimeType} ${it.width}x${it.height}" } ?: org.json.JSONObject.NULL)
            .put("audio", a?.let { "${it.sampleMimeType} ${it.channelCount}ch" } ?: org.json.JSONObject.NULL)
            .put("framesRendered", player.videoDecoderCounters?.renderedOutputBufferCount ?: 0)
            .put("audioBuffers", player.audioDecoderCounters?.renderedOutputBufferCount ?: 0)
            .put("bitrate", v?.bitrate ?: -1)
    }

    fun release() {
        main.removeCallbacks(retry)
        main.removeCallbacks(stallWatchdog)
        player.release()
    }

    private fun armWatchdog() {
        main.removeCallbacks(stallWatchdog)
        main.postDelayed(stallWatchdog, STALL_TIMEOUT_MS)
    }

    private fun startUrl() {
        val c = channel ?: return
        val url = c.urls.getOrNull(urlIndex) ?: return
        Log.i(TAG, "Playing ${c.name} [${urlIndex + 1}/${c.urls.size}] $url")
        listener.onStatus(Status.LOADING)
        armWatchdog()
        player.setMediaSource(mediaSourceFor(url))
        player.prepare()
        player.playWhenReady = true
    }

    /** IPTV links are nearly always HLS, often without a .m3u8 extension; treat them as HLS
     *  unless the URL clearly names a plain media file or a DASH/SmoothStreaming manifest. */
    private fun mediaSourceFor(url: String): MediaSource {
        val item = MediaItem.fromUri(url)
        val path = Uri.parse(url).path.orEmpty().lowercase()
        val plainFile = PLAIN_FILE_EXTENSIONS.any { path.endsWith(it) }
        val type = Util.inferContentType(Uri.parse(url))
        return if (!plainFile && (type == C.CONTENT_TYPE_HLS || type == C.CONTENT_TYPE_OTHER)) {
            hls.createMediaSource(item)
        } else {
            other.createMediaSource(item)
        }
    }

    private fun onStreamFailed(reason: String) {
        main.removeCallbacks(stallWatchdog)
        val c = channel ?: return
        Log.w(TAG, "${c.name} url ${urlIndex + 1} failed: $reason")
        if (urlIndex + 1 < c.urls.size) {
            urlIndex++
            startUrl()
            return
        }
        // Every URL failed: back off (5 s, 10 s, … capped at 30 s) and try the list again.
        urlIndex = 0
        failedRounds++
        listener.onStatus(Status.RECONNECTING)
        main.postDelayed(retry, (5_000L * failedRounds).coerceAtMost(30_000L))
    }

    private companion object {
        const val TAG = "ParentsTv.Player"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 9) ParentsTV/1.0"
        const val STALL_TIMEOUT_MS = 20_000L
        val PLAIN_FILE_EXTENSIONS = listOf(".ts", ".mp4", ".mkv", ".mp3", ".aac", ".flv")
    }
}
