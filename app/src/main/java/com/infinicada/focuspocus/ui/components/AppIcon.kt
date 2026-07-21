package com.infinicada.focuspocus.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// LazyColumn disposes rows as they scroll out, so without a cache every
// scroll-back re-queries PackageManager and re-rasterizes the icon (visible
// as a blank-then-pop per row). Byte-bounded so a large app list can't grow
// it past a few dozen icons' worth of memory.
private val iconCache = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        value.width * value.height * 4
}

@Composable
fun AppIcon(packageName: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // PackageManager icon lookup + bitmap rasterization are too slow for the main
    // thread: AppIcon renders per row in large LazyColumns (app picker, insights),
    // so a synchronous decode janks every newly composed row while scrolling.
    // Cache hits skip the load entirely and draw on first composition.
    //
    // The state must be keyed on packageName: when a reused slot rebinds to a
    // different app (a list reordering or scrolling without item keys), an
    // unkeyed holder — produceState's included — would carry the previous
    // app's bitmap across the switch, and the null-check below would then
    // skip the reload, leaving the wrong icon on the row permanently.
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(iconCache.get(packageName)) }
    LaunchedEffect(packageName) {
        if (icon == null) {
            icon = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap().asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }?.also { iconCache.put(packageName, it) }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        // Reserve the slot while the icon loads so list rows don't shift.
        Box(modifier = modifier)
    }
}
