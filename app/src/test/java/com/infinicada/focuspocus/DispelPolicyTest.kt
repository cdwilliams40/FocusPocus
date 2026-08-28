package com.infinicada.focuspocus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard and the Quick Settings tile share this policy so a one-tap
 * shade control cannot become the loophole that makes hide-stop-button and the
 * talisman lock decorative.
 */
class DispelPolicyTest {

    @Test
    fun `an ordinary session can be stopped`() {
        assertTrue(
            DispelPolicy.isStopOffered(
                nfcLockMode = false,
                hideStopButton = false,
                focusDurationMinutes = 30,
                ritualRequiresTalisman = false
            )
        )
    }

    @Test
    fun `talisman lock hides the stop everywhere`() {
        assertFalse(
            DispelPolicy.isStopOffered(
                nfcLockMode = true,
                hideStopButton = false,
                focusDurationMinutes = 30,
                ritualRequiresTalisman = false
            )
        )
        // Even a talisman-bound ritual: the lock is the stricter rule.
        assertFalse(
            DispelPolicy.isStopOffered(
                nfcLockMode = true,
                hideStopButton = false,
                focusDurationMinutes = 0,
                ritualRequiresTalisman = true
            )
        )
    }

    @Test
    fun `hide-stop covers timed sessions only`() {
        assertFalse(
            DispelPolicy.isStopOffered(
                nfcLockMode = false,
                hideStopButton = true,
                focusDurationMinutes = 30,
                ritualRequiresTalisman = false
            )
        )
        // An unlimited session with no way out would be a trap, not a commitment.
        assertTrue(
            DispelPolicy.isStopOffered(
                nfcLockMode = false,
                hideStopButton = true,
                focusDurationMinutes = 0,
                ritualRequiresTalisman = false
            )
        )
    }

    @Test
    fun `a talisman-bound ritual shows a disabled stop rather than hiding it`() {
        assertTrue(
            DispelPolicy.isStopOffered(
                nfcLockMode = false,
                hideStopButton = true,
                focusDurationMinutes = 30,
                ritualRequiresTalisman = true
            )
        )
        assertFalse(DispelPolicy.isStopEnabled(ritualRequiresTalisman = true))
        assertTrue(DispelPolicy.isStopEnabled(ritualRequiresTalisman = false))
    }

    @Test
    fun `the one-tap answer collapses offered-but-disabled to no`() {
        assertFalse(
            DispelPolicy.canStopInOneTap(
                nfcLockMode = false,
                hideStopButton = false,
                focusDurationMinutes = 30,
                ritualRequiresTalisman = true
            )
        )
        assertTrue(
            DispelPolicy.canStopInOneTap(
                nfcLockMode = false,
                hideStopButton = false,
                focusDurationMinutes = 30,
                ritualRequiresTalisman = false
            )
        )
    }
}
