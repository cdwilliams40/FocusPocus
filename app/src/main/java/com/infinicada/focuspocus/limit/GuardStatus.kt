package com.infinicada.focuspocus.limit

/** Small time helpers shared by the dashboard, the overlay and notifications. */
object GuardStatus {
    /** Whole minutes left before [expiryMillis], rounded up; 0 once expired. */
    fun minutesUntil(expiryMillis: Long, now: Long): Int {
        val msLeft = expiryMillis - now
        return if (msLeft <= 0L) 0 else ((msLeft + 59_999) / 60_000).toInt()
    }
}
