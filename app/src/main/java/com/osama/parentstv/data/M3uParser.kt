package com.osama.parentstv.data

/** Converts an extended M3U playlist (#EXTM3U / #EXTINF) into channels. */
object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")
    // iptv-org appends quality and status tags to names, e.g. "2M Monde (360p) [Not 24/7]".
    private val nameNoise = Regex("""\s*(\(\d{3,4}[pi]\)|\[[^\]]*\])""")

    fun looksLikeM3u(text: String): Boolean = text.trimStart().startsWith("#EXTM3U") || text.contains("#EXTINF")

    fun parse(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var pending: Pair<Map<String, String>, String>? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val commaAt = line.lastIndexOf(',')
                    val header = if (commaAt >= 0) line.substring(0, commaAt) else line
                    val name = if (commaAt >= 0) line.substring(commaAt + 1) else ""
                    val attrs = attr.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    pending = attrs to name
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                pending != null -> {
                    val (attrs, rawName) = pending!!
                    val name = cleanName(rawName.ifBlank { attrs["tvg-name"].orEmpty() })
                    if (name.isNotEmpty()) {
                        val tvgId = attrs["tvg-id"].orEmpty().trim()
                        result += Channel(
                            id = if (tvgId.isNotEmpty()) tvgId.lowercase() else ChannelJson.slug(name),
                            name = name,
                            group = attrs["group-title"].orEmpty().trim(),
                            logo = attrs["tvg-logo"].orEmpty().trim(),
                            urls = listOf(line),
                        )
                    }
                    pending = null
                }
            }
        }
        return result
    }

    fun cleanName(name: String): String = name.replace(nameNoise, "").trim()
}
