package com.infinicada.focuspocus.ui.components

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.AppInfo
import kotlinx.coroutines.launch

/**
 * Full-screen multi-select app picker. Browsing is keyboard-free (alphabetical
 * sections + A-Z rail + category chips); search is opt-in via the top-bar icon.
 * The new selection is committed only when Done is tapped.
 */
@Composable
fun AppPickerDialog(
    installedApps: List<AppInfo>,
    title: String,
    initialSelection: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    AppPickerImpl(
        installedApps = installedApps,
        title = title,
        multiSelect = true,
        initialSelection = initialSelection.toSet(),
        onConfirm = onConfirm,
        onPick = null,
        onDismiss = onDismiss
    )
}

/** Full-screen single-select app picker: tapping a row picks it immediately. */
@Composable
fun SingleAppPickerDialog(
    installedApps: List<AppInfo>,
    title: String,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    selectedPackage: String? = null
) {
    AppPickerImpl(
        installedApps = installedApps,
        title = title,
        multiSelect = false,
        initialSelection = selectedPackage?.let { setOf(it) } ?: emptySet(),
        onConfirm = null,
        onPick = onPick,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppPickerImpl(
    installedApps: List<AppInfo>,
    title: String,
    multiSelect: Boolean,
    initialSelection: Set<String>,
    onConfirm: ((List<String>) -> Unit)?,
    onPick: ((AppInfo) -> Unit)?,
    onDismiss: () -> Unit
) {
    var selections by remember { mutableStateOf(initialSelection) }
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Int?>(null) }
    var showSelectedOnly by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val sortedApps = remember(installedApps) { installedApps.sortedBy { it.name.lowercase() } }

    // Letter groups for the unfiltered browse view; '#' bucket sorts first.
    val groups = remember(sortedApps) {
        sortedApps
            .groupBy { app ->
                val c = app.name.firstOrNull()?.uppercaseChar() ?: '#'
                if (c in 'A'..'Z') c else '#'
            }
            .toSortedMap(compareBy { if (it == '#') ' ' else it })
    }
    val headerIndices = remember(groups) {
        var index = 0
        buildMap {
            groups.forEach { (letter, apps) ->
                put(letter, index)
                index += 1 + apps.size
            }
        }
    }
    val categories = remember(installedApps) {
        installedApps.asSequence()
            .map { it.category }
            .filter { it != ApplicationInfo.CATEGORY_UNDEFINED }
            .distinct()
            .mapNotNull { cat ->
                ApplicationInfo.getCategoryTitle(context, cat)?.toString()?.let { cat to it }
            }
            .sortedBy { it.second }
            .toList()
    }

    val filterActive = query.isNotEmpty() || selectedCategory != null || showSelectedOnly
    val filteredApps = remember(sortedApps, query, selectedCategory, showSelectedOnly, selections) {
        if (query.isEmpty() && selectedCategory == null && !showSelectedOnly) sortedApps
        else sortedApps.filter { app ->
            (query.isEmpty() || app.name.contains(query, ignoreCase = true)) &&
                (selectedCategory == null || app.category == selectedCategory) &&
                (!showSelectedOnly || app.packageName in selections)
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun toggle(app: AppInfo) {
        if (multiSelect) {
            selections = if (app.packageName in selections) selections - app.packageName
            else selections + app.packageName
        } else {
            onPick?.invoke(app)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                if (searchActive) {
                    PickerSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = {
                            searchActive = false
                            query = ""
                        }
                    )
                } else {
                    PickerTopBar(
                        title = title,
                        selectionCount = if (multiSelect) selections.size else null,
                        onDismiss = onDismiss,
                        onSearch = { searchActive = true },
                        onDone = if (multiSelect) {
                            { onConfirm?.invoke(selections.toList()) }
                        } else null
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (multiSelect) {
                        FilterChip(
                            selected = showSelectedOnly,
                            onClick = { showSelectedOnly = !showSelectedOnly },
                            label = { Text(stringResource(R.string.app_picker_selected_chip, selections.size)) }
                        )
                    }
                    categories.forEach { (cat, label) ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(label) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (filteredApps.isEmpty()) {
                        Text(
                            stringResource(R.string.time_limits_no_apps_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    } else if (!filterActive) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(end = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            groups.forEach { (letter, apps) ->
                                stickyHeader(key = "header_$letter") {
                                    LetterHeader(letter)
                                }
                                items(apps, key = { it.packageName }) { app ->
                                    AppPickerRow(
                                        app = app,
                                        selected = app.packageName in selections,
                                        showCheckbox = multiSelect,
                                        onClick = { toggle(app) }
                                    )
                                }
                            }
                        }
                        AlphabetRail(
                            letters = groups.keys.toList(),
                            onLetterSelected = { letter ->
                                headerIndices[letter]?.let { index ->
                                    scope.launch { listState.scrollToItem(index) }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppPickerRow(
                                    app = app,
                                    selected = app.packageName in selections,
                                    showCheckbox = multiSelect,
                                    onClick = { toggle(app) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTopBar(
    title: String,
    selectionCount: Int?,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onDone: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selectionCount != null) {
                Text(
                    pluralStringResource(R.plurals.app_picker_selected_count, selectionCount, selectionCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.app_picker_open_search))
        }
        if (onDone != null) {
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}

@Composable
private fun PickerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.app_picker_close_search)
            )
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.app_picker_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.app_picker_clear_search)
                        )
                    }
                }
            } else null,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
    }
    // Focus (and thus the keyboard) appears only here, after the user
    // deliberately opened search -- never on picker open.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun AppPickerRow(
    app: AppInfo,
    selected: Boolean,
    showCheckbox: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            packageName = app.packageName,
            contentDescription = app.name,
            modifier = Modifier.size(40.dp)
        )
        Text(
            app.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
        if (showCheckbox) {
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

@Composable
private fun LetterHeader(letter: Char) {
    Text(
        letter.toString(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun AlphabetRail(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(24.dp)
            .pointerInputRail(letters, onLetterSelected),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                letter.toString(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun Modifier.pointerInputRail(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit
): Modifier = pointerInput(letters) {
    awaitEachGesture {
        val down = awaitFirstDown()
        fun pick(y: Float) {
            if (letters.isEmpty() || size.height <= 0) return
            val index = ((y / size.height) * letters.size).toInt()
                .coerceIn(0, letters.size - 1)
            onLetterSelected(letters[index])
        }
        pick(down.position.y)
        down.consume()
        drag(down.id) { change ->
            pick(change.position.y)
            change.consume()
        }
    }
}
