package com.example.metalrat

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinksTest {
    private fun bm(url: String?, pos: Long? = null, text: String? = null) =
        Bookmark(id = "x", createdAt = 0, url = url, positionSec = pos, selectedText = text)

    @Test fun youTubeIds() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id&t=10s",
            "https://m.youtube.com/watch?feature=share&v=$id",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://music.youtube.com/watch?v=$id&list=RD",
            "youtube.com/watch?v=$id".let { "https://$it" },
        ).forEach { assertEquals(id, Links.youTubeId(it), it) }
        assertNull(Links.youTubeId("https://www.youtube.com/"))
        assertNull(Links.youTubeId("https://example.com/watch?v=$id"))
    }

    @Test fun youTubeOpensAtTime() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=83s",
            Links.openUrl(bm("https://youtu.be/dQw4w9WgXcQ?si=abc&t=5", pos = 83)),
        )
    }

    @Test fun otherSitesGetTheirTimeFormat() {
        assertEquals("https://vimeo.com/123#t=90s", Links.openUrl(bm("https://vimeo.com/123", 90)))
        assertEquals("https://www.twitch.tv/videos/1?t=1h1m1s", Links.openUrl(bm("https://www.twitch.tv/videos/1", 3661)))
        assertEquals("https://example.com/a.mp4#t=12", Links.openUrl(bm("https://example.com/a.mp4", 12)))
        assertEquals("https://example.com/page", Links.openUrl(bm("https://example.com/page", 12)))
    }

    @Test fun textFragments() {
        assertEquals(
            "https://de.wikipedia.org/wiki/Eichh%C3%B6rnchen#:~:text=Das%20Eichh%C3%B6rnchen",
            Links.openUrl(bm("https://de.wikipedia.org/wiki/Eichh%C3%B6rnchen", text = "Das  Eichhörnchen")),
        )
        // Characters with meaning in text directives are escaped.
        assertEquals("https://x.org/#:~:text=a%2Db%2C%20c%26d", Links.withTextFragment("https://x.org/", "a-b, c&d"))
        // Existing anchor is kept.
        assertEquals("https://x.org/p#Abschnitt:~:text=Wort", Links.withTextFragment("https://x.org/p#Abschnitt", "Wort"))
        // Long passages use start,end.
        val long = (1..20).joinToString(" ") { "w$it" }
        assertEquals("https://x.org/#:~:text=w1%20w2%20w3%20w4,w17%20w18%20w19%20w20", Links.withTextFragment("https://x.org/", long))
        // A link that already has a fragment directive isn't touched.
        assertEquals("https://x.org/#:~:text=a", Links.openUrl(bm("https://x.org/#:~:text=a", text = "a")))
    }

    @Test fun selectionFromSharedHighlight() {
        assertEquals("Das Eichhörnchen", Links.selectionFromFragment("https://x.org/#:~:text=Das%20Eichh%C3%B6rnchen"))
        assertEquals("Anfang … Ende", Links.selectionFromFragment("https://x.org/#:~:text=vor-,Anfang,Ende,-nach"))
        assertNull(Links.selectionFromFragment("https://x.org/#top"))
        // Round trip through our own encoder.
        val text = "a-b, c&d (ü)"
        assertEquals(text, Links.selectionFromFragment(Links.withTextFragment("https://x.org/", text)))
    }

    @Test fun timeParsing() {
        assertEquals(83, Links.timeFromUrl("https://youtu.be/dQw4w9WgXcQ?t=83"))
        assertEquals(83, Links.timeFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1m23s"))
        assertEquals(90, Links.timeFromUrl("https://vimeo.com/1#t=90s"))
        assertNull(Links.timeFromUrl("https://example.com/"))
        assertEquals(3723, Links.parseTime("1:02:03"))
        assertEquals(754, Links.parseTime("12:34"))
        assertEquals(3600, Links.parseTime("1h"))
        assertNull(Links.parseTime("abc"))
        assertEquals("1:02:03", Links.formatClock(3723))
        assertEquals("0:07", Links.formatClock(7))
    }

    @Test fun urlsInSharedText() {
        assertEquals("https://de.wikipedia.org/wiki/Java_(Insel)",
            Links.extractUrl("Schau mal (https://de.wikipedia.org/wiki/Java_(Insel))."))
        assertEquals("https://youtu.be/abc", Links.extractUrl("Titel\nhttps://youtu.be/abc"))
        assertEquals("https://en.wikipedia.org/wiki/X", Links.normalizeUrl("en.wikipedia.org/wiki/X"))
        assertEquals("Titel", Links.cleanTitle("(3) Titel - YouTube"))
        assertEquals("de.wikipedia.org/wiki/Eichhörnchen", Links.displayLink("https://de.wikipedia.org/wiki/Eichh%C3%B6rnchen#:~:text=x"))
    }

    @Test fun captureFromExtension() {
        val b = CaptureRequest(
            id = "12345678-abcd", url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", title = "(2) Song - YouTube",
            time = 61.7, duration = Double.POSITIVE_INFINITY, image = "https://stale.example/og.jpg",
        ).toBookmark()
        assertEquals("12345678-abcd", b.id)
        assertEquals("Song", b.title)
        assertEquals(61, b.positionSec)
        assertNull(b.durationSec)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", b.thumbnailUrl)
    }

    @Test fun pageMeta() {
        val html = """<html><head><title>T &amp; U</title>
            <meta content="/img/a.png" property="og:image"><meta name='twitter:image' content='x'></head>"""
        val meta = PageMeta.parse("https://example.com/wiki/page", html)
        assertEquals("T & U", meta.title)
        assertEquals("https://example.com/img/a.png", meta.image)
    }

    @Test fun storeRoundTripAndImport() {
        val dir = createTempDirectory("metalrat").toFile()
        val file = File(dir, "bookmarks.json")
        val store = BookmarkStore(file)
        store.add(Bookmark(id = "a", createdAt = 1, url = "https://a.org"))
        store.add(Bookmark(id = "b", createdAt = 2, url = "https://b.org", positionSec = 5))
        assertEquals(listOf("b", "a"), store.items.value.map { it.id })

        val reopened = BookmarkStore(file)
        assertEquals(store.items.value, reopened.items.value)

        val other = BookmarkStore(File(dir, "other.json"))
        other.add(Bookmark(id = "a", createdAt = 1, url = "https://changed.org"))
        other.add(Bookmark(id = "c", createdAt = 3, title = "neu"))
        assertEquals(1, store.importJson(other.exportJson()))
        assertEquals(listOf("c", "b", "a"), store.items.value.map { it.id })
        assertEquals("https://a.org", store.get("a")?.url)
        assertTrue(file.readText().contains("\"app\": \"MetalRat\""))
        dir.deleteRecursively()
    }
}

class SpacedRepetitionTest {
    private val min = 60_000L
    private val hour = 60 * min

    @Test fun intervalsCountFromTheEndOfPractice() {
        val t0 = 1_000_000_000_000L
        val first = SpacedRepetition.practiced(null, minutes = 5, now = t0)
        assertEquals(1, first.count)
        assertEquals(t0 + 5 * min + 45 * min, first.due)
        val second = SpacedRepetition.practiced(first, minutes = 120, now = first.due)
        assertEquals(first.due + 2 * hour + 2 * hour, second.due)
        // Climbs the ladder and then stays at a month.
        var p = second
        repeat(10) { p = SpacedRepetition.practiced(p, 5, p.due) }
        assertEquals(30 * 24 * hour + 5 * min, p.due - p.lastAt)
    }

    @Test fun labels() {
        val now = 1_000_000_000_000L
        fun label(inMs: Long) = SpacedRepetition.dueLabel(Practice(1, now, 5, now + inMs), now)
        assertEquals("fällig", label(-1))
        assertEquals("in 45 min", label(45 * min))
        assertEquals("in 2 h 5 min", label(2 * hour + 5 * min))
        assertEquals("in 3 Tagen", label(3 * 24 * hour))
        assertEquals(10, SpacedRepetition.lastMinutes(listOf(
            Bookmark("a", 0, practice = Practice(1, 5, 30, 0)),
            Bookmark("b", 0, practice = Practice(1, 9, 10, 0)),
        )))
    }
}
