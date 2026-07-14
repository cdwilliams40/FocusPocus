package com.infinicada.focuspocus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(packageName: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // PackageManager icon lookup + bitmap rasterization are too slow for the main
    // thread: AppIcon renders per row in large LazyColumns (app picker, insights),
    // so a synchronous decode janks every newly composed row while scrolling.
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap().asImageBitmap()
            } catch (e: Exception) {
                null
            }
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
