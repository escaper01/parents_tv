package com.osama.parentstv.data

import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes the channels file format described in PLAN.md. */
object ChannelJson {
    const val VERSION = 1

    fun encode(channels: List<Channel>): String {
        val arr = JSONArray()
        channels.forEachIndexed { index, c -> arr.put(toJson(c, index + 1)) }
        return JSONObject().put("version", VERSION).put("channels", arr).toString(2)
    }

    fun toJson(c: Channel, number: Int? = null): JSONObject = JSONObject().apply {
        put("id", c.id)
        if (number != null) put("number", number)
        put("name", c.name)
        put("group", c.group)
        put("logo", c.logo)
        put("urls", JSONArray(c.urls))
        put("enabled", c.enabled)
    }

    /**
     * Accepts either `{"channels":[...]}` or a bare array. Entries without a name or a URL are
     * dropped. A single `url` string is accepted as well as a `urls` array.
     */
    fun decode(text: String): List<Channel> {
        val trimmed = text.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed)
        else JSONObject(trimmed).optJSONArray("channels") ?: JSONArray()

        val entries = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        // Honour an explicit "number" for ordering when present; otherwise keep file order.
        val ordered = entries.withIndex().sortedWith(
            compareBy({ it.value.optInt("number", Int.MAX_VALUE) }, { it.index })
        )
        return ordered.mapNotNull { fromJson(it.value) }
    }

    fun fromJson(o: JSONObject): Channel? {
        val name = o.optString("name").trim()
        val urls = buildList {
            o.optJSONArray("urls")?.let { a -> for (i in 0 until a.length()) add(a.optString(i)) }
            o.optString("url").takeIf { it.isNotBlank() }?.let { add(it) }
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (name.isEmpty() || urls.isEmpty()) return null

        val id = o.optString("id").trim().ifEmpty { slug(name) }
        return Channel(
            id = id,
            name = name,
            group = o.optString("group").trim(),
            logo = o.optString("logo").trim(),
            urls = urls,
            enabled = o.optBoolean("enabled", true),
        )
    }

    fun slug(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9\\u0600-\\u06ff]+"), "-").trim('-').ifEmpty { "channel" }
}
