package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

/**
 * Brings stored data up to the schema this build expects. Every step is keyed
 * on [Constants.PrefsKeys.SCHEMA_VERSION] and safe to run twice: a step that
 * dies halfway re-runs on the next process start, and backup import runs the
 * same steps over restored data.
 *
 * Add a step by appending to [steps]; never reorder or remove one.
 */
object Migrations {
    private const val TAG = "Migrations"

    /** Prefs keys of features removed in 2.0. Kept here, not in Constants, so
     *  nothing else can start using them again. */
    internal object LegacyKeys {
        const val BOONS = "boons"
        const val UNLOCKED_SIGILS = "unlockedSigils"
        const val HIGHEST_STREAK_MILESTONE_PAID = "highestStreakMilestonePaid"
        const val LAST_SESSION_RECORDED_DATE = "lastSessionRecordedDate"
        const val LAST_WRAPUP_DATE = "lastWrapupDate"
        const val WRAPUP_ENABLED = "wrapupEnabled"
    }

    private val steps: List<(SharedPreferences) -> Unit> = listOf(
        // 1: boons, sigils, streak milestones and the daily wrap-up are gone.
        { prefs ->
            prefs.edit {
                remove(LegacyKeys.BOONS)
                remove(LegacyKeys.UNLOCKED_SIGILS)
                remove(LegacyKeys.HIGHEST_STREAK_MILESTONE_PAID)
                remove(LegacyKeys.LAST_SESSION_RECORDED_DATE)
                remove(LegacyKeys.LAST_WRAPUP_DATE)
                remove(LegacyKeys.WRAPUP_ENABLED)
            }
        }
    )

    val CURRENT_VERSION: Int get() = steps.size

    @Synchronized
    fun run(prefs: SharedPreferences) {
        val from = prefs.getInt(Constants.PrefsKeys.SCHEMA_VERSION, 0)
        if (from >= steps.size) return
        for (version in from until steps.size) {
            steps[version](prefs)
            // commit, not apply: a later step must never run over data whose
            // earlier step isn't on disk yet.
            prefs.edit(commit = true) { putInt(Constants.PrefsKeys.SCHEMA_VERSION, version + 1) }
            Log.i(TAG, "Migrated prefs to schema ${version + 1}")
        }
    }
}
