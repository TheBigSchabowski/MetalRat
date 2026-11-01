package com.example.metalrat

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** The "Merken" tile in the quick settings panel (swipe down, next to Wi-Fi and screen recording). */
class MerkenTile : TileService() {
    override fun onTileAdded() {
        Prefs.setTileAdded(this)
    }

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = null
            updateTile()
        }
    }

    override fun onClick() {
        val message = Capturer.captureCurrent(this)
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "✓ gemerkt"
            updateTile()
        }
        // Closes the panel (Android 12+, needs the accessibility service); otherwise it stays open.
        UrlReader.instance?.dismissShade()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("metalrat", Context.MODE_PRIVATE)
    fun tileAdded(context: Context) = prefs(context).getBoolean("tile_added", false)
    fun setTileAdded(context: Context) = prefs(context).edit().putBoolean("tile_added", true).apply()
}
