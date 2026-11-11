package com.infinicada.focuspocus.limit

/**
 * What the window events that follow a block mean.
 *
 * Android often fires several window events for one app switch, and an app the
 * service has just closed can send one more on its way out. For [WINDOW_MS] after
 * a block, an event from the blocked app is therefore not acted on again
 * ([isRecent]) — and while FocusPocus's own block screen is in front it is not
 * even taken as the app being in the foreground ([isEcho]).
 *
 * An event that is held back this way may still be the user getting back into the
 * app, so the app is judged once the window has passed ([remainingMillis]) if it
 * is still in front then ([isStillInFront]).
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
     * the event counts, and the app is tracked as the foreground app again — so it
     * is judged when the window has passed, even though this event is debounced.
     */
    fun isEcho(lastBlockMillis: Long?, now: Long, blockScreenOnTop: Boolean): Boolean =
        blockScreenOnTop && isRecent(lastBlockMillis, now)

    /** How long until the block at [lastBlockMillis] has settled; 0 once it has. */
    fun remainingMillis(lastBlockMillis: Long?, now: Long): Long =
        ((lastBlockMillis ?: 0L) + WINDOW_MS - now).coerceIn(0L, WINDOW_MS)

    /**
     * Whether [packageName], seen again inside its window and so not judged then,
     * is really the app in front now that the window has passed.
     *
     * [trackedForeground] is what the window events say. On its own that is not
     * enough here: the app's late event can also arrive after the user has already
     * left the block screen, and acting on it would put a block over the home
     * screen. So the system's own record has to agree: [lastResumedPackage] is the
     * app whose activity was resumed last since the block. Null means there is no
     * such record (no usage access), and then the window events are all there is.
     */
    fun isStillInFront(
        packageName: String,
        trackedForeground: String?,
        lastResumedPackage: String?
    ): Boolean =
        trackedForeground == packageName &&
            (lastResumedPackage == null || lastResumedPackage == packageName)
}
