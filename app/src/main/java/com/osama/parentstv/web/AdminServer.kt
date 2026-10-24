package com.osama.parentstv.web

import android.util.Log
import com.osama.parentstv.ParentsTvApp
import com.osama.parentstv.data.ChannelJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Minimal HTTP server for the channel management page. Serves `assets/admin/index.html` and a
 * small JSON API. Every `/api/` call except `/api/ping` needs the PIN shown on the TV, passed as
 * the `X-Pin` header (or `?pin=` for the export download link).
 */
class AdminServer(private val app: ParentsTvApp, private val port: Int) {

    private val pool = Executors.newFixedThreadPool(4)
    private var socket: ServerSocket? = null

    fun start() {
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
        socket = server
        Thread({
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                pool.execute { handle(client) }
            }
        }, "admin-server").apply { isDaemon = true }.start()
        Log.i(TAG, "Admin page on port $port")
    }

    fun stop() {
        runCatching { socket?.close() }
        pool.shutdownNow()
    }

    // ---- HTTP plumbing ----------------------------------------------------------------------

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String,
    )

    private class Response(
        val status: Int,
        val body: ByteArray,
        val type: String = "application/json; charset=utf-8",
        val extraHeaders: Map<String, String> = emptyMap(),
    ) {
        companion object {
            fun json(o: Any, status: Int = 200) = Response(status, o.toString().toByteArray())
            fun error(status: Int, message: String) = json(JSONObject().put("error", message), status)
        }
    }

    private fun handle(client: Socket) {
        client.use { s ->
            s.soTimeout = 15_000
            val response = try {
                val req = readRequest(BufferedInputStream(s.getInputStream())) ?: return
                route(req)
            } catch (e: Exception) {
                Log.w(TAG, "Request failed", e)
                Response.error(400, e.message ?: "bad request")
            }
            writeResponse(s.getOutputStream(), response)
        }
    }

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        require(length <= MAX_BODY) { "body too large" }
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }

        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return Request(parts[0].uppercase(), URLDecoder.decode(path, "UTF-8"), query, headers, String(body, 0, read))
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
            if (buf.size() > 8192) throw IllegalArgumentException("header too long")
        }
    }

    private fun writeResponse(out: OutputStream, r: Response) {
        val head = buildString {
            append("HTTP/1.1 ${r.status} ${reason(r.status)}\r\n")
            append("Content-Type: ${r.type}\r\n")
            append("Content-Length: ${r.body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            r.extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
            append("\r\n")
        }
        out.write(head.toByteArray())
        out.write(r.body)
        out.flush()
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; else -> "Error"
    }

    // ---- Routes -----------------------------------------------------------------------------

    private fun route(req: Request): Response {
        if (req.method == "GET" && (req.path == "/" || req.path == "/index.html")) {
            val html = app.assets.open("admin/index.html").use { it.readBytes() }
            return Response(200, html, "text/html; charset=utf-8")
        }
        if (!req.path.startsWith("/api/")) return Response.error(404, "not found")
        if (req.path == "/api/ping") return Response.json(JSONObject().put("ok", true))

        val pin = req.headers["x-pin"] ?: req.query["pin"]
        if (pin != app.settings.pin) return Response.error(401, "wrong PIN")

        val repo = app.channels
        val segments = req.path.removePrefix("/api/").split('/')
        return when {
            req.method == "GET" && req.path == "/api/channels" -> Response.json(
                JSONObject()
                    .put("channels", JSONArray(repo.all().mapIndexed { i, c -> ChannelJson.toJson(c, i + 1) }))
                    .put("current", app.remote?.currentChannelId() ?: JSONObject.NULL)
                    // Ids of deleted channels are dropped here rather than kept as ghosts.
                    .put("favorites", JSONArray(app.settings.favorites.filter { repo.get(it) != null }))
            )

            // Favorites: POST adds, DELETE removes. The id is the channel id (URL-encoded).
            segments.size == 2 && segments[0] == "favorites" && (req.method == "POST" || req.method == "DELETE") -> {
                val id = segments[1]
                if (repo.get(id) == null) return Response.error(404, "no such channel")
                if (req.method == "POST") app.settings.addFavorite(id) else app.settings.removeFavorite(id)
                app.notifyFavoritesChanged()
                Response.json(JSONObject().put("favorites", JSONArray(app.settings.favorites)))
            }

            // Create or update one channel. Body: channel object; optional "position" (0-based).
            req.method == "POST" && req.path == "/api/channels" -> {
                val body = JSONObject(req.body)
                val channel = ChannelJson.fromJson(body) ?: return Response.error(400, "name and at least one URL are required")
                val saved = repo.upsert(channel, body.optInt("position", -1).takeIf { it >= 0 })
                Response.json(ChannelJson.toJson(saved))
            }

            req.method == "DELETE" && segments.size == 2 && segments[0] == "channels" ->
                if (repo.delete(segments[1])) Response.json(JSONObject().put("ok", true))
                else Response.error(404, "no such channel")

            req.method == "POST" && req.path == "/api/reorder" -> {
                val ids = JSONObject(req.body).getJSONArray("ids")
                repo.reorder((0 until ids.length()).map { ids.getString(it) })
                Response.json(JSONObject().put("ok", true))
            }

            // Body: raw JSON (our format) or M3U text. Duplicates are merged, never doubled.
            req.method == "POST" && req.path == "/api/import" -> Response.json(repo.import(req.body).toJson())

            req.method == "POST" && req.path == "/api/import-url" -> {
                val url = JSONObject(req.body).getString("url")
                Response.json(repo.import(fetchText(url)).toJson())
            }

            req.method == "GET" && req.path == "/api/export" -> Response(
                200, repo.export().toByteArray(),
                extraHeaders = mapOf("Content-Disposition" to "attachment; filename=\"channels.json\""),
            )

            req.method == "POST" && req.path == "/api/reset" -> {
                repo.resetToDefaults()
                Response.json(JSONObject().put("ok", true))
            }

            req.method == "POST" && segments.size == 2 && segments[0] == "play" -> {
                val remote = app.remote ?: return Response.error(400, "the player is not on screen")
                remote.play(segments[1])
                Response.json(JSONObject().put("ok", true))
            }

            req.method == "GET" && req.path == "/api/status" -> {
                val remote = app.remote ?: return Response.error(400, "the player is not on screen")
                Response.json(remote.playerStatus())
            }

            req.method == "POST" && req.path == "/api/test" ->
                Response.json(testStream(JSONObject(req.body).getString("url")))

            else -> Response.error(404, "not found")
        }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) ParentsTV/1.0")
        }

    private fun fetchText(url: String): String {
        val conn = open(url)
        try {
            require(conn.responseCode in 200..299) { "download failed: HTTP ${conn.responseCode}" }
            return conn.inputStream.use { it.readBytes().also { b -> require(b.size <= MAX_BODY) { "file too large" } } }
                .toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    /** Quick reachability check: does the URL answer with 2xx and look like a stream/playlist? */
    private fun testStream(url: String): JSONObject = runCatching {
        val conn = open(url)
        try {
            val code = conn.responseCode
            val type = conn.contentType.orEmpty()
            val head = if (code in 200..299) conn.inputStream.use { s -> ByteArray(512).let { String(it, 0, s.read(it).coerceAtLeast(0)) } } else ""
            val ok = code in 200..299 && (head.contains("#EXTM3U") || type.contains("mpegurl", true) || type.startsWith("video") || type.contains("octet-stream"))
            JSONObject().put("ok", ok).put("status", code).put("type", type)
        } finally {
            conn.disconnect()
        }
    }.getOrElse { JSONObject().put("ok", false).put("error", it.message ?: it.javaClass.simpleName) }

    private companion object {
        const val TAG = "ParentsTv.Admin"
        const val MAX_BODY = 5 * 1024 * 1024
    }
}
