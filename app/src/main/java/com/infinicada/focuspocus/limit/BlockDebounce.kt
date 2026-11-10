package com.infinicada.focuspocus.limit

/**
 * What the window events that follow a block mean.
 *
 * Android often fires several window events for one app switch, and an app the
 * service has just closed can send one more on its way out. For [WINDOW_MS] after
 * a block, an event from the blocked app is therefore not acted on again
 * ([isRecent]) — and while FocusPocus's own block screen is in front it is not
 * even taken as the app being in the foreground ([isEcho]).
 */
object BlockDebounce {
    const val WINDOW_MS = 2000L

    /** True while the block at [lastBlockMillis] (null: never blocked) is still settling. */
    fun isRecent(lastBlockMillis: Long?, now: Long): Boolean =
        now - (lastBlockMillis ?: 0L) < WINDOW_MS

    /**
     * True when a window event from an app blocked at [lastBlockMillis] is only the
     * tail of that block, not the app coming back: the block is still settling and
     * the block screen is the activity in front, so the app cannot be.
     *
     * The second condition is what keeps a real return covered. If the user gets
     * back into the app inside the window, the block screen has lost the top spot,
     * the event counts, and the app is tracked as the foreground app again — so the
     * next timed check still catches it even though this event is debounced.
     */
    fun isEcho(lastBlockMillis: Long?, now: Long, blockScreenOnTop: Boolean): Boolean =
        blockScreenOnTop && isRecent(lastBlockMillis, now)
}
