package dev.deiv.dear.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.deiv.dear.R
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.data.model.EntryWithTags
import dev.deiv.dear.ui.common.CatalogStatusLabel
import dev.deiv.dear.ui.common.EntryImage
import dev.deiv.dear.ui.common.MonoLabel
import dev.deiv.dear.ui.common.PillButton
import dev.deiv.dear.ui.common.TagPills
import dev.deiv.dear.ui.common.VideoThumbnail
import dev.deiv.dear.ui.common.WorkingDots
import dev.deiv.dear.ui.common.domainOf
import dev.deiv.dear.ui.common.formatDay
import dev.deiv.dear.ui.common.formatDuration
import dev.deiv.dear.ui.common.formatTime
import dev.deiv.dear.ui.common.localDate
import dev.deiv.dear.ui.common.typeLabel
import dev.deiv.dear.ui.theme.NothingRed

@Composable
fun CollectionScreen(
    onOpenEntry: (Long) -> Unit,
    viewModel: CollectionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val pending by viewModel.pendingCount.collectAsState()
    var showCapture by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { AddButton(onClick = { showCapture = true }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") { SearchField(state.query, viewModel::setQuery) }
            if (state.availableTypes.size > 1) {
                item(key = "filters") { FilterRow(state.filter, state.availableTypes, viewModel::setFilter) }
            }
            if (pending > 0) {
                item(key = "catalog") { CatalogBanner(pending) }
            }

            when {
                !state.loaded -> Unit
                state.totalCount == 0 -> item(key = "empty") { EmptyState() }
                state.items.isEmpty() -> item(key = "none") {
                    MonoLabel(stringResource(R.string.no_results), modifier = Modifier.padding(top = 24.dp))
                }
                else -> state.items.groupBy { localDate(it.entry.createdAt) }.forEach { (day, entries) ->
                    item(key = "day-$day") { DayHeader(formatDay(context, day)) }
                    items(entries, key = { it.entry.id }) { item ->
                        EntryCard(
                            item = item,
                            photos = state.photos[item.entry.id].orEmpty(),
                            onClick = { onOpenEntry(item.entry.id) },
                        )
                    }
                }
            }
        }
    }

    if (showCapture) {
        CaptureSheet(
            onDismiss = { showCapture = false },
            onSave = {
                viewModel.addNote(it)
                showCapture = false
            },
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = NothingRed,
        ),
    )
}

@Composable
private fun FilterRow(selected: TypeFilter, available: Set<EntryType>, onSelect: (TypeFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TypeFilter.entries
            .filter { it.type == null || it.type in available }
            .forEach { filter ->
                val isSelected = filter == selected
                val colors = MaterialTheme.colorScheme
                Text(
                    text = filterLabel(filter).uppercase(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.background(colors.primary)
                            else Modifier.border(1.dp, colors.outline, CircleShape)
                        )
                        .clickable { onSelect(filter) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.onPrimary else colors.onSurface,
                )
            }
    }
}

@Composable
private fun filterLabel(filter: TypeFilter): String = stringResource(
    when (filter.type) {
        null -> R.string.filter_all
        EntryType.IMAGE -> R.string.filter_image
        EntryType.LINK -> R.string.filter_link
        EntryType.TEXT -> R.string.filter_text
        EntryType.VIDEO -> R.string.filter_video
        EntryType.AUDIO -> R.string.filter_audio
        EntryType.FILE -> R.string.filter_file
    }
)

@Composable
private fun CatalogBanner(pending: Int) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkingDots()
        MonoLabel(pluralStringResource(R.plurals.catalog_pending, pending, pending))
    }
}

@Composable
private fun DayHeader(label: String) {
    Row(
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(label)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EntryCard(item: EntryWithTags, photos: List<Entry>, onClick: () -> Unit) {
    val entry = item.entry
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel(
                    text = when {
                        entry.type == EntryType.LINK && entry.url != null -> domainOf(entry.url)
                        entry.durationMs != null -> typeLabel(entry.type) + " · " + formatDuration(entry.durationMs)
                        else -> typeLabel(entry.type)
                    },
                    modifier = Modifier.weight(1f),
                )
                CatalogStatusLabel(entry.status, modifier = Modifier.padding(end = 10.dp))
                MonoLabel(formatTime(entry.createdAt))
            }

            when (entry.type) {
                EntryType.IMAGE -> entry.filePath?.let { path ->
                    EntryImage(
                        path = path,
                        contentDescription = entry.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
                EntryType.VIDEO -> entry.filePath?.let { path ->
                    VideoThumbnail(
                        path = path,
                        durationMs = entry.durationMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
                EntryType.LINK -> if (photos.isNotEmpty()) PhotoPreview(photos)
                EntryType.TEXT -> entry.text?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> Unit
            }

            // Notes speak for themselves; everything else gets a title line.
            val title = when (entry.type) {
                EntryType.TEXT -> null
                EntryType.LINK -> entry.title ?: entry.text ?: entry.url
                else -> entry.title ?: entry.originalName
            }
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TagPills(item.tags.take(4).map { it.name })
        }
    }
}

/** The first photos of a post side by side; the last one counts those left out. */
@Composable
private fun PhotoPreview(photos: List<Entry>) {
    val shown = photos.take(PREVIEW_PHOTOS)
    val more = photos.size - shown.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (shown.size == 1) 220.dp else 150.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shown.forEachIndexed { i, photo ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                photo.filePath?.let { path ->
                    EntryImage(path = path, contentDescription = photo.title, modifier = Modifier.fillMaxSize())
                }
                if (more > 0 && i == shown.lastIndex) {
                    Text(
                        text = "+$more",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.empty_title).uppercase(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(NothingRed)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = stringResource(R.string.action_new),
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureSheet(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.capture_title).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
            )
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.capture_hint)) },
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NothingRed,
                ),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                PillButton(
                    text = stringResource(R.string.capture_save),
                    onClick = { onSave(text) },
                    accent = true,
                    enabled = text.isNotBlank(),
                )
            }
        }
    }
}

private const val PREVIEW_PHOTOS = 3
