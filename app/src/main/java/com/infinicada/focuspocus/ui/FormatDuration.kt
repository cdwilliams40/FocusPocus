package com.infinicada.focuspocus.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.infinicada.focuspocus.R

@Composable
fun formatDuration(minutes: Int): String {
    return when {
        minutes == 0 -> stringResource(R.string.format_duration_unlimited)
        minutes < 60 -> stringResource(R.string.format_duration_minutes, minutes)
        minutes % 60 == 0 -> pluralStringResource(R.plurals.format_duration_hours, minutes / 60, minutes / 60)
        else -> stringResource(R.string.format_duration_hours_minutes, minutes / 60, minutes % 60)
    }
}
