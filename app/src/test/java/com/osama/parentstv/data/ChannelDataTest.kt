package com.osama.parentstv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelDataTest {

    private val m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="2m.ma@SD" tvg-logo="https://x/2m.png" group-title="General",2M Monde (360p) [Not 24/7]
        https://a.example/2m/index.m3u8
        #EXTINF:-1 tvg-id="",France 24 Arabic (1080p)
        #EXTVLCOPT:http-user-agent=foo
        https://b.example/f24/index.m3u8
        #EXTINF:-1,
        https://c.example/no-name.m3u8
    """.trimIndent()

    @Test fun `m3u parser reads attributes and strips quality tags`() {
        val list = M3uParser.parse(m3u)
        assertEquals(2, list.size)
        assertEquals("2m.ma@sd", list[0].id)
        assertEquals("2M Monde", list[0].name)
        assertEquals("General", list[0].group)
        assertEquals("https://x/2m.png", list[0].logo)
        assertEquals("france-24-arabic", list[1].id)
        assertEquals(listOf("https://b.example/f24/index.m3u8"), list[1].urls)
    }

    @Test fun `json round trip keeps order and fields`() {
        val src = listOf(
            Channel("a", "A", "G", "L", listOf("u1", "u2"), enabled = false),
            Channel("b", "B", urls = listOf("u3")),
        )
        assertEquals(src, ChannelJson.decode(ChannelJson.encode(src)))
    }

    @Test fun `json decode accepts bare array, single url and drops invalid entries`() {
        val list = ChannelJson.decode("""[{"name":"X","url":"http://x"},{"name":"","urls":["http://y"]},{"name":"Z"}]""")
        assertEquals(1, list.size)
        assertEquals("x", list[0].id)
        assertEquals(listOf("http://x"), list[0].urls)
    }

    @Test fun `json decode orders by explicit number`() {
        val list = ChannelJson.decode("""{"channels":[{"name":"B","number":2,"url":"u2"},{"name":"A","number":1,"url":"u1"}]}""")
        assertEquals(listOf("A", "B"), list.map { it.name })
    }

    @Test fun `importing the same channels twice creates no duplicates`() {
        val start = listOf(Channel("2m", "2M", urls = listOf("http://a/2m.m3u8")))
        val incoming = listOf(
            Channel("2m", "2M", urls = listOf("http://a/2m.m3u8")),
            Channel("f24", "France 24", urls = listOf("http://b/f24.m3u8")),
        )
        val (once, r1) = ChannelMerger.merge(start, incoming)
        assertEquals(ImportResult(added = 1, merged = 0, unchanged = 1), r1)

        val (twice, r2) = ChannelMerger.merge(once, incoming)
        assertEquals(ImportResult(added = 0, merged = 0, unchanged = 2), r2)
        assertEquals(once, twice)
    }

    @Test fun `same url under another id merges and new urls become fallbacks`() {
        val start = listOf(Channel("2m", "2M", urls = listOf("http://a/2m.m3u8")))
        val incoming = listOf(Channel("2m-tv", "2M TV", logo = "logo", urls = listOf("HTTP://A/2m.m3u8/", "http://backup/2m.m3u8")))
        val (list, r) = ChannelMerger.merge(start, incoming)
        assertEquals(ImportResult(added = 0, merged = 1, unchanged = 0), r)
        assertEquals(1, list.size)
        assertEquals("2M", list[0].name)
        assertEquals(listOf("http://a/2m.m3u8", "http://backup/2m.m3u8"), list[0].urls)
        assertEquals("logo", list[0].logo)
    }

    @Test fun `m3u detection`() {
        assertTrue(M3uParser.looksLikeM3u(m3u))
        assertFalse(M3uParser.looksLikeM3u("""{"channels":[]}"""))
    }

    @Test fun `unique id avoids collisions`() {
        val list = listOf(Channel("x", "X", urls = listOf("u")), Channel("x-2", "X", urls = listOf("v")))
        assertEquals("x-3", ChannelMerger.uniqueId("x", list))
    }
}
