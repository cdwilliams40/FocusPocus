package com.infinicada.focuspocus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.WellMath

/**
 * Handles the one-tap "End pact & refund" action on [PactNotifier]'s ongoing notification.
 */
class PactActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_END_ACTIVE_PACTS) return

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val pactManager = PactManager(prefs)
        val now = System.currentTimeMillis()
        val active = pactManager.takeAllActiveAllowances(now)
        if (active.isEmpty()) {
            PactNotifier.update(context)
            return
        }

        val refundMillis = WellMath.refundableMillis(
            revoked = active.values,
            stillRunning = emptyList(),
            now = now
        )
        ManaWell.refund(prefs, refundMillis, now)
        DeviceOwnerManager.syncSuspensions(context)
        PactNotifier.update(context)
        RitualAlarmScheduler.scheduleNext(context)

        val refundedMinutes = (refundMillis / WellMath.MINUTE_MS).toInt()
        EnforcementLog.record(
            prefs,
            EnforcementLog.CAT_PACT,
            "Ended ${active.size} pact(s) from notification (refunded ${refundedMinutes}m)"
        )

        val endedLabel = context.resources.getQuantityString(
            R.plurals.home_end_pact_result, active.size, active.size
        )
        val toastText = if (refundedMinutes > 0) {
            endedLabel + " · " + context.getString(R.string.well_refund_toast, refundedMinutes)
        } else {
            endedLabel
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, toastText, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_END_ACTIVE_PACTS = "com.infinicada.focuspocus.ACTION_END_ACTIVE_PACTS"
    }
}
