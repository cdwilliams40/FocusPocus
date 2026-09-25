package com.infinicada.focuspocus

import android.content.Context
import com.google.gson.Gson
import com.infinicada.focuspocus.handler.TriggerHandler
import com.infinicada.focuspocus.handler.TriggerResult
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.PresetAction

/**
 * One-tap Quick Spell casting shared by the Quick Settings tile and the
 * home-screen widget. Both only ever *start* focus — callers check
 * [SessionManager.isFocusActive] first and open the app instead while a
 * session runs, so neither surface becomes a shortcut around a hidden stop
 * button or a talisman lock.
 */
object QuickSpellCaster {

    /** The first Quick Spell that starts focus (a break-only spell can't start a session). */
    fun castablePreset(presets: List<FocusPreset>): FocusPreset? =
        presets.firstOrNull { (it.action ?: PresetAction.TOGGLE) != PresetAction.TEMP_DISABLE }

    fun castablePreset(context: Context): FocusPreset? =
        castablePreset((context.applicationContext as FocusPocusApplication).container.presets.getPresets())

    /**
     * Casts [preset] through the same TriggerHandler path as NFC taps and deep
     * links, so every gate applies. Returns the message to show the user, or
     * null when the trigger had nothing to say.
     */
    fun cast(context: Context, preset: FocusPreset): String? {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val container = (context.applicationContext as FocusPocusApplication).container
        val result = TriggerHandler(context, prefs, Gson()).togglePreset(
            preset,
            container.blockers.getBlockers(),
            container.schedules.getSchedules()
        )
        return when (result) {
            is TriggerResult.Success -> {
                DndController.updateDndState(context)
                SessionNotifier.update(context)
                context.getString(result.messageResId, *result.args.toTypedArray())
            }
            is TriggerResult.Error ->
                context.getString(result.messageResId, *result.args.toTypedArray())
            else -> null
        }
    }
}
