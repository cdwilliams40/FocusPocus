package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import java.lang.reflect.Type

/**
 * Helper object for handling SharedPreferences operations with Gson.
 */
object PrefsHelper {
    private const val TAG = "PrefsHelper"

    /**
     * Loads a JSON string from SharedPreferences and parses it into an object of type T.
     *
     * @param prefs The SharedPreferences instance.
     * @param gson The Gson instance.
     * @param key The key to retrieve.
     * @param type The Type of the object to parse.
     * @param onCorruption A callback to be invoked if parsing fails. The key will be removed from SharedPreferences before this callback is invoked.
     * @return The parsed object, or null if the key is missing or parsing fails.
     */
    fun <T> load(
        prefs: SharedPreferences,
        gson: Gson,
        key: String,
        type: Type,
        onCorruption: (() -> Unit)? = null
    ): T? {
        val json = prefs.getString(key, null)
        if (json != null) {
            try {
                return gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing JSON for key $key", e)
                prefs.edit().remove(key).apply()
                onCorruption?.invoke()
                return null
            }
        }
        return null
    }

    /**
     * Serializes an object to JSON and saves it to SharedPreferences.
     *
     * @param prefs The SharedPreferences instance.
     * @param gson The Gson instance.
     * @param key The key to save.
     * @param value The object to save.
     */
    fun <T> save(
        prefs: SharedPreferences,
        gson: Gson,
        key: String,
        value: T
    ) {
        val json = gson.toJson(value)
        prefs.edit().putString(key, json).apply()
    }
}
