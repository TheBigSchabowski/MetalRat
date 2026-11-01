package com.example.metalrat

import kotlinx.serialization.Serializable

/** What the browser extension posts to the desktop app (see browser-extension/background.js). */
@Serializable
data class CaptureRequest(
    /** Chosen by the extension, so a retried delivery replaces instead of duplicating. */
    val id: String? = null,
    val url: String? = null,
    val title: String? = null,
    /** video.currentTime in seconds. */
    val time: Double? = null,
    val duration: Double? = null,
    val selection: String? = null,
    val image: String? = null,
    val source: String? = null,
    val capturedAt: Long? = null,
)

fun CaptureRequest.toBookmark(): Bookmark {
    val url = url?.trim()?.takeIf { it.isNotEmpty() }
    val videoId = url?.let(Links::youTubeId)
    return Bookmark(
        id = id?.takeIf { it.length in 8..64 } ?: newBookmarkId(),
        createdAt = capturedAt ?: System.currentTimeMillis(),
        url = url,
        title = title?.let(Links::cleanTitle),
        positionSec = time?.takeIf { it >= 1 }?.toLong() ?: url?.let(Links::timeFromUrl),
        durationSec = duration?.takeIf { it.isFinite() && it > 0 }?.toLong(),
        selectedText = selection?.trim()?.takeIf { it.isNotEmpty() }?.take(2000) ?: url?.let(Links::selectionFromFragment),
        // YouTube's og:image goes stale while navigating inside the site; the video ID doesn't.
        thumbnailUrl = if (videoId != null) Links.youTubeThumbnail(videoId) else image?.takeIf { it.startsWith("http") },
        source = source,
    )
}
