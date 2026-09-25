package com.infinicada.focuspocus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.google.gson.Gson
import com.infinicada.focuspocus.data.BlockerListRepository
import com.infinicada.focuspocus.data.InsightsRepository
import com.infinicada.focuspocus.data.PresetRepository
import com.infinicada.focuspocus.limit.GuardHeadline
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.SessionCooldownManager
import java.util.concurrent.Executors

/**
 * Home-screen status widget: the Quick Spell caster (a live countdown while a
 * session runs), the Pacts dashboard headline, and streak/mana.
 *
 * Refreshing follows [SessionNotifier]'s model: [attach] registers a
 * debounced SharedPreferences listener on every key the widget reads, so all
 * mutation paths refresh it without per-call-site wiring, and the
 * accessibility service's minute tick calls [update] so seals lifting and
 * daily limits running out show up without any prefs write. The countdown
 * itself is a RemoteViews Chronometer, which the launcher ticks with no
 * polling from the app. Nothing here is allowed to throw.
 *
 * The widget only ever *starts* focus, same contract as the Quick Settings
 * tile: while a session runs the Cast button is gone and a tap opens the app.
 */
object StatusWidget {
    private const val TAG = "StatusWidget"
    private const val UPDATE_DEBOUNCE_MS = 200L

    /** Below this height (dp) only the spell/session row fits. */
    private const val COMPACT_MAX_HEIGHT_DP = 100

    /** Everything the widget shows, beyond the session keys. */
    private val WATCHED_KEYS = SessionNotifier.WATCHED_KEYS + setOf(
        Constants.PrefsKeys.FOCUS_PRESETS,
        Constants.PrefsKeys.BLOCKER_LISTS,
        Constants.PrefsKeys.APP_TIME_LIMIT_CONFIGS,
        Constants.PrefsKeys.APP_COOLDOWN_STATES,
        Constants.PrefsKeys.PACT_ALLOWANCES,
        Constants.PrefsKeys.PACT_GROUPS,
        Constants.PrefsKeys.FOCUS_SESSIONS,
        Constants.PrefsKeys.PROGRESSION_ENABLED,
        Constants.PrefsKeys.MANA_BALANCE
    )

    /** What the widget should show. */
    data class State(
        /** The running session, or null when idle. */
        val session: SessionNotifier.CountdownState?,
        /** The spell the Cast button would start; null when none is castable. */
        val quickSpellName: String?,
        /** Dashboard headline counts; null when no guards exist yet. */
        val headline: GuardHeadline?,
        val streak: Int,
        /** Mana balance; null when progression is turned off. */
        val manaBalance: Long?
    )

    private val gson = Gson()

    // Resolving reads JSON and may query UsageStats, so it stays off the main
    // thread; a single thread also keeps overlapping refreshes in order.
    private val executor = Executors.newSingleThreadExecutor()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var appContext: Context? = null
    private val updateRunnable = Runnable { appContext?.let { update(it) } }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in WATCHED_KEYS) {
            handler.removeCallbacks(updateRunnable)
            handler.postDelayed(updateRunnable, UPDATE_DEBOUNCE_MS)
        }
    }

    /** Starts observing app state. Called once from Application.onCreate. */
    fun attach(context: Context) {
        appContext = context.applicationContext
        try {
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register widget state listener", e)
        }
        update(context)
    }

    /**
     * Re-renders every placed widget in the background. A cheap no-op when
     * none are placed. [onDone] always runs, for goAsync callers.
     */
    fun update(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        try {
            executor.execute {
                try {
                    render(app)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update widget", e)
                } finally {
                    onDone()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule widget update", e)
            onDone()
        }
    }

    private fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val state = resolveState(prefs, gson, System.currentTimeMillis()) {
            AppTimeLimitManager.getAllUsedMinutesToday(context)
        }
        ids.forEach { id ->
            val compact = isCompact(manager.getAppWidgetOptions(id))
            manager.updateAppWidget(id, buildViews(context, state, compact))
        }
    }

    /**
     * Derives the widget content from persisted state. Pure with respect to
     * Android services (UsageStats comes in through [usedToday]) so unit tests
     * can drive it directly.
     */
    fun resolveState(
        prefs: SharedPreferences,
        gson: Gson,
        nowMillis: Long,
        usedToday: () -> Map<String, Int>
    ): State {
        val insights = InsightsRepository(prefs, gson)
        val pactManager = PactManager(prefs, gson)
        val configs = insights.getAppTimeLimitConfigs()
        val groups = pactManager.getGroups()
        val blockers = BlockerListRepository(prefs, gson).getBlockers()
        val liveStates = GuardStatus.liveStates(
            configs = configs,
            groups = groups,
            blockers = blockers,
            cooldownExpiries = SessionCooldownManager(prefs, gson).peekActiveCooldowns(nowMillis)
                .mapValues { it.value.cooldownExpiryMillis },
            allowances = pactManager.getActiveAllowances(nowMillis),
            usedToday = usedToday
        )
        val rows = GuardStatus.buildRows(
            configs, groups, blockers, liveStates,
            openStats = emptyMap(), names = emptyMap(), now = nowMillis
        )
        return State(
            session = SessionNotifier.resolveState(prefs, gson, nowMillis),
            quickSpellName = QuickSpellCaster.castablePreset(PresetRepository(prefs, gson).getPresets())?.name,
            headline = if (rows.isEmpty()) null else GuardStatus.headlineCounts(rows),
            streak = calculateCurrentStreak(insights.getFocusSessions()),
            manaBalance = if (Progression.isEnabled(prefs)) {
                prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L)
            } else null
        )
    }

    private fun isCompact(options: Bundle?): Boolean {
        // Portrait max height: the taller of the two orientations' reports.
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        return height in 1 until COMPACT_MAX_HEIGHT_DP
    }

    fun buildViews(context: Context, state: State, compact: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_status)

        val openApp = Intent().setClassName(context, "com.infinicada.focuspocus.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, 0, openApp, PendingIntent.FLAG_IMMUTABLE)
        )

        val session = state.session
        if (session != null) {
            bindSession(context, views, session)
            views.setViewVisibility(R.id.widget_cast, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_chronometer, View.GONE)
            views.setChronometer(R.id.widget_chronometer, 0L, null, false)
            if (state.quickSpellName != null) {
                views.setTextViewText(R.id.widget_title, state.quickSpellName)
                views.setTextViewText(R.id.widget_status, context.getString(R.string.tile_quick_spell_label))
                views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                views.setViewVisibility(R.id.widget_cast, View.VISIBLE)
                val cast = Intent(context, StatusWidgetCastReceiver::class.java)
                views.setOnClickPendingIntent(
                    R.id.widget_cast,
                    PendingIntent.getBroadcast(context, 0, cast, PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.tile_quick_spell_none))
                views.setViewVisibility(R.id.widget_status, View.GONE)
                views.setViewVisibility(R.id.widget_cast, View.GONE)
            }
        }

        val headline = state.headline
        if (!compact && headline != null) {
            views.setTextViewText(R.id.widget_headline, headlineText(context, headline))
            views.setViewVisibility(R.id.widget_headline, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_headline, View.GONE)
        }

        val progress = listOfNotNull(
            if (state.streak > 0) {
                context.resources.getQuantityString(R.plurals.home_day_streak, state.streak, state.streak)
            } else null,
            state.manaBalance?.let { context.getString(R.string.mana_chip, it) }
        )
        if (!compact && progress.isNotEmpty()) {
            views.setTextViewText(R.id.widget_progress, progress.joinToString(" · "))
            views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_progress, View.GONE)
        }
        return views
    }

    private fun bindSession(context: Context, views: RemoteViews, session: SessionNotifier.CountdownState) {
        views.setTextViewText(
            R.id.widget_title,
            when {
                session.onBreak -> context.getString(R.string.focus_session_notification_break)
                else -> session.sessionName ?: context.getString(R.string.tile_quick_spell_active)
            }
        )
        val now = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        when {
            session.countdownEndMillis != null -> {
                val format = context.getString(
                    if (session.onBreak) R.string.widget_break_time_left else R.string.widget_focus_time_left
                )
                views.setChronometer(
                    R.id.widget_chronometer, elapsedNow + (session.countdownEndMillis - now), format, true
                )
                views.setChronometerCountDown(R.id.widget_chronometer, true)
                views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
                views.setViewVisibility(R.id.widget_status, View.GONE)
            }
            session.countUpStartMillis != null -> {
                views.setChronometer(
                    R.id.widget_chronometer,
                    elapsedNow - (now - session.countUpStartMillis),
                    context.getString(R.string.widget_focus_elapsed),
                    true
                )
                views.setChronometerCountDown(R.id.widget_chronometer, false)
                views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
                views.setViewVisibility(R.id.widget_status, View.GONE)
            }
            else -> {
                // Talisman sessions have no start anchor: no clock, just the state.
                views.setChronometer(R.id.widget_chronometer, 0L, null, false)
                views.setViewVisibility(R.id.widget_chronometer, View.GONE)
                views.setTextViewText(
                    R.id.widget_status,
                    context.getString(
                        if (session.onBreak) R.string.focus_session_notification_break
                        else R.string.tile_quick_spell_active
                    )
                )
                views.setViewVisibility(R.id.widget_status, View.VISIBLE)
            }
        }
    }

    /** Same wording as the Pacts dashboard headline. */
    private fun headlineText(context: Context, headline: GuardHeadline): String {
        val res = context.resources
        val parts = listOfNotNull(
            if (headline.sealedCount > 0) {
                res.getQuantityString(R.plurals.home_guard_sealed_count, headline.sealedCount, headline.sealedCount)
            } else null,
            if (headline.pactActiveCount > 0) {
                res.getQuantityString(
                    R.plurals.home_guard_active_count, headline.pactActiveCount, headline.pactActiveCount
                )
            } else null,
            if (headline.overLimitCount > 0) {
                res.getQuantityString(R.plurals.home_guard_over_count, headline.overLimitCount, headline.overLimitCount)
            } else null
        )
        return if (parts.isEmpty()) context.getString(R.string.home_guard_all_quiet) else parts.joinToString(" · ")
    }
}

class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        StatusWidget.update(context) { pending.finish() }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Resizing across the compact threshold adds or drops the lower rows.
        val pending = goAsync()
        StatusWidget.update(context) { pending.finish() }
    }
}

/**
 * Handles the widget's Cast button. Separate from [StatusWidgetProvider]
 * because a widget provider must be exported, and this one must not be:
 * other apps can't be allowed to start sessions with a bare broadcast.
 */
class StatusWidgetCastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val preset = QuickSpellCaster.castablePreset(context)
        // The button is hidden during sessions, but a stale render can still
        // be tapped: never let it stop or replace a running session.
        if (preset != null && !SessionManager.isFocusActive(prefs)) {
            QuickSpellCaster.cast(context, preset)?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
        StatusWidget.update(context)
    }
}
