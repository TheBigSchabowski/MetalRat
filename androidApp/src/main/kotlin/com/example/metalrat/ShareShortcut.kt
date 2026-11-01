package com.example.metalrat

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * A long-lived sharing shortcut: Android shows it in the Direct Share row at the top of the share
 * sheet. Reporting each use keeps it ranked near the front.
 */
object ShareShortcut {
    private const val ID = "merken"
    private const val CATEGORY = "com.example.metalrat.category.MERKEN"

    fun publish(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(R.string.action_label))
            .setLongLabel("In MetalRat merken")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND))
            .setCategories(setOf(CATEGORY))
            .setLongLived(true)
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }

    fun reportUsed(context: Context) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, ID) }
    }
}
