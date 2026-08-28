package com.infinicada.focuspocus

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.infinicada.focuspocus.handler.TriggerHandler
import com.infinicada.focuspocus.handler.TriggerResult

/**
 * Casting and dispelling from the notification shade.
 *
 * The tile casts the first Quick Spell and dispels a running session — but it
 * is emphatically not a back door. [DispelPolicy] decides whether a one-tap
 * stop is allowed at all, so talisman lock, hide-stop-button and a
 * talisman-bound ritual hold here exactly as they do on the dashboard; when
 * they do, the tile opens the app instead of silently doing nothing, because
 * the app is where the reason can be explained.
 *
 * Casting funnels through [TriggerHandler.togglePreset], the same path NFC and
 * deep links use, so its locks and its session-recording behaviour come along
 * for free.
 */
class FocusTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val container = (application as FocusPocusApplication).container

        try {
            if (SessionManager.isSessionActive(prefs)) {
                dispel(prefs, gson, container)
            } else {
                cast(prefs, gson, container)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tile click failed", e)
        }
        refreshTile()
    }

    private fun dispel(
        prefs: android.content.SharedPreferences,
        gson: Gson,
        container: com.infinicada.focuspocus.data.AppContainer
    ) {
        val activeScheduleId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val ritualRequiresTalisman = activeScheduleId != null &&
            container.schedules.getSchedules()
                .find { it.id == activeScheduleId }?.unbindingTalismanId != null

        val allowed = DispelPolicy.canStopInOneTap(
            nfcLockMode = prefs.getBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, false),
            hideStopButton = prefs.getBoolean(Constants.PrefsKeys.HIDE_STOP_BUTTON, false),
            focusDurationMinutes = prefs.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0),
            ritualRequiresTalisman = ritualRequiresTalisman
        )
        if (!allowed) {
            // The session is deliberately hard to end. Hand the user to the app,
            // which explains which lock is holding rather than leaving a tap
            // that appears to do nothing.
            openApp()
            return
        }
        SessionManager.stopSession(this, prefs, gson)
        toast(getString(R.string.tile_dispelled))
    }

    private fun cast(
        prefs: android.content.SharedPreferences,
        gson: Gson,
        container: com.infinicada.focuspocus.data.AppContainer
    ) {
        val preset = container.presets.getPresets().firstOrNull()
        if (preset == null) {
            openApp()
            return
        }
        val result = TriggerHandler(this, prefs, gson).togglePreset(
            preset,
            container.blockers.getBlockers(),
            container.schedules.getSchedules()
        )
        when (result) {
            is TriggerResult.Success -> toast(getString(result.messageResId, *result.args.toTypedArray()))
            is TriggerResult.Error -> toast(getString(result.messageResId, *result.args.toTypedArray()))
            else -> {}
        }
    }

    /**
     * Repaints the tile from persisted session state. Called on every listen
     * and after every click, because the session can also start or end from
     * a ritual, a talisman, or the timer running out.
     */
    private fun refreshTile() {
        val tile: Tile = qsTile ?: return
        try {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val active = SessionManager.isSessionActive(prefs) ||
                prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null) != null
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label)
            tile.contentDescription = getString(
                if (active) R.string.tile_state_active else R.string.tile_state_inactive
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(
                    if (active) R.string.tile_state_active else R.string.tile_state_inactive
                )
            }
            tile.icon = Icon.createWithResource(this, R.mipmap.fplogo_round)
            tile.updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Could not refresh the tile", e)
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the app from the tile", e)
        }
    }

    private fun toast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Toast failed", e)
        }
    }

    private companion object {
        const val TAG = "FocusTileService"
    }
}
