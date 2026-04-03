package com.infinicada.focuspocus

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.infinicada.focuspocus.handler.NfcResult
import com.infinicada.focuspocus.handler.TriggerHandler
import com.infinicada.focuspocus.handler.TriggerResult
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.ui.FocusPocusApp
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import com.infinicada.focuspocus.viewmodel.SessionViewModel
import com.infinicada.focuspocus.viewmodel.SettingsViewModel
import com.infinicada.focuspocus.viewmodel.SpellbookViewModel

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private lateinit var triggerHandler: TriggerHandler

    private var lastScannedTagId by mutableStateOf<String?>(null)
    private var isServiceEnabled by mutableStateOf(false)
    private var nfcTriggerCount by mutableStateOf(0)
    private var qrTriggerCount by mutableStateOf(0)

    // Deep link confirmation state
    private var pendingDeepLinkPreset by mutableStateOf<FocusPreset?>(null)
    private var showDeepLinkConfirmation by mutableStateOf(false)

    // Listener to detect when the accessibility service ends a ritual while the app is in the foreground
    private val scheduleIdChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Constants.PrefsKeys.ACTIVE_SCHEDULE_ID) {
            // The SessionViewModel will pick this up via syncFromPrefs triggered by the composable
            nfcTriggerCount++ // Reuse trigger mechanism to force sync
        }
    }

    private lateinit var scanLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scanLauncher = registerForActivityResult(ScanContract()) { result ->
            result.contents?.let { handleQrResult(it) }
        }

        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        triggerHandler = TriggerHandler(this, sharedPreferences, gson)

        // Run data cleanup via repositories
        val container = (application as FocusPocusApplication).container
        container.session.clearDanglingActiveBlocker(
            container.blockers.getBlockers().map { it.name }.toSet()
        )
        container.session.clearDanglingActiveSchedule(
            container.schedules.getSchedules().map { it.id }.toSet()
        )

        // Apply analytics consent state
        val analyticsConsent = sharedPreferences.getBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT, false)
        try {
            FirebaseApp.initializeApp(this)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(analyticsConsent)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(analyticsConsent)
        } catch (e: Exception) {
            // Ignore initialization issues when google-services.json is missing
            Log.e("MainActivity", "Firebase initialization failed", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        setContent {
            val settingsVM: SettingsViewModel = viewModel()
            val themeMode by settingsVM.themeMode.collectAsStateWithLifecycle()

            FocusPocusTheme(themeMode = themeMode) {
                FocusPocusApp(
                    isServiceEnabled = isServiceEnabled,
                    lastScannedTagId = lastScannedTagId,
                    nfcTriggerCount = nfcTriggerCount,
                    qrTriggerCount = qrTriggerCount,
                    onScanQrCode = { launchQrScanner() },
                    pendingDeepLinkPreset = pendingDeepLinkPreset,
                    showDeepLinkConfirmation = showDeepLinkConfirmation,
                    onConfirmDeepLink = { confirmDeepLinkAction() },
                    onDismissDeepLink = { dismissDeepLinkConfirmation() },
                    modifier = Modifier
                )
            }
        }

        handleIntent(intent)
    }

    private fun handleQrResult(contents: String) {
        val container = (application as FocusPocusApplication).container
        val result = triggerHandler.handleQrResult(
            contents,
            container.presets.getPresets(),
            container.talismans.getNamedTags(),
            container.blockers.getBlockers()
        )
        when (result) {
            is TriggerResult.Success -> {
                Toast.makeText(this, getString(result.messageResId, *result.args), Toast.LENGTH_SHORT).show()
                qrTriggerCount++
            }
            is TriggerResult.Error -> {
                Toast.makeText(this, getString(result.messageResId, *result.args), Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val container = (application as FocusPocusApplication).container
        val preset = triggerHandler.resolveDeepLink(
            data,
            container.presets.getPresets(),
            container.talismans.getNamedTags()
        )
        if (preset != null) {
            pendingDeepLinkPreset = preset
            showDeepLinkConfirmation = true
        }
    }

    private fun confirmDeepLinkAction() {
        pendingDeepLinkPreset?.let { preset ->
            val container = (application as FocusPocusApplication).container
            val result = triggerHandler.togglePreset(preset, container.blockers.getBlockers())
            when (result) {
                is TriggerResult.Success -> {
                    Toast.makeText(this, getString(result.messageResId, *result.args), Toast.LENGTH_SHORT).show()
                    qrTriggerCount++
                }
                is TriggerResult.Error -> {
                    Toast.makeText(this, getString(result.messageResId, *result.args), Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
        dismissDeepLinkConfirmation()
    }

    private fun dismissDeepLinkConfirmation() {
        pendingDeepLinkPreset = null
        showDeepLinkConfirmation = false
    }

    private fun launchQrScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt(getString(R.string.main_scan_qr_prompt))
        options.setBeepEnabled(false)
        options.setOrientationLocked(true)
        scanLauncher.launch(options)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        isServiceEnabled = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)
        sharedPreferences.registerOnSharedPreferenceChangeListener(scheduleIdChangeListener)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(scheduleIdChangeListener)
    }

    override fun onTagDiscovered(tag: Tag?) {
        tag?.let {
            val tagIdBytes = it.id
            if (tagIdBytes == null || tagIdBytes.isEmpty()) {
                Log.w("MainActivity", "NFC tag has empty or null ID")
                return@let
            }
            val newTagId = tagIdBytes.toHexString()
            runOnUiThread {
                lastScannedTagId = newTagId

                val container = (application as FocusPocusApplication).container
                val result = triggerHandler.handleNfcTag(
                    tagId = newTagId,
                    activeScheduleId = container.session.getActiveScheduleId(),
                    schedules = container.schedules.getSchedules(),
                    focusPresets = container.presets.getPresets(),
                    namedTags = container.talismans.getNamedTags(),
                    blockerLists = container.blockers.getBlockers()
                )

                when (result) {
                    is NfcResult.DispelSchedule -> {
                        container.session.stopSession()
                        Toast.makeText(this, getString(result.messageResId), Toast.LENGTH_SHORT).show()
                        nfcTriggerCount++
                    }
                    is NfcResult.Toast -> {
                        Toast.makeText(this, getString(result.messageResId), Toast.LENGTH_SHORT).show()
                    }
                    is NfcResult.PresetToggled -> {
                        val triggerResult = result.triggerResult
                        when (triggerResult) {
                            is TriggerResult.Success -> {
                                Toast.makeText(this, getString(triggerResult.messageResId, *triggerResult.args), Toast.LENGTH_SHORT).show()
                                nfcTriggerCount++
                            }
                            is TriggerResult.Error -> {
                                Toast.makeText(this, getString(triggerResult.messageResId, *triggerResult.args), Toast.LENGTH_SHORT).show()
                            }
                            else -> {}
                        }
                    }
                    is NfcResult.ToggleFocusTag -> {
                        val currentFocusTagId = container.session.getFocusTagId()
                        if (currentFocusTagId == null) {
                            container.session.setFocusTagId(newTagId)
                        } else {
                            container.session.setFocusTagId(null)
                        }
                        nfcTriggerCount++
                    }
                    is NfcResult.UnknownTag -> { /* no-op */ }
                }
            }
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName)
                return true
        }
        return false
    }
}
