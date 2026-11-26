package com.infinicada.focuspocus.handler

/**
 * Prevents a single physical NFC tap from double-firing (e.g. starting a talisman
 * session and immediately stopping it while the phone is still held against the tag).
 *
 * Debounces repeated reads of the same [tagId] within [windowMillis], while allowing
 * a different tag to be scanned immediately.
 */
class NfcTapDebouncer(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS
) {
    private var lastTagId: String? = null
    private var lastTapMillis: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldAccept(tagId: String, nowMillis: Long): Boolean {
        if (tagId == lastTagId && lastTapMillis != Long.MIN_VALUE &&
            nowMillis - lastTapMillis in 0 until windowMillis
        ) {
            return false
        }
        lastTagId = tagId
        lastTapMillis = nowMillis
        return true
    }

    @Synchronized
    fun reset() {
        lastTagId = null
        lastTapMillis = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 1_500L
    }
}
