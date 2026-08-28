package com.infinicada.focuspocus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.google.gson.Gson
import com.infinicada.focuspocus.limit.SessionCooldownManager

/**
 * The home-screen glance: what is holding right now, without opening the app.
 *
 * Built on RemoteViews rather than Glance deliberately — two text views and a
 * tap target do not justify a Compose runtime in the widget process, and the
 * widget has to render on the launcher's terms anyway.
 *
 * Refreshed from three places: the system's own half-hourly poll (the floor
 * Android allows), [refresh] on the enforcement engine's minute tick so a
 * countdown is never more than a minute stale, and whenever the launcher asks.
 */
class FocusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        try {
            val snapshot = snapshot(context)
            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            views.setTextViewText(R.id.widget_headline, headline(context, snapshot))
            views.setTextViewText(R.id.widget_detail, detail(context, snapshot))
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            manager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
            // A widget that throws is a widget the launcher stops drawing.
            Log.e(TAG, "Could not render the widget", e)
        }
    }

    private fun headline(context: Context, snapshot: FocusWidgetState.Snapshot): String =
        when (snapshot.kind) {
            FocusWidgetState.Kind.FOCUSING -> context.getString(R.string.widget_focusing)
            FocusWidgetState.Kind.ON_BREAK -> context.getString(R.string.widget_on_break)
            FocusWidgetState.Kind.SEALED -> context.resources.getQuantityString(
                R.plurals.widget_sealed, snapshot.sealedCount, snapshot.sealedCount
            )
            FocusWidgetState.Kind.IDLE -> context.getString(R.string.widget_idle)
        }

    private fun detail(context: Context, snapshot: FocusWidgetState.Snapshot): String =
        when (snapshot.kind) {
            FocusWidgetState.Kind.FOCUSING, FocusWidgetState.Kind.ON_BREAK ->
                if (snapshot.minutesRemaining > 0) {
                    context.resources.getQuantityString(
                        R.plurals.widget_minutes_left,
                        snapshot.minutesRemaining,
                        snapshot.minutesRemaining
                    )
                } else {
                    context.getString(R.string.widget_untimed)
                }
            FocusWidgetState.Kind.SEALED -> context.getString(R.string.widget_sealed_detail)
            FocusWidgetState.Kind.IDLE -> context.getString(R.string.widget_idle_detail)
        }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        private const val TAG = "FocusWidgetProvider"

        private fun snapshot(context: Context): FocusWidgetState.Snapshot {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val sealed = SessionCooldownManager(prefs, Gson()).peekActiveCooldowns().size
            return FocusWidgetState.of(
                sessionActive = SessionManager.isSessionActive(prefs) ||
                    prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null) != null,
                onBreak = prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false),
                focusEndMillis = prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L),
                breakEndMillis = prefs.getLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, 0L),
                sealedCount = sealed
            )
        }

        /**
         * Repaints every placed widget. Cheap and safe to call on a timer: it
         * returns before touching any store when the user has placed none,
         * which is the common case.
         */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, FocusWidgetProvider::class.java)
                )
                if (ids.isEmpty()) return
                val provider = FocusWidgetProvider()
                ids.forEach { provider.render(context, manager, it) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not refresh widgets", e)
            }
        }
    }
}
