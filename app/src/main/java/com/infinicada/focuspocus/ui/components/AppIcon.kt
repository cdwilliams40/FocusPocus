package com.infinicada.focuspocus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIcon(packageName: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
    icon?.let {
        Image(
            bitmap = it.toBitmap().asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}
