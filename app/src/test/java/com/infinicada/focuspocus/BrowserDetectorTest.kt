package com.infinicada.focuspocus

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

class BrowserDetectorTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var packageManager: PackageManager

    private lateinit var detector: BrowserDetector

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(context.packageManager).thenReturn(packageManager)
        detector = BrowserDetector(context)
    }

    @Test
    fun `getBrowserPackages returns known browsers plus dynamic ones`() {
        // Setup mock ResolveInfo for a dynamic browser
        val dynamicBrowserPackage = "com.example.browser"
        // With isReturnDefaultValues = true, we can instantiate framework classes
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = ActivityInfo()
        resolveInfo.activityInfo.packageName = dynamicBrowserPackage

        `when`(packageManager.queryIntentActivities(any<Intent>(), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val packages = detector.getBrowserPackages()

        // Verify known browsers are present
        assertTrue(packages.contains("com.android.chrome"))
        assertTrue(packages.contains("org.mozilla.firefox"))

        // Verify dynamic browser is present
        assertTrue(packages.contains(dynamicBrowserPackage))
    }

    @Test
    fun `getBrowserPackages handles exception gracefully`() {
        `when`(packageManager.queryIntentActivities(any<Intent>(), anyInt()))
            .thenThrow(RuntimeException("PackageManager error"))

        val packages = detector.getBrowserPackages()

        // Verify known browsers are still returned
        assertTrue(packages.isNotEmpty())
        assertTrue(packages.contains("com.android.chrome"))
    }
}
