package com.osama.parentstv.data

data class ImportResult(val added: Int, val merged: Int, val unchanged: Int) {
    fun toJson() = org.json.JSONObject().put("added", added).put("merged", merged).put("unchanged", unchanged)
}

/**
 * Merges imported channels into an existing list without creating duplicates.
 *
 * An incoming channel matches an existing one when the ids are equal or when they share any
 * stream URL. On a match, new URLs are appended as fallbacks and empty fields are filled in;
 * the existing name, position and enabled flag are kept. Unmatched channels are appended.
 */
object ChannelMerger {

    fun normalizeUrl(url: String): String = url.trim().trimEnd('/').lowercase()

    fun merge(existing: List<Channel>, incoming: List<Channel>): Pair<List<Channel>, ImportResult> {
        val out = existing.toMutableList()
        var added = 0
        var merged = 0
        var unchanged = 0

        for (inc in incoming) {
            val incUrls = inc.urls.map(::normalizeUrl).toSet()
            val idx = out.indexOfFirst { c ->
                c.id.equals(inc.id, ignoreCase = true) || c.urls.any { normalizeUrl(it) in incUrls }
            }
            if (idx < 0) {
                out += inc.copy(id = uniqueId(inc.id, out))
                added++
                continue
            }
            val cur = out[idx]
            val known = cur.urls.map(::normalizeUrl).toMutableSet()
            val extraUrls = inc.urls.filter { known.add(normalizeUrl(it)) }
            val updated = cur.copy(
                urls = cur.urls + extraUrls,
                logo = cur.logo.ifEmpty { inc.logo },
                group = cur.group.ifEmpty { inc.group },
            )
            if (updated != cur) {
                out[idx] = updated
                merged++
            } else {
                unchanged++
            }
        }
        return out to ImportResult(added, merged, unchanged)
    }

    fun uniqueId(base: String, list: List<Channel>): String {
        if (list.none { it.id.equals(base, ignoreCase = true) }) return base
        var n = 2
        while (list.any { it.id.equals("$base-$n", ignoreCase = true) }) n++
        return "$base-$n"
    }
}
