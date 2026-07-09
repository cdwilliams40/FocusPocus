package com.infinicada.focuspocus.model

import android.content.pm.ApplicationInfo

data class AppInfo(
    val name: String,
    val packageName: String,
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED
)
