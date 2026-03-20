package com.infinicada.focuspocus

import android.app.Application
import com.infinicada.focuspocus.data.AppContainer

class FocusPocusApplication : Application() {
    val container by lazy { AppContainer(this) }
}
