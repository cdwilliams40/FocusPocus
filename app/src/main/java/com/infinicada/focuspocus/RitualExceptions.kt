package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.infinicada.focuspocus.limit.DayKey
import com.infinicada.focuspocus.model.Schedule
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

object RitualExceptions {
    private const val TAG = "RitualExceptions"
    const val MIN_ADVANCE_MS = 24L * 60 * 60 * 1000
    private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun loadAll(
        prefs: SharedPreferences,
        todayKey: String = DayKey.today()
    ): Map<String, Set<String>> {
        val json = prefs.getString(Constants.PrefsKeys.RITUAL_EXCEPTIONS, null) ?: return emptyMap()
        // Yesterday's skip is kept: an overnight ritual's window runs past
        // midnight, and isScheduleActiveAt checks the previous day's key for it.
        val oldestKept = previousDayKey(todayKey) ?: todayKey
        return try {
            val raw = AppJson.json.decodeFromString<Map<String, List<String>>>(json)
            raw.mapValues { (_, list) ->
                list.filter { it >= oldestKept }.toSet()
            }.filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ritual exceptions JSON", e)
            emptyMap()
        }
    }

    private fun previousDayKey(dayKey: String): String? = try {
        LocalDate.parse(dayKey, BASIC_DATE).minusDays(1).format(BASIC_DATE)
    } catch (_: Exception) {
        null
    }

    fun skippedDatesFor(
        prefs: SharedPreferences,
        scheduleId: String,
        todayKey: String = DayKey.today()
    ): Set<String> = loadAll(prefs, todayKey)[scheduleId].orEmpty()

    /**
     * Pure check: returns true if [schedule] runs on [dayKey] and its start
     * instant on [dayKey] is at least [MIN_ADVANCE_MS] (24 hours) after [nowMillis].
     */
    fun canRequestSkip(
        schedule: Schedule,
        dayKey: String,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val startMins = parseScheduleMinutes(schedule.effectiveStartTime) ?: return false
        val date = try {
            LocalDate.parse(dayKey, BASIC_DATE)
        } catch (_: Exception) {
            return false
        }
        val cal = Calendar.getInstance().apply {
            timeInMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
        val dow = calendarDayToDayOfWeek(cal.get(Calendar.DAY_OF_WEEK)) ?: return false
        if (dow !in schedule.effectiveDays) return false

        val startMillis = date.atTime(startMins / 60, startMins % 60)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        return startMillis - nowMillis >= MIN_ADVANCE_MS
    }

    /**
     * Upcoming dates in the next [lookaheadDays] days on which [schedule] runs
     * and that satisfy the 24-hour advance rule.
     */
    fun upcomingSkippableDates(
        schedule: Schedule,
        nowMillis: Long = System.currentTimeMillis(),
        lookaheadDays: Int = 14,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<String> {
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return (1..lookaheadDays).mapNotNull { offset ->
            val key = today.plusDays(offset.toLong()).format(BASIC_DATE)
            key.takeIf { canRequestSkip(schedule, it, nowMillis, zoneId) }
        }
    }

    /** Schedules a planned skip for [dayKey] if it satisfies the 24h advance rule. */
    fun requestSkip(
        prefs: SharedPreferences,
        schedule: Schedule,
        dayKey: String,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (!canRequestSkip(schedule, dayKey, nowMillis, zoneId)) return false
        val todayKey = java.time.Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().format(BASIC_DATE)
        val all = loadAll(prefs, todayKey).mapValues { it.value.toMutableSet() }.toMutableMap()
        all.getOrPut(schedule.id) { mutableSetOf() }.add(dayKey)
        saveAll(prefs, all)
        return true
    }

    /**
     * After a backup restore: keeps the skips that were already planned, and
     * restored ones only where they still meet the 24-hour rule, so a backup
     * can't plant a skip for today or tomorrow. Skips for rituals that no
     * longer exist are dropped.
     */
    fun holdForRestore(
        prefs: SharedPreferences,
        previous: Map<String, Set<String>>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val todayKey = java.time.Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().format(BASIC_DATE)
        val restored = loadAll(prefs, todayKey)
        val schedules = PrefsHelper.loadListLenient<Schedule>(prefs, Constants.PrefsKeys.SCHEDULES)
            .orEmpty().associateBy { it.id }
        val held = (previous.keys + restored.keys).associateWith { id ->
            val schedule = schedules[id] ?: return@associateWith emptySet()
            previous[id].orEmpty() + restored[id].orEmpty().filter { day ->
                canRequestSkip(schedule, day, nowMillis, zoneId)
            }
        }
        saveAll(prefs, held)
    }

    /** Cancels a planned skip (tightening — always allowed immediately). */
    fun cancelSkip(
        prefs: SharedPreferences,
        scheduleId: String,
        dayKey: String,
        todayKey: String = DayKey.today()
    ) {
        val all = loadAll(prefs, todayKey).mapValues { it.value.toMutableSet() }.toMutableMap()
        all[scheduleId]?.remove(dayKey)
        if (all[scheduleId].isNullOrEmpty()) all.remove(scheduleId)
        saveAll(prefs, all)
    }

    private fun saveAll(prefs: SharedPreferences, map: Map<String, Set<String>>) {
        val serializable = map.mapValues { (_, set) -> set.sorted() }.filterValues { it.isNotEmpty() }
        prefs.edit {
            if (serializable.isEmpty()) remove(Constants.PrefsKeys.RITUAL_EXCEPTIONS)
            else putString(Constants.PrefsKeys.RITUAL_EXCEPTIONS, AppJson.json.encodeToString(serializable))
        }
    }
}
