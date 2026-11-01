package com.example.eichhorn

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/** Turns "what's on screen right now" into a bookmark. Each function returns the toast text. */
object Capturer {
    /** Quick settings tile / accessibility shortcut: the video that is playing, or the open page. */
    fun captureCurrent(context: Context): String {
        val fg = UrlReader.instance?.foreground()
        // A page with an address (Wikipedia …) only takes media info from its own app, otherwise
        // music playing in the background would end up on the Wikipedia bookmark.
        val media = MediaSessions.current(context, fg?.pkg, onlyPkg = fg?.url != null)
        if (media == null && fg?.url == null) {
            return if (!MediaSessions.hasAccess(context) && UrlReader.instance == null) {
                "Eichhorn braucht noch Berechtigungen – öffne die App."
            } else {
                "Nichts zum Merken gefunden: kein Video und kein Link im Vordergrund."
            }
        }
        val pkg = media?.pkg ?: fg!!.pkg
        var url = fg?.url?.takeIf { fg.pkg == pkg }
        if (url == null && pkg in Links.YOUTUBE_PACKAGES && Links.isYouTubeVideoId(media?.mediaId)) {
            url = "https://www.youtube.com/watch?v=${media!!.mediaId}"
        }
        val id = newBookmarkId()
        val bookmark = Bookmark(
            id = id,
            createdAt = System.currentTimeMillis(),
            url = url,
            title = media?.title?.let(Links::cleanTitle),
            author = media?.author,
            positionSec = media?.positionSec,
            durationSec = media?.durationSec,
            thumbnailUrl = media?.art?.let { saveArt(context, id, it) },
            source = appLabel(context, pkg),
            sourcePackage = pkg,
        )
        save(bookmark)
        return "Gemerkt" + (bookmark.positionSec?.let { " bei ${Links.formatClock(it)}" } ?: "") +
            (bookmark.title?.let { ": $it" } ?: "")
    }

    /** "Teilen › Merken": a link from any app. Adds the position if the sharing app is playing it. */
    fun captureShare(context: Context, text: String?, subject: String?, referrerPkg: String?): String {
        val body = text.orEmpty().trim()
        val url = Links.extractUrl(body) ?: subject?.let(Links::extractUrl)
        if (url == null && body.isEmpty()) return "Nichts empfangen."
        val rest = url?.let { body.replace(it, "") }?.trim()?.trim('"', '“', '”', '„', '«', '»')?.trim()?.ifEmpty { null }
        var title = subject?.trim()?.ifEmpty { null }
        var selection = url?.let(Links::selectionFromFragment)
        when {
            url == null -> selection = body
            selection == null && rest != null && title == null && rest.length <= 150 -> title = rest
            selection == null && rest != null && rest != title -> selection = rest
        }
        val media = MediaSessions.all(context).firstOrNull { m ->
            m.pkg == referrerPkg || (title != null && m.title != null && similar(title!!, m.title))
        }
        val id = newBookmarkId()
        val bookmark = Bookmark(
            id = id,
            createdAt = System.currentTimeMillis(),
            url = url,
            title = (title ?: media?.title)?.let(Links::cleanTitle),
            author = media?.author,
            positionSec = url?.let(Links::timeFromUrl) ?: media?.positionSec,
            durationSec = media?.durationSec,
            selectedText = selection?.take(2000),
            thumbnailUrl = media?.art?.let { saveArt(context, id, it) },
            source = appLabel(context, referrerPkg ?: media?.pkg),
            sourcePackage = referrerPkg ?: media?.pkg,
        )
        save(bookmark)
        return "Gemerkt" + (bookmark.positionSec?.let { " bei ${Links.formatClock(it)}" } ?: "")
    }

    /** Text selection menu › "Merken": the passage plus the page it is on. */
    fun captureSelection(context: Context, text: String): String {
        val passage = text.trim()
        if (passage.isEmpty()) return "Kein Text markiert."
        val fg = UrlReader.instance?.foreground()
        save(
            Bookmark(
                id = newBookmarkId(),
                createdAt = System.currentTimeMillis(),
                url = fg?.url,
                selectedText = passage.take(2000),
                source = appLabel(context, fg?.pkg),
                sourcePackage = fg?.pkg,
            ),
        )
        return if (fg?.url != null) "Textstelle gemerkt" else "Text gemerkt – ohne Link (Bedienungshilfe in Eichhorn einschalten)"
    }

    private fun save(b: Bookmark) {
        val app = EichhornApp.instance
        app.store.add(b)
        app.enricher.enrich(b.id)
    }

    private fun similar(a: String, b: String): Boolean {
        val x = a.lowercase().filter { it.isLetterOrDigit() }
        val y = b.lowercase().filter { it.isLetterOrDigit() }
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }

    /** The player's cover image as a local thumbnail (YouTube links later get the real thumbnail). */
    private fun saveArt(context: Context, id: String, art: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "art").apply { mkdirs() }
        val scaled = if (art.width > 640) Bitmap.createScaledBitmap(art, 640, 640 * art.height / art.width, true) else art
        val file = File(dir, "$id.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.toURI().toString()
    }.getOrNull()
}
