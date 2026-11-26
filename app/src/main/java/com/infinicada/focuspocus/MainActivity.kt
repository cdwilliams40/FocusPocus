package com.infinicada.focuspocus

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.infinicada.focuspocus.handler.NfcForegroundHelper
import com.infinicada.focuspocus.handler.NfcTapDebouncer
import com.infinicada.focuspocus.ui.FocusPocusApp
import com.infinicada.focuspocus.ui.theme.DarkBackground
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import com.infinicada.focuspocus.ui.theme.LightBackground
import com.infinicada.focuspocus.ui.theme.ThemeMode
import com.infinicada.focuspocus.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val nfcDebouncer = NfcTapDebouncer()

    private var lastScannedTagId by mutableStateOf<String?>(null)
    private var isServiceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        // Run data cleanup via repositories
        val container = (application as FocusPocusApplication).container

        // Repaint the window background to match the in-app theme before Compose attaches.
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val windowNight = when (container.settings.getThemeMode()) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemNight
        }
        window.setBackgroundDrawable(
            (if (windowNight) DarkBackground else LightBackground).toArgb().toDrawable()
        )
        container.session.clearDanglingActiveBlocker(
            container.blockers.getBlockers().map { it.name }.toSet()
        )
        container.session.clearDanglingActiveSchedule(
            container.schedules.getSchedules().map { it.id }.toSet()
        )

        // Apply analytics consent state (Firebase already initialized in FocusPocusApplication)
        val analyticsConsent = sharedPreferences.getBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT, true)
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(analyticsConsent)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(analyticsConsent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Firebase consent update failed", e)
        }

        // Only prompt for notification permission after onboarding has explained why.
        if (container.settings.isOnboardingCompleted()) {
            requestNotificationPermissionIfNeeded()
        }

        setContent {
            val settingsVM: SettingsViewModel = viewModel()
            val themeMode by settingsVM.themeMode.collectAsStateWithLifecycle()

            FocusPocusTheme(themeMode = themeMode) {
                FocusPocusApp(
                    isServiceEnabled = isServiceEnabled,
                    lastScannedTagId = lastScannedTagId,
                    onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                    modifier = Modifier
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Foreground-only NFC reader mode: never registers a manifest NFC filter
        // or writes NDEF records, so URL/NDEF tags keep working outside Focus Pocus.
        NfcForegroundHelper.enableReaderMode(this, nfcAdapter, this)
        isServiceEnabled = ProtectionHealth.isAccessibilityServiceEnabled(this)
        (application as FocusPocusApplication).container.sessionStore.refresh()
    }

    override fun onPause() {
        super.onPause()
        NfcForegroundHelper.disableReaderMode(this, nfcAdapter)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val tagIdBytes = tag?.id
        if (tagIdBytes == null || tagIdBytes.isEmpty()) {
            Log.w("MainActivity", "NFC tag has empty or null ID")
            return
        }
        val newTagId = tagIdBytes.toHexString()
        if (!nfcDebouncer.shouldAccept(newTagId, System.currentTimeMillis())) return

        runOnUiThread {
            lastScannedTagId = newTagId
            val container = (application as FocusPocusApplication).container
            NfcForegroundHelper.handleTag(
                activity = this,
                container = container,
                tagId = newTagId,
                emitForegroundSummary = true
            )
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
