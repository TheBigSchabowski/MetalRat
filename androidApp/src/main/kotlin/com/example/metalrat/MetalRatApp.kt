package com.example.metalrat

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** One store per process, shared by the activity, the tile, the share target and the accessibility service. */
class MetalRatApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { BookmarkStore(File(filesDir, "bookmarks.json")) }
    val enricher by lazy { Enricher(store, scope) }
    val thumbs by lazy { ThumbnailCache(File(cacheDir, "thumbs"), ::decodeThumbnail) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ShareShortcut.publish(this)
    }

    companion object {
        lateinit var instance: MetalRatApp
            private set
    }
}

/** Decodes with downsampling: og:images are often 1200 px+, cards need ~600. */
private fun decodeThumbnail(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 600) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

fun appLabel(context: Context, pkg: String?): String? {
    pkg ?: return null
    if (pkg in Links.YOUTUBE_PACKAGES && pkg != "com.google.android.apps.youtube.music") return "YouTube"
    return try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
