package com.infinicada.focuspocus

import android.app.Application
import com.google.firebase.FirebaseApp
import com.infinicada.focuspocus.data.AppContainer

class FocusPocusApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("FocusPocusApplication", "Firebase initialization failed", e)
        }
    }
}
