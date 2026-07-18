package com.infinicada.focuspocus

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.GuardWindow
import com.infinicada.focuspocus.limit.PactManager
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
 * Pact-gated apps (per-app pact configs and pact-circle members) are suspended the
 * same way, but *at all times* rather than session-scoped — a pact'd app is blocked
 * by default, so it stays greyed out (and out of launcher suggestions) until the
 * user requests time from the Pacts dashboard, which grants an allowance and lifts
 * the suspension for its duration. Governed by
 * [Constants.PrefsKeys.DEVICE_OWNER_SUSPEND_PACTS] (on by default).
 *
 * The accessibility service remains the fallback (and still handles time limits);
 * suspension is layered on top when [Constants.PrefsKeys.DEVICE_OWNER_ENFORCEMENT]
 * is enabled.
 */
object DeviceOwnerManager {
    private const val TAG = "DeviceOwnerManager"

    /** Command the user runs once over adb to grant device-owner status. */
    const val SET_DEVICE_OWNER_COMMAND =
        "adb shell dpm set-device-owner com.infinicada.focuspocus/.FocusDeviceAdminReceiver"

    /**
     * Diagnostic command for the most common provisioning failure: Android refuses
     * to set a device owner while *any* account is registered, and many apps
     * (Instagram, WhatsApp, ...) register hidden accounts that never appear in the
     * system account settings. This lists them all, per user.
     */
    const val LIST_ACCOUNTS_COMMAND = "adb shell dumpsys account"

    /**
     * Cooling-off period before a Warden-removal request unlocks the actual
     * removal — deprovisioning must be a decision made a day ahead, not in the
     * moment the protection bites.
     */
    const val REMOVAL_COOLDOWN_MS = 24L * 60 * 60 * 1000

    /**
     * Whether a removal request made at [requestMillis] has cleared its
     * cooling-off period at [now]. Zero/negative means no request is pending.
     */
    fun isRemovalUnlocked(requestMillis: Long, now: Long = System.currentTimeMillis()): Boolean =
        requestMillis > 0 && now - requestMillis >= REMOVAL_COOLDOWN_MS

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
     * True for builds deployed straight from Android Studio's Run button, which
     * are flagged test-only. Android deliberately allows removing a test-only
     * device owner (so developers can't brick their phones) — meaning uninstall
     * protection does NOT hold for such builds and the user should install a
     * normal build instead.
     */
    fun isTestOnlyBuild(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_TEST_ONLY) != 0

    /**
     * Blocks uninstall of FocusPocus itself. Device-owner apps can't be uninstalled
     * anyway, but the explicit flag also survives edge cases (e.g. work-profile
     * removal flows) and makes the intent unambiguous.
     *
     * Also sets the admin support messages: when the user taps a suspended app the
     * OS shows its own "app is paused" dialog, whose details screen displays this
     * text — the one channel available to point them back at Focus Pocus to
     * request time.
     */
    fun applySelfProtection(context: Context) {
        if (!isDeviceOwner(context)) return
        try {
            getDpm(context)?.setUninstallBlocked(getAdminComponent(context), context.packageName, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying self protection", e)
        }
        try {
            val dpm = getDpm(context) ?: return
            val admin = getAdminComponent(context)
            dpm.setShortSupportMessage(admin, context.getString(R.string.device_admin_support_short))
            dpm.setLongSupportMessage(admin, context.getString(R.string.device_admin_support_long))
        } catch (e: Exception) {
            Log.e(TAG, "Error setting support messages", e)
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
     * Packages that should be suspended right now: session-blocked apps plus
     * pact-gated apps with no active allowance, each minus its own escape
     * hatches (breaks, conditional unlocks) and system-critical exemptions.
     */
    private fun computeDesiredSuspensions(context: Context, prefs: SharedPreferences): Set<String> =
        computeSessionSuspensions(context, prefs) + computePactSuspensions(context, prefs)

    /**
     * Launchable apps blocked by any active blocker while a focus session is
     * running (and not on a break), minus apps freed by a satisfied conditional
     * unlock.
     */
    private fun computeSessionSuspensions(context: Context, prefs: SharedPreferences): Set<String> {
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
     * Pact-gated apps that should be greyed out right now. Unlike session
     * suspensions these apply at all times — even outside sessions and during
     * breaks — mirroring the accessibility service's pact gate, which is why
     * breaks are deliberately not consulted here. An app escapes only while its
     * pact allowance runs or a conditional unlock is satisfied.
     */
    private fun computePactSuspensions(context: Context, prefs: SharedPreferences): Set<String> {
        if (!prefs.getBoolean(Constants.PrefsKeys.DEVICE_OWNER_SUSPEND_PACTS, true)) return emptySet()

        val configs = AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)
        val pactManager = PactManager(prefs, gson)
        val groups = pactManager.getGroups()
        if (groups.isEmpty() && configs.values.none { it.pactModeEnabled }) return emptySet()

        // Guard hours: an app whose pact is outside its window is free, so it
        // must not stay greyed out. The minute tick re-syncs suspensions, so
        // greying follows the window within a minute of it opening or closing.
        val gated = GuardStatus.pactGatedConfigs(configs, groups, BlockerRepository.getBlockers(prefs))
            .filterValues { GuardWindow.isActiveNow(it) }
            .keys
        if (gated.isEmpty()) return emptySet()

        return computePactSuspendedPackages(
            pactGated = gated,
            allowedPackages = pactManager.getActiveAllowances().keys +
                getConditionallyUnlockedTimeLimitApps(context, prefs, gated),
            launchablePackages = getLaunchablePackages(context),
            exemptPackages = getExemptPackages(context)
        )
    }

    /**
     * Pure pact-suspension computation, extracted for unit testing: gated
     * packages that are launchable, not exempt, and not currently allowed
     * (by an active pact allowance or a satisfied conditional unlock).
     */
    fun computePactSuspendedPackages(
        pactGated: Set<String>,
        allowedPackages: Set<String>,
        launchablePackages: Set<String>,
        exemptPackages: Set<String>
    ): Set<String> = pactGated.filterTo(mutableSetOf()) { pkg ->
        pkg in launchablePackages && pkg !in exemptPackages && pkg !in allowedPackages
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
    ): Boolean = loadConditionalUnlocks(prefs).any { rule ->
        blockerName in rule.effectiveUnlockedBlockerNames && isRuleSatisfied(context, rule)
    }

    /**
     * Mirrors MyAccessibilityService's conditional-unlock check for time-limit
     * apps: the subset of [candidates] freed by a satisfied rule. Iterates rules
     * (one usage query each) rather than candidates, since rules are few.
     */
    private fun getConditionallyUnlockedTimeLimitApps(
        context: Context,
        prefs: SharedPreferences,
        candidates: Set<String>
    ): Set<String> = loadConditionalUnlocks(prefs)
        .filter { rule ->
            rule.effectiveUnlockedTimeLimitApps.any { it in candidates } && isRuleSatisfied(context, rule)
        }
        .flatMapTo(mutableSetOf()) { it.effectiveUnlockedTimeLimitApps }

    private fun isRuleSatisfied(context: Context, rule: ConditionalUnlock): Boolean =
        rule.requiredMinutes > 0 &&
            UsageStatsHelper.getPackageUsageToday(context, rule.requiredAppPackage) >=
            rule.requiredMinutes.toLong() * 60 * 1000

    private fun loadConditionalUnlocks(prefs: SharedPreferences): List<ConditionalUnlock> {
        val json = prefs.getString(Constants.PrefsKeys.CONDITIONAL_UNLOCKS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ConditionalUnlock>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing conditional unlocks JSON", e)
            emptyList()
        }
    }
}
