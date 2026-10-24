package com.osama.parentstv.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Owns the channel list on disk (`files/channels.json`), seeded from `assets/default_channels.json`
 * on first run. All mutations are synchronized and saved atomically; listeners are notified on the
 * main thread so the player can react to changes made from the web page.
 */
class ChannelRepository(private val context: Context) {

    fun interface Listener {
        fun onChannelsChanged(channels: List<Channel>)
    }

    private val file = File(context.filesDir, "channels.json")
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<Listener>()
    private var channels: List<Channel> = load()

    @Synchronized fun all(): List<Channel> = channels

    /** Channels the viewer can switch between, in order. */
    @Synchronized fun playable(): List<Channel> = channels.filter { it.enabled }

    @Synchronized fun get(id: String): Channel? = channels.firstOrNull { it.id == id }

    /** Inserts or replaces by id. A new channel goes to the end unless [position] is given. */
    @Synchronized fun upsert(channel: Channel, position: Int? = null): Channel {
        val list = channels.toMutableList()
        val idx = list.indexOfFirst { it.id == channel.id }
        if (idx >= 0) {
            list[idx] = channel
        } else {
            val toAdd = channel.copy(id = ChannelMerger.uniqueId(channel.id, list))
            list.add((position ?: list.size).coerceIn(0, list.size), toAdd)
            commit(list)
            return toAdd
        }
        commit(list)
        return channel
    }

    @Synchronized fun delete(id: String): Boolean {
        val list = channels.filterNot { it.id == id }
        if (list.size == channels.size) return false
        commit(list)
        return true
    }

    /** Reorders to match [ids]; channels not mentioned keep their relative order at the end. */
    @Synchronized fun reorder(ids: List<String>) {
        val byId = channels.associateBy { it.id }
        val first = ids.mapNotNull { byId[it] }
        commit(first + channels.filterNot { it.id in ids.toSet() })
    }

    @Synchronized fun import(text: String): ImportResult {
        val incoming = if (M3uParser.looksLikeM3u(text)) M3uParser.parse(text) else ChannelJson.decode(text)
        val (list, result) = ChannelMerger.merge(channels, incoming)
        if (result.added + result.merged > 0) commit(list)
        return result
    }

    @Synchronized fun replaceAll(list: List<Channel>) = commit(list)

    @Synchronized fun export(): String = ChannelJson.encode(channels)

    @Synchronized fun resetToDefaults() = commit(defaults())

    fun addListener(l: Listener) = synchronized(listeners) { listeners += l }
    fun removeListener(l: Listener) = synchronized(listeners) { listeners -= l }

    private fun commit(list: List<Channel>) {
        channels = list
        val tmp = File(file.parentFile, "channels.json.tmp")
        tmp.writeText(ChannelJson.encode(list))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        val snapshot = list
        val targets = synchronized(listeners) { listeners.toList() }
        main.post { targets.forEach { it.onChannelsChanged(snapshot) } }
    }

    private fun load(): List<Channel> {
        if (file.exists()) {
            runCatching { return ChannelJson.decode(file.readText()) }
        }
        return defaults().also { list ->
            file.writeText(ChannelJson.encode(list))
        }
    }

    private fun defaults(): List<Channel> = runCatching {
        context.assets.open("default_channels.json").bufferedReader().use { ChannelJson.decode(it.readText()) }
    }.getOrDefault(emptyList())
}
