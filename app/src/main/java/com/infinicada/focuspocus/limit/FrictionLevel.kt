package com.infinicada.focuspocus.limit

/**
 * Escalating friction levels shown on the block overlay when a per-session cooldown is active.
 *
 * Each time the user tries to open a cooled-down app the level increases, making dismissal
 * progressively harder until they stop trying.
 */
enum class FrictionLevel(val countdownSeconds: Int, val requiresPhrase: Boolean) {
    /** First cooldown attempt — short countdown only. */
    LEVEL_1(5, false),
    /** Second attempt — longer countdown. */
    LEVEL_2(15, false),
    /** Third+ attempt — longest countdown plus must type a reflective phrase to close. */
    LEVEL_3(30, true);

    companion object {
        /**
         * Returns the friction level for a given [attemptCount] (1-indexed).
         * attemptCount 1 → LEVEL_1, 2 → LEVEL_2, 3+ → LEVEL_3.
         */
        fun fromAttemptCount(attemptCount: Int): FrictionLevel = when {
            attemptCount <= 1 -> LEVEL_1
            attemptCount == 2 -> LEVEL_2
            else -> LEVEL_3
        }
    }
}
