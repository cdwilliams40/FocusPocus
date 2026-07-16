package com.infinicada.focuspocus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.BlockerRepository
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.SessionManager
import com.infinicada.focuspocus.limit.GuardActions
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.SessionCooldownManager

/**
 * Home-screen widget: the guard headline ("2 sealed · 1 pact active", or the
 * running session's state) with one-tap Cast and Seal-all actions. Everything
 * reads the same SharedPreferences stores as the app and the accessibility
 * service; the service's minute tick keeps the text fresh via [push].
 *
 * Deliberately no Dispel action: stop friction (hidden stop buttons, talisman
 * locks) must not be bypassable from the launcher.
 */
class GuardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, buildViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_SEAL_ALL -> {
                try {
                    GuardActions.sealAllPacts(prefs, gson)
                    DeviceOwnerManager.syncSuspensions(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Seal-all from widget failed", e)
                }
                push(context)
            }
            ACTION_CAST -> {
                try {
                    castFromWidget(context, prefs)
                } catch (e: Exception) {
                    Log.e(TAG, "Cast from widget failed", e)
                }
                push(context)
            }
        }
    }

    /**
     * Starts a session with the last-used enchantment selection and duration.
     * A background receiver can't launch activities, so with no castable
     * selection (the button shouldn't have been visible) this is a no-op.
     */
    private fun castFromWidget(context: Context, prefs: SharedPreferences) {
        val sessionActive = SessionManager.isSessionActive(prefs) ||
            prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null) != null
        val names = castableBlockerNames(prefs)
        if (sessionActive || names.isEmpty()) return
        SessionManager.startSession(
            sharedPreferences = prefs,
            blockerNames = names,
            durationMinutes = prefs.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0),
            breaksEnabled = prefs.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)
        )
        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)
    }

    companion object {
        private const val TAG = "GuardWidgetProvider"
        private const val ACTION_SEAL_ALL = "com.infinicada.focuspocus.widget.SEAL_ALL"
        private const val ACTION_CAST = "com.infinicada.focuspocus.widget.CAST"

        private val gson = Gson()

        /** The persisted last-used selection, filtered to enchantments that still exist. */
        private fun castableBlockerNames(prefs: SharedPreferences): List<String> {
            val stored: List<String> = run {
                val json = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
                if (json != null) {
                    try {
                        val type = object : TypeToken<List<String>>() {}.type
                        gson.fromJson<List<String>?>(json, type) ?: emptyList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing active blockers JSON", e)
                        emptyList()
                    }
                } else {
                    listOfNotNull(prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null))
                }
            }
            if (stored.isEmpty()) return emptyList()
            val existing = BlockerRepository.getBlockers(prefs).map { it.name }.toSet()
            return stored.filter { it in existing }
        }

        /** Refreshes every placed widget. Cheap, best effort, never throws. */
        fun push(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, GuardWidgetProvider::class.java)
                )
                if (ids.isEmpty()) return
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                Log.e(TAG, "Widget refresh failed", e)
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_guards)
            val now = System.currentTimeMillis()

            val sessionActive = SessionManager.isSessionActive(prefs) ||
                prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null) != null
            views.setTextViewText(R.id.widget_status, statusText(context, prefs, sessionActive, now))

            // Body tap always opens the app.
            val openIntent = Intent().setClassName(context, "com.infinicada.focuspocus.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
            )

            // Cast: only while idle with a last-used selection worth repeating
            // (during a session the status line says it all, and with nothing
            // selected a background receiver couldn't open the picker anyway).
            if (!sessionActive && castableBlockerNames(prefs).isNotEmpty()) {
                views.setViewVisibility(R.id.widget_cast_button, View.VISIBLE)
                views.setOnClickPendingIntent(
                    R.id.widget_cast_button, broadcast(context, ACTION_CAST, 1)
                )
            } else {
                views.setViewVisibility(R.id.widget_cast_button, View.GONE)
            }

            // Seal all: only when it would seal something.
            val anyUnsealed = try {
                GuardActions.anyPactUnsealed(prefs, gson, now)
            } catch (e: Exception) {
                Log.e(TAG, "anyPactUnsealed failed", e)
                false
            }
            if (anyUnsealed) {
                views.setViewVisibility(R.id.widget_seal_button, View.VISIBLE)
                views.setOnClickPendingIntent(
                    R.id.widget_seal_button, broadcast(context, ACTION_SEAL_ALL, 2)
                )
            } else {
                views.setViewVisibility(R.id.widget_seal_button, View.GONE)
            }
            return views
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, GuardWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE
            )

        private fun statusText(
            context: Context,
            prefs: SharedPreferences,
            sessionActive: Boolean,
            now: Long
        ): String {
            if (sessionActive) {
                if (prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)) {
                    return context.getString(R.string.widget_on_break)
                }
                val endMillis = prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
                if (endMillis > now) {
                    val minutesLeft = ((endMillis - now) / 60_000L).toInt() + 1
                    return context.getString(R.string.widget_session_time_left, minutesLeft)
                }
                return context.getString(R.string.widget_session_active)
            }

            val configs = AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)
            val pactManager = PactManager(prefs, gson)
            val groups = pactManager.getGroups()
            val gated = GuardStatus.pactGatedPackages(
                configs, groups, BlockerRepository.getBlockers(prefs)
            )
            if (configs.isEmpty() && gated.isEmpty()) {
                return context.getString(R.string.widget_no_guards)
            }

            val cooldowns = SessionCooldownManager(prefs, gson).peekActiveCooldowns(now).keys
            val sealed = (gated + configs.keys).count { it in cooldowns }
            val pactActive = pactManager.getActiveAllowances(now).keys.count { it in gated }
            val parts = mutableListOf<String>()
            if (sealed > 0) {
                parts += context.resources.getQuantityString(
                    R.plurals.home_guard_sealed_count, sealed, sealed
                )
            }
            if (pactActive > 0) {
                parts += context.resources.getQuantityString(
                    R.plurals.home_guard_active_count, pactActive, pactActive
                )
            }
            return if (parts.isEmpty()) {
                context.getString(R.string.home_guard_all_quiet)
            } else {
                parts.joinToString(" · ")
            }
        }
    }
}
