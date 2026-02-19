package com.infinicada.focuspocus

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AppUtilsTest {

    @Test
    fun getAppName_returnsLabel_whenAppExists() {
        val packageName = "com.example.app"
        val appName = "Example App"

        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        val applicationInfo = mock(ApplicationInfo::class.java)

        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.getApplicationInfo(packageName, 0)).thenReturn(applicationInfo)
        `when`(packageManager.getApplicationLabel(applicationInfo)).thenReturn(appName)

        val result = AppUtils.getAppName(context, packageName)

        assertEquals(appName, result)
    }

    @Test
    fun getAppName_returnsPackageName_whenAppNotFound() {
        val packageName = "com.example.missing"

        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)

        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.getApplicationInfo(packageName, 0)).thenThrow(PackageManager.NameNotFoundException())

        val result = AppUtils.getAppName(context, packageName)

        assertEquals(packageName, result)
    }
}
