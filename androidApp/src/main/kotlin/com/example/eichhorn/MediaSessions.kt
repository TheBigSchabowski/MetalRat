package com.example.eichhorn

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Exists only so the user can grant notification access: with it, Eichhorn may read the active
 * media sessions (title, position) of YouTube, browsers and other players. It reads no notifications.
 */
class MediaListener : NotificationListenerService()

/** What a player reports right now. */
data class NowPlaying(
    val pkg: String,
    val title: String?,
    val author: String?,
    val positionSec: Long?,
    val durationSec: Long?,
    val mediaId: String?,
    val art: android.graphics.Bitmap?,
)

object MediaSessions {
    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun controllers(context: Context): List<MediaController> = try {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaListener::class.java))
    } catch (e: SecurityException) {
        emptyList()
    }

    /** The session of [pkg], or — if [pkg] has none and [onlyPkg] is false — the playing one. */
    fun current(context: Context, pkg: String?, onlyPkg: Boolean = false): NowPlaying? {
        val all = controllers(context)
        val playing = { c: MediaController -> c.playbackState?.state == PlaybackState.STATE_PLAYING }
        val chosen = all.firstOrNull { it.packageName == pkg && playing(it) }
            ?: all.firstOrNull { it.packageName == pkg }
            ?: if (onlyPkg) null else all.firstOrNull(playing)
        return chosen?.let(::read)
    }

    /** Every active session, for matching a shared link to the video it came from. */
    fun all(context: Context): List<NowPlaying> = controllers(context).map(::read)

    private fun read(c: MediaController): NowPlaying {
        val md = c.metadata
        return NowPlaying(
            pkg = c.packageName,
            title = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            author = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            positionSec = position(c.playbackState),
            durationSec = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }?.div(1000),
            mediaId = md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
        )
    }

    /** The reported position plus the time it has been playing since that report. */
    private fun position(state: PlaybackState?): Long? {
        state ?: return null
        var ms = state.position
        if (ms < 0) return null
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            ms += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        return (ms / 1000).takeIf { it > 0 }
    }
}
