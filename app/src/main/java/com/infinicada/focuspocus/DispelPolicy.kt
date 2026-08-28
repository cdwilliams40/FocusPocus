package com.infinicada.focuspocus

/**
 * Whether a running session may be ended from a one-tap control.
 *
 * Two settings and one ritual binding exist precisely to make stopping hard:
 * hide-stop-button (no early exit from a timed session), talisman lock (a
 * physical tag must be tapped), and a ritual bound to an unbinding talisman.
 * Any surface offering a stop has to honour all three, or it becomes the
 * loophole that makes the others decorative — which is the whole reason this
 * lives in one place instead of being re-derived per surface.
 *
 * Pure and unit tested; both the dashboard's dispel button and the Quick
 * Settings tile read it.
 */
object DispelPolicy {

    /**
     * True when a stop control should be offered at all.
     *
     * @param nfcLockMode talisman lock is on — only a tag ends a session.
     * @param hideStopButton the user asked for no early exit from timed sessions.
     * @param focusDurationMinutes 0 for an untimed session, which hide-stop
     *   deliberately does not cover: an unlimited session with no way out would
     *   be a trap rather than a commitment.
     * @param ritualRequiresTalisman the running ritual is bound to an unbinding
     *   talisman, which supersedes hide-stop (the tag is the way out, and it
     *   still exists).
     */
    fun isStopOffered(
        nfcLockMode: Boolean,
        hideStopButton: Boolean,
        focusDurationMinutes: Int,
        ritualRequiresTalisman: Boolean
    ): Boolean {
        if (nfcLockMode) return false
        if (ritualRequiresTalisman) return true
        return !(hideStopButton && focusDurationMinutes > 0)
    }

    /**
     * True when the offered control should actually work. A ritual bound to a
     * talisman shows a disabled button explaining the tag requirement rather
     * than hiding it, so the user learns why instead of hunting for a control
     * that vanished.
     */
    fun isStopEnabled(ritualRequiresTalisman: Boolean): Boolean = !ritualRequiresTalisman

    /**
     * The one-tap answer, for surfaces with no room to explain: a Quick
     * Settings tile can only act or decline, so an offered-but-disabled stop
     * counts as "no".
     */
    fun canStopInOneTap(
        nfcLockMode: Boolean,
        hideStopButton: Boolean,
        focusDurationMinutes: Int,
        ritualRequiresTalisman: Boolean
    ): Boolean =
        isStopOffered(nfcLockMode, hideStopButton, focusDurationMinutes, ritualRequiresTalisman) &&
            isStopEnabled(ritualRequiresTalisman)
}
