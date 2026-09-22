package com.infinicada.focuspocus

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.google.gson.Gson
import com.infinicada.focuspocus.handler.TriggerHandler
import com.infinicada.focuspocus.handler.TriggerResult
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.PresetAction

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
        val preset = tilePreset()
        if (isFocusActive(prefs) || preset == null) {
            openApp()
            return
        }
        val container = (application as FocusPocusApplication).container
        // Same path as NFC taps and deep links, so every gate applies here too.
        val result = TriggerHandler(this, prefs, Gson()).togglePreset(
            preset,
            container.blockers.getBlockers(),
            container.schedules.getSchedules()
        )
        when (result) {
            is TriggerResult.Success -> {
                DndController.updateDndState(this)
                SessionNotifier.update(this)
                toast(getString(result.messageResId, *result.args.toTypedArray()))
            }
            is TriggerResult.Error ->
                toast(getString(result.messageResId, *result.args.toTypedArray()))
            else -> {}
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val active = isFocusActive(getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE))
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            active -> getString(R.string.tile_quick_spell_active)
            else -> tilePreset()?.name ?: getString(R.string.tile_quick_spell_none)
        }
        tile.updateTile()
    }

    /** The first Quick Spell that starts focus (a break-only spell can't start a session). */
    private fun tilePreset(): FocusPreset? =
        (application as FocusPocusApplication).container.presets.getPresets()
            .firstOrNull { (it.action ?: PresetAction.TOGGLE) != PresetAction.TEMP_DISABLE }

    private fun isFocusActive(prefs: SharedPreferences): Boolean =
        SessionManager.isSessionActive(prefs) ||
            prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null) != null

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
