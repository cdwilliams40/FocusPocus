package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.data.InstalledAppsRepository
import com.infinicada.focuspocus.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class InstalledAppsRepositoryTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var repo: InstalledAppsRepository
    private val gson = Gson()

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        repo = InstalledAppsRepository(prefs, gson)
    }

    @Test
    fun `getCachedApps returns empty on first run`() {
        assertEquals(emptyList<AppInfo>(), repo.getCachedApps())
    }

    @Test
    fun `cached apps round-trip with names and categories`() {
        val apps = listOf(
            AppInfo(name = "Stuff", packageName = "com.thing.stuff", category = 3),
            AppInfo(name = "Other", packageName = "com.thing.other")
        )
        repo.cacheApps(apps)
        assertEquals(apps, repo.getCachedApps())
    }

    @Test
    fun `caching replaces the previous scan wholesale`() {
        repo.cacheApps(listOf(AppInfo(name = "Gone", packageName = "com.thing.gone")))
        val fresh = listOf(AppInfo(name = "Stuff", packageName = "com.thing.stuff"))
        repo.cacheApps(fresh)
        assertEquals(fresh, repo.getCachedApps())
    }

    @Test
    fun `corrupt cache falls back to empty instead of throwing`() {
        prefs.putString(Constants.PrefsKeys.INSTALLED_APPS_CACHE, "{not json")
        assertEquals(emptyList<AppInfo>(), repo.getCachedApps())
    }
}
