package com.example.eichhorn

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Receives captures from the browser extension on 127.0.0.1 only.
 * Web pages can't use it: requests carrying a web origin are refused, only extension origins
 * (and origin-less local tools like curl) get through.
 */
class CaptureServer(private val store: BookmarkStore, private val enricher: Enricher) {
    var error: String? = null
        private set

    fun start(): Boolean = try {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0)
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (e: Exception) {
                runCatching { respond(exchange, 500, """{"ok":false}""") }
            } finally {
                exchange.close()
            }
        }
        server.executor = Executors.newSingleThreadExecutor { r -> Thread(r, "eichhorn-capture").apply { isDaemon = true } }
        server.start()
        true
    } catch (e: IOException) {
        error = "Port $PORT ist belegt – läuft Eichhorn schon in einem anderen Fenster? Die Browser-Erweiterung liefert dorthin."
        false
    }

    private fun handle(ex: HttpExchange) {
        val origin = ex.requestHeaders.getFirst("Origin")
        if (origin != null) {
            if (EXTENSION_ORIGINS.none { origin.startsWith(it) }) return respond(ex, 403, """{"ok":false}""")
            ex.responseHeaders.add("Access-Control-Allow-Origin", origin)
            ex.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")
            ex.responseHeaders.add("Access-Control-Allow-Private-Network", "true")
        }
        val path = ex.requestURI.path
        when {
            ex.requestMethod == "OPTIONS" -> respond(ex, 204, null)
            path == "/ping" -> respond(ex, 200, """{"app":"Eichhorn"}""")
            path == "/add" && ex.requestMethod == "POST" -> {
                val body = ex.requestBody.use { it.readNBytes(1_000_000) }.toString(Charsets.UTF_8)
                val request = runCatching { BookmarkJson.json.decodeFromString(CaptureRequest.serializer(), body) }
                    .getOrElse { return respond(ex, 400, """{"ok":false}""") }
                val bookmark = request.toBookmark()
                store.add(bookmark)
                enricher.enrich(bookmark.id)
                respond(ex, 200, """{"ok":true}""")
            }
            else -> respond(ex, 404, """{"ok":false}""")
        }
    }

    private fun respond(ex: HttpExchange, code: Int, body: String?) {
        if (body == null) {
            ex.sendResponseHeaders(code, -1)
            return
        }
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    companion object {
        const val PORT = 47123
        private val EXTENSION_ORIGINS = listOf("chrome-extension://", "moz-extension://", "safari-web-extension://")
    }
}
