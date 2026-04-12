package com.infinicada.focuspocus

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.infinicada.focuspocus.data.AppContainer

class FocusPocusApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            // Apply analytics consent before any Firebase SDK has a chance to collect data
            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val analyticsConsent = prefs.getBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT, false)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(analyticsConsent)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(analyticsConsent)
        } catch (e: Exception) {
            Log.e("FocusPocusApplication", "Firebase initialization failed", e)
        }
    }
}
