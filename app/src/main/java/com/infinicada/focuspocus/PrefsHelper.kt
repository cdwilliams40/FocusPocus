package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.serialization.encodeToString

/**
 * Helper object for handling SharedPreferences JSON persistence with kotlinx.serialization.
 */
object PrefsHelper {
    const val TAG = "PrefsHelper"

    /** Suffix under which the raw payload of an unparseable key is preserved. */
    const val CORRUPT_BACKUP_SUFFIX = "_corrupt_backup"

    inline fun <reified T> load(
        prefs: SharedPreferences,
        key: String,
        noinline onCorruption: (() -> Unit)? = null
    ): T? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            AppJson.json.decodeFromString<T>(raw)
        } catch (e: Exception) {
            handleCorruption(prefs, key, raw, e, onCorruption)
            null
        }
    }

    inline fun <reified T> loadListLenient(
        prefs: SharedPreferences,
        key: String,
        noinline onCorruption: (() -> Unit)? = null
    ): List<T>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            AppJson.decodeListLenient(raw)
        } catch (e: Exception) {
            handleCorruption(prefs, key, raw, e, onCorruption)
            null
        }
    }

    inline fun <reified T> save(
        prefs: SharedPreferences,
        key: String,
        value: T
    ) {
        val encoded = AppJson.json.encodeToString(value)
        prefs.edit { putString(key, encoded) }
    }

    fun handleCorruption(
        prefs: SharedPreferences,
        key: String,
        raw: String,
        e: Exception,
        onCorruption: (() -> Unit)?
    ) {
        Log.e(TAG, "Error parsing JSON for key $key", e)
        prefs.edit {
            putString(key + CORRUPT_BACKUP_SUFFIX, raw)
            remove(key)
        }
        onCorruption?.invoke()
    }
}
