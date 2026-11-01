package com.example.metalrat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/**
 * Fills in what a capture couldn't know: the YouTube link for a video saved from the YouTube app
 * (found via its title), titles and preview images (YouTube thumbnail, og:image).
 */
class Enricher(private val store: BookmarkStore, private val scope: CoroutineScope) {
    fun enrich(id: String) {
        scope.launch(Dispatchers.IO) { runCatching { enrichNow(id) } }
    }

    private fun enrichNow(id: String) {
        var b = store.get(id) ?: return
        if (needsYouTubeSearch(b)) {
            val query = listOfNotNull(b.title, b.author).joinToString(" ")
            YouTube.search(query)?.let { videoId ->
                val url = "https://www.youtube.com/watch?v=$videoId"
                store.update(id) { it.copy(url = url) }
                b = b.copy(url = url)
            }
        }
        val url = b.url ?: return
        val videoId = Links.youTubeId(url)
        if (videoId != null) {
            // The YouTube thumbnail beats a local file: it still works after exporting to another device.
            val thumb = b.thumbnailUrl?.takeIf { it.startsWith("http") } ?: Links.youTubeThumbnail(videoId)
            val info = if (b.title == null || b.author == null) YouTube.oembed(url) else null
            store.update(id) {
                it.copy(thumbnailUrl = thumb, title = it.title ?: info?.first, author = it.author ?: info?.second)
            }
            return
        }
        if (!url.startsWith("http") || (b.thumbnailUrl != null && b.title != null)) return
        // Login walls: fetching them yields "Login • Instagram" and the logo, not the post.
        if (Links.host(url)?.let { h -> LOGIN_WALLED.any { h == it || h.endsWith(".$it") } } == true) return
        val meta = PageMeta.fetch(url) ?: return
        store.update(id) { it.copy(title = it.title ?: meta.title, thumbnailUrl = it.thumbnailUrl ?: meta.image) }
    }

    private companion object {
        val LOGIN_WALLED = setOf("instagram.com", "facebook.com", "threads.net", "tiktok.com")
    }

    private fun needsYouTubeSearch(b: Bookmark): Boolean {
        if (b.title.isNullOrBlank()) return false
        val url = b.url
        if (url == null) return b.sourcePackage in Links.YOUTUBE_PACKAGES
        // Browser address bar showed only "youtube.com": the video itself is unknown.
        return Links.youTubeId(url) == null && Links.host(url)?.endsWith("youtube.com") == true &&
            runCatching { URI(url).rawPath.orEmpty().trim('/').isEmpty() }.getOrDefault(false)
    }
}

internal object Http {
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    fun get(url: String, maxBytes: Int = 4_000_000, headers: Map<String, String> = emptyMap()): ByteArray? {
        var current = url
        repeat(5) {
            val conn = URI(current).toURL().openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 10_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
                headers.forEach(conn::setRequestProperty)
                when (conn.responseCode) {
                    in 300..399 -> {
                        // Followed by hand so http → https redirects work too.
                        val location = conn.getHeaderField("Location") ?: return null
                        current = URI(current).resolve(location).toString()
                    }
                    in 200..299 -> return conn.inputStream.use { readLimited(it, maxBytes) }
                    else -> return null
                }
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    fun getText(url: String, maxBytes: Int = 2_000_000, headers: Map<String, String> = emptyMap()): String? =
        get(url, maxBytes, headers)?.toString(Charsets.UTF_8)

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < maxBytes) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, minOf(n, maxBytes - out.size()))
        }
        return out.toByteArray()
    }
}

internal object YouTube {
    // EU consent cookie, otherwise youtube.com answers with the consent page instead of results.
    private val CONSENT = mapOf("Cookie" to "SOCS=CAI; CONSENT=YES+1")

    /** First real (non-ad) search result for [query]. */
    fun search(query: String): String? {
        if (query.isBlank()) return null
        val html = Http.getText("https://www.youtube.com/results?search_query=" + Links.encodeQuery(query), headers = CONSENT)
            ?: return null
        return Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
            ?: Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
    }

    /** Title and channel of a video, or null if it no longer exists. */
    fun oembed(url: String): Pair<String?, String?>? {
        val text = Http.getText("https://www.youtube.com/oembed?format=json&url=" + Links.encodeQuery(url)) ?: return null
        val obj = runCatching { BookmarkJson.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return obj["title"]?.jsonPrimitive?.content to obj["author_name"]?.jsonPrimitive?.content
    }
}

internal data class PageMeta(val title: String?, val image: String?) {
    companion object {
        private val META_TAG = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
        private val TITLE_TAG = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)

        fun fetch(url: String): PageMeta? {
            val html = Http.getText(url, maxBytes = 1_500_000) ?: return null
            return parse(url, html)
        }

        fun parse(url: String, html: String): PageMeta {
            val head = html.substringBefore("</head>")
            val metas = META_TAG.findAll(head).mapNotNull { m ->
                val tag = m.value
                val key = attr(tag, "property") ?: attr(tag, "name") ?: return@mapNotNull null
                val content = attr(tag, "content") ?: return@mapNotNull null
                key.lowercase() to content
            }.toMap()
            val title = metas["og:title"] ?: TITLE_TAG.find(head)?.groupValues?.get(1)
            val image = (metas["og:image"] ?: metas["og:image:url"] ?: metas["twitter:image"])
                ?.let { runCatching { URI(url).resolve(entities(it).trim()).toString() }.getOrNull() }
                ?.takeIf { it.startsWith("http") }
            return PageMeta(title?.let(::entities)?.trim()?.takeIf { it.isNotEmpty() }, image)
        }

        private fun attr(tag: String, name: String): String? =
            Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE).find(tag)?.let {
                it.groupValues[2].ifEmpty { it.groupValues[3] }
            }

        private fun entities(s: String): String = s
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&#039;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: it.value }
            .replace("&amp;", "&")
    }
}
