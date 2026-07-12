package com.infinicada.focuspocus

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.model.ConditionalUnlock

/**
 * Device-owner enforcement layer.
 *
 * When FocusPocus is provisioned as device owner (see [SET_DEVICE_OWNER_COMMAND]),
 * blocked apps are *suspended* via [DevicePolicyManager.setPackagesSuspended] for
 * the duration of a focus session: their launcher icons grey out and any attempt
 * to open them is refused by the OS itself — enforcement no longer depends on the
 * accessibility service winning a race against the opened app. Device-owner status
 * also makes FocusPocus impossible to uninstall while it holds the role.
 *
 * The accessibility service remains the fallback (and still handles websites and
 * time limits); suspension is layered on top when [Constants.PrefsKeys.DEVICE_OWNER_ENFORCEMENT]
 * is enabled.
 */
object DeviceOwnerManager {
    private const val TAG = "DeviceOwnerManager"

    /** Command the user runs once over adb to grant device-owner status. */
    const val SET_DEVICE_OWNER_COMMAND =
        "adb shell dpm set-device-owner com.infinicada.focuspocus/.FocusDeviceAdminReceiver"

    private val gson = Gson()

    fun getAdminComponent(context: Context): ComponentName =
        ComponentName(context, FocusDeviceAdminReceiver::class.java)

    private fun getDpm(context: Context): DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean = try {
        getDpm(context)?.isDeviceOwnerApp(context.packageName) == true
    } catch (e: Exception) {
        Log.e(TAG, "Error checking device owner state", e)
        false
    }

    /**
     * Blocks uninstall of FocusPocus itself. Device-owner apps can't be uninstalled
     * anyway, but the explicit flag also survives edge cases (e.g. work-profile
     * removal flows) and makes the intent unambiguous.
     */
    fun applySelfProtection(context: Context) {
        if (!isDeviceOwner(context)) return
        try {
            getDpm(context)?.setUninstallBlocked(getAdminComponent(context), context.packageName, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying self protection", e)
        }
    }

    /**
     * The user-controlled escape hatch: unsuspends everything, lifts the uninstall
     * block, and relinquishes device-owner status. The Settings UI refuses to call
     * this while a focus session is active.
     */
    fun clearDeviceOwner(context: Context): Boolean {
        val dpm = getDpm(context) ?: return false
        if (!isDeviceOwner(context)) return false
        return try {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            applySuspensionDiff(context, prefs, desired = emptySet())
            dpm.setUninstallBlocked(getAdminComponent(context), context.packageName, false)
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(context.packageName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing device owner", e)
            false
        }
    }

    /**
     * Reconciles OS-level package suspensions with the current session state.
     * Idempotent and cheap when nothing changed, so it is safe to call from every
     * session transition and the accessibility service's minute tick. Never throws.
     */
    fun syncSuspensions(context: Context) {
        try {
            if (!isDeviceOwner(context)) return
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val enforcementOn = prefs.getBoolean(Constants.PrefsKeys.DEVICE_OWNER_ENFORCEMENT, false)
            val desired = if (enforcementOn) computeDesiredSuspensions(context, prefs) else emptySet()
            applySuspensionDiff(context, prefs, desired)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing suspensions", e)
        }
    }

    private fun applySuspensionDiff(context: Context, prefs: SharedPreferences, desired: Set<String>) {
        val previous = prefs.getStringSet(Constants.PrefsKeys.DEVICE_OWNER_SUSPENDED_PACKAGES, emptySet())
            ?.toSet() ?: emptySet()
        if (desired == previous) return

        val dpm = getDpm(context) ?: return
        val admin = getAdminComponent(context)
        val nowSuspended = previous.toMutableSet()

        val toSuspend = desired - previous
        if (toSuspend.isNotEmpty()) {
            val failed = try {
                dpm.setPackagesSuspended(admin, toSuspend.toTypedArray(), true).toSet()
            } catch (e: Exception) {
                Log.e(TAG, "Error suspending packages", e)
                toSuspend
            }
            nowSuspended += (toSuspend - failed)
        }

        val toUnsuspend = previous - desired
        if (toUnsuspend.isNotEmpty()) {
            val failed = try {
                dpm.setPackagesSuspended(admin, toUnsuspend.toTypedArray(), false).toSet()
            } catch (e: Exception) {
                Log.e(TAG, "Error unsuspending packages", e)
                emptySet()
            }
            // Packages that fail to unsuspend because they were uninstalled are gone
            // either way; only keep tracking ones that are still installed.
            nowSuspended -= toUnsuspend.filter { it !in failed || !isPackageInstalled(context, it) }.toSet()
        }

        prefs.edit()
            .putStringSet(Constants.PrefsKeys.DEVICE_OWNER_SUSPENDED_PACKAGES, nowSuspended)
            .apply()
        Log.d(TAG, "Suspension sync: ${nowSuspended.size} package(s) suspended")
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Packages that should be suspended right now: launchable apps blocked by any
     * active blocker while a focus session is running (and not on a break), minus
     * apps freed by a satisfied conditional unlock and system-critical exemptions.
     */
    private fun computeDesiredSuspensions(context: Context, prefs: SharedPreferences): Set<String> {
        val focusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false) ||
            prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null) != null
        if (!focusActive) return emptySet()
        if (prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)) return emptySet()

        val activeNames = getActiveBlockerNames(prefs)
        if (activeNames.isEmpty()) return emptySet()
        val activeBlockers = BlockerRepository.getBlockers(prefs)
            .filter { it.name in activeNames && !isConditionallyUnlocked(context, prefs, it.name) }
        if (activeBlockers.isEmpty()) return emptySet()

        return computeBlockedPackages(
            activeBlockers = activeBlockers,
            launchablePackages = getLaunchablePackages(context),
            exemptPackages = getExemptPackages(context)
        )
    }

    /**
     * Pure blocked-set computation, extracted for unit testing. Whitelist blockers
     * block everything launchable outside the list; blacklist blockers block only
     * their listed apps. A package is suspended if any active blocker blocks it.
     */
    fun computeBlockedPackages(
        activeBlockers: List<Blocker>,
        launchablePackages: Set<String>,
        exemptPackages: Set<String>
    ): Set<String> {
        if (activeBlockers.isEmpty()) return emptySet()
        return launchablePackages.filterTo(mutableSetOf()) { pkg ->
            pkg !in exemptPackages && activeBlockers.any { it.shouldBlock(pkg) }
        }
    }

    private fun getLaunchablePackages(context: Context): Set<String> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
    } catch (e: Exception) {
        Log.e(TAG, "Error querying launchable packages", e)
        emptySet()
    }

    /**
     * Never suspend: ourselves, home launchers, keyboards, or core system UI.
     * DevicePolicyManager refuses several of these anyway; the explicit list keeps
     * the persisted bookkeeping clean.
     */
    private fun getExemptPackages(context: Context): Set<String> {
        val exempt = mutableSetOf(context.packageName, "com.android.systemui", "android")
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(homeIntent, 0)
                .mapTo(exempt) { it.activityInfo.packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launchers", e)
        }
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.enabledInputMethodList?.mapTo(exempt) { it.packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying input methods", e)
        }
        return exempt
    }

    private fun getActiveBlockerNames(prefs: SharedPreferences): List<String> {
        val json = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val parsed: List<String>? = gson.fromJson(json, type)
                if (parsed != null) return parsed
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing active blockers JSON", e)
            }
        }
        val single = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
        return if (single != null) listOf(single) else emptyList()
    }

    /** Mirrors MyAccessibilityService's conditional-unlock check for blockers. */
    private fun isConditionallyUnlocked(
        context: Context,
        prefs: SharedPreferences,
        blockerName: String
    ): Boolean {
        val json = prefs.getString(Constants.PrefsKeys.CONDITIONAL_UNLOCKS, null) ?: return false
        val rules: List<ConditionalUnlock> = try {
            val type = object : TypeToken<List<ConditionalUnlock>>() {}.type
            gson.fromJson(json, type) ?: return false
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing conditional unlocks JSON", e)
            return false
        }
        return rules.any { rule ->
            blockerName in rule.effectiveUnlockedBlockerNames &&
                rule.requiredMinutes > 0 &&
                UsageStatsHelper.getPackageUsageToday(context, rule.requiredAppPackage) >=
                rule.requiredMinutes.toLong() * 60 * 1000
        }
    }
}
