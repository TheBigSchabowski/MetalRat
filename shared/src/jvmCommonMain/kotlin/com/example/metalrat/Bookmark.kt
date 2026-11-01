package com.example.metalrat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID

/** One saved moment: a page, optionally at a video position or at a text passage. */
@Serializable
data class Bookmark(
    val id: String,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val url: String? = null,
    val title: String? = null,
    /** Channel / artist, if the player reported one. */
    val author: String? = null,
    /** Video position in seconds when it was saved. */
    val positionSec: Long? = null,
    val durationSec: Long? = null,
    /** Text the user had selected: opening the bookmark scrolls there and highlights it. */
    val selectedText: String? = null,
    /** http(s) URL or file:// path; when it can't be loaded the card shows the link instead. */
    val thumbnailUrl: String? = null,
    /** Human-readable origin, e.g. "YouTube", "Chrome · Mac". */
    val source: String? = null,
    /** Android package that was in front when it was saved. */
    val sourcePackage: String? = null,
    val note: String? = null,
)

fun newBookmarkId(): String = UUID.randomUUID().toString()

@Serializable
data class ExportFile(
    val app: String = "MetalRat",
    val version: Int = 1,
    val exportedAt: Long,
    val bookmarks: List<Bookmark>,
)

object BookmarkJson {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(bookmarks: List<Bookmark>): String =
        json.encodeToString(ExportFile.serializer(), ExportFile(exportedAt = System.currentTimeMillis(), bookmarks = bookmarks))

    /** Accepts an export file or a bare JSON array of bookmarks. */
    fun parse(text: String): List<Bookmark> {
        val element = json.parseToJsonElement(text)
        return if (element is JsonArray) json.decodeFromJsonElement(element)
        else json.decodeFromJsonElement<ExportFile>(element).bookmarks
    }
}
