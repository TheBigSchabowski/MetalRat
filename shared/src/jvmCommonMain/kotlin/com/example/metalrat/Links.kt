package com.example.metalrat

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** URL handling: video IDs, jump-to-time links and text fragments (#:~:text=…). */
object Links {
    private val YT_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""")
    private val BARE_DOMAIN = Regex("""^[\w-]+(\.[\w-]+)+(:\d+)?(/\S*)?$""")
    private val MEDIA_FILE = Regex("""\.(mp4|webm|mkv|mov|m4v|mp3|m4a|ogg|opus|wav|flac)$""", RegexOption.IGNORE_CASE)

    /** Android packages of YouTube and its forks. */
    val YOUTUBE_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "app.revanced.android.youtube",
        "app.rvx.android.youtube",
        "com.vanced.android.youtube",
    )

    private fun parse(url: String): URI? {
        val s = url.trim()
        return runCatching { URI(s) }.getOrNull()
            ?: runCatching { URI(s.replace(" ", "%20").replace("|", "%7C").replace("\"", "%22")) }.getOrNull()
    }

    fun host(url: String): String? = parse(url)?.host?.lowercase()?.removePrefix("www.")

    fun queryParams(rawQuery: String?): Map<String, String> =
        rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        }

    fun youTubeId(url: String): String? {
        val uri = parse(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.rawPath.orEmpty()
        val id = when {
            host == "youtu.be" -> path.trim('/').substringBefore('/')
            host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith("youtube-nocookie.com") -> when (path) {
                "/watch" -> queryParams(uri.rawQuery)["v"]
                else -> Regex("^/(shorts|live|embed|v)/([^/?#]+)").find(path)?.groupValues?.get(2)
            }
            else -> null
        }
        return id?.takeIf { YT_ID.matches(it) }
    }

    fun isYouTubeVideoId(s: String?): Boolean = s != null && YT_ID.matches(s)

    fun youTubeThumbnail(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    /** The link that opens the bookmark at the saved time / text passage. */
    fun openUrl(b: Bookmark): String? {
        val base = b.url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val pos = b.positionSec?.takeIf { it > 0 }
        youTubeId(base)?.let { id ->
            return "https://www.youtube.com/watch?v=$id" + (pos?.let { "&t=${it}s" } ?: "")
        }
        var url = base
        if (pos != null) url = withTime(url, pos)
        val text = b.selectedText
        if (!text.isNullOrBlank() && !url.contains(":~:")) url = withTextFragment(url, text)
        return url
    }

    private fun withTime(url: String, sec: Long): String {
        val host = host(url) ?: return url
        val path = parse(url)?.rawPath.orEmpty()
        return when {
            host == "vimeo.com" || host.endsWith(".vimeo.com") -> url.substringBefore('#') + "#t=${sec}s"
            host.endsWith("twitch.tv") && "/videos/" in path -> setQuery(url, "t", twitchTime(sec))
            host.endsWith("dailymotion.com") -> setQuery(url, "start", "$sec")
            MEDIA_FILE.containsMatchIn(path) -> url.substringBefore('#') + "#t=$sec"
            else -> url
        }
    }

    private fun twitchTime(sec: Long) = "${sec / 3600}h${sec % 3600 / 60}m${sec % 60}s"

    fun setQuery(url: String, key: String, value: String): String {
        val fragment = url.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
        val noFragment = url.substringBefore('#')
        val base = noFragment.substringBefore('?')
        val rest = noFragment.substringAfter('?', "").split('&').filter { it.isNotEmpty() && it.substringBefore('=') != key }
        return base + "?" + (rest + "$key=$value").joinToString("&") + fragment
    }

    /** Appends a text fragment so the browser scrolls to [text] and highlights it. */
    fun withTextFragment(url: String, text: String): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return url
        // Long passages: match from the first to the last words (textStart,textEnd) so line breaks,
        // footnote marks etc. in between don't break the match.
        val directive = if (words.size <= 10) encodeFragmentText(words.joinToString(" "))
        else encodeFragmentText(words.take(4).joinToString(" ")) + "," + encodeFragmentText(words.takeLast(4).joinToString(" "))
        val separator = if ('#' in url) "" else "#"
        return "$url$separator:~:text=$directive"
    }

    /** Percent-encodes everything except unreserved characters; '-', ',' and '&' have meaning in text directives. */
    fun encodeFragmentText(s: String): String = buildString {
        for (byte in s.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xff
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == '~') append(ch)
            else append('%').append("%02X".format(c))
        }
    }

    /** The highlighted text of a link that already carries a text fragment (e.g. Chrome's "share highlight"). */
    fun selectionFromFragment(url: String): String? {
        val directives = url.substringAfter(":~:", "").takeIf { it.isNotEmpty() } ?: return null
        val parts = directives.split('&').filter { it.startsWith("text=") }.map { directive ->
            directive.removePrefix("text=").split(',')
                .filterNot { it.endsWith("-") || it.startsWith("-") } // prefix-/-suffix context
                .joinToString(" … ") { decode(it) }
        }.filter { it.isNotBlank() }
        return parts.joinToString(" … ").takeIf { it.isNotBlank() }
    }

    /** Video position contained in a shared link (?t=1m23s, &start=83, #t=90s …). */
    fun timeFromUrl(url: String): Long? {
        val uri = parse(url) ?: return null
        val params = queryParams(uri.rawQuery) + queryParams(uri.rawFragment?.substringBefore(":~:"))
        val value = params["t"] ?: params["start"] ?: params["time_continue"] ?: return null
        return parseTime(value)?.takeIf { it > 0 }
    }

    /** "83", "83s", "1m23s", "1h2m3s", "1:23", "1:02:03" → seconds. */
    fun parseTime(value: String): Long? {
        val s = value.trim().lowercase()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { return it }
        if (':' in s) return parseClock(s)
        s.removeSuffix("s").toDoubleOrNull()?.let { return it.toLong() }
        val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$").matchEntire(s) ?: return null
        val (h, min, sec) = m.destructured
        return (h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (sec.toLongOrNull() ?: 0)
    }

    private fun parseClock(s: String): Long? {
        val parts = s.split(':')
        if (parts.size > 3 || parts.any { it.toLongOrNull() == null }) return null
        return parts.fold(0L) { acc, p -> acc * 60 + p.toLong() }
    }

    fun formatClock(sec: Long): String {
        val h = sec / 3600
        val m = sec % 3600 / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun extractUrl(text: String): String? {
        val raw = URL_IN_TEXT.find(text)?.value ?: return null
        var url = raw.trimEnd('.', ',', ';', '!', '?', '"', '\'', ']', '}', '>', '»', '“')
        // Wikipedia links end in ')' legitimately, prose around a link doesn't.
        while (url.endsWith(')') && url.count { it == ')' } > url.count { it == '(' }) url = url.dropLast(1)
        return url
    }

    /** Adds https:// to things like "en.wikipedia.org/wiki/X"; leaves anything else untouched. */
    fun normalizeUrl(raw: String): String {
        val s = raw.trim()
        if (s.startsWith("http://", true) || s.startsWith("https://", true) || s.startsWith("file:", true)) return s
        return if (BARE_DOMAIN.matches(s)) "https://$s" else s
    }

    /** Readable link for display: no scheme, decoded umlauts, no fragment directive. */
    fun displayLink(url: String): String =
        decode(url.substringBefore(":~:").removeSuffix("#")).removePrefix("https://").removePrefix("http://").removePrefix("www.")

    /** Strips YouTube's "(3) " notification counter and " - YouTube" suffix. */
    fun cleanTitle(title: String): String? =
        title.replace(Regex("^\\(\\d+\\)\\s*"), "").removeSuffix(" - YouTube").trim().takeIf { it.isNotEmpty() }

    fun encodeQuery(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
}
