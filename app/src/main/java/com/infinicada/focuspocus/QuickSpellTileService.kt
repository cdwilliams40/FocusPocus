package com.infinicada.focuspocus

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * Quick Settings tile that casts the user's first Quick Spell without opening
 * the app. It only ever *starts* focus: while a session runs (manual, ritual
 * or talisman) a tap opens the app instead, so the shade never becomes a
 * shortcut around a hidden stop button or a talisman lock.
 */
class QuickSpellTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val preset = QuickSpellCaster.castablePreset(this)
        if (SessionManager.isFocusActive(prefs) || preset == null) {
            openApp()
            return
        }
        QuickSpellCaster.cast(this, preset)?.let { toast(it) }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val active = SessionManager.isFocusActive(getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE))
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            active -> getString(R.string.tile_quick_spell_active)
            else -> QuickSpellCaster.castablePreset(this)?.name ?: getString(R.string.tile_quick_spell_none)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }

    /**
     * Pre-Android 14 path only: the PendingIntent overload doesn't exist
     * there, and the Intent overload only throws on API 34+, which
     * [openApp] routes around.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
