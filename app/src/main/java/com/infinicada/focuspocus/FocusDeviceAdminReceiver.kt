package com.infinicada.focuspocus

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device admin receiver backing FocusPocus's device-owner powers.
 *
 * When the app is made device owner (via `adb shell dpm set-device-owner`),
 * this component is the admin that [DeviceOwnerManager] acts through:
 * suspending (greying out) blocked apps during focus sessions, keeping
 * pact-gated apps suspended whenever no allowance runs, and blocking
 * uninstall of FocusPocus itself.
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device admin enabled")
        DeviceOwnerManager.applySelfProtection(context)
        DeviceOwnerManager.syncSuspensions(context)
        // onEnabled also fires for plain device-admin activation, so gate the
        // Warden sigil on actual device-owner status.
        if (DeviceOwnerManager.isDeviceOwner(context)) {
            Progression.unlockSigils(
                context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE),
                com.google.gson.Gson(),
                listOf(com.infinicada.focuspocus.model.SigilCatalog.WARDEN)
            )
        }
    }

    /**
     * Shown by the system when the user tries to deactivate the admin from
     * Settings — the extra friction that makes uninstalling a deliberate act
     * instead of an impulsive one.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device admin disabled")
    }

    companion object {
        private const val TAG = "FocusDeviceAdmin"
    }
}
