package dev.deiv.dear.ui.entry

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.deiv.dear.R
import dev.deiv.dear.data.model.CatalogStatus
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.data.model.EntryWithTags
import dev.deiv.dear.ui.common.EntryImage
import dev.deiv.dear.ui.common.MonoLabel
import dev.deiv.dear.ui.common.PillButton
import dev.deiv.dear.ui.common.TagPills
import dev.deiv.dear.ui.common.VideoThumbnail
import dev.deiv.dear.ui.common.WorkingDots
import dev.deiv.dear.ui.common.appLabel
import dev.deiv.dear.ui.common.domainOf
import dev.deiv.dear.ui.common.formatDateTime
import dev.deiv.dear.ui.common.formatDuration
import dev.deiv.dear.ui.common.typeLabel
import dev.deiv.dear.ui.theme.NothingRed
import java.io.File

@Composable
fun EntryScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: EntryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val loaded = state as? EntryUiState.Loaded
    val item = loaded?.item

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (item != null) {
                IconButton(onClick = { share(context, item.entry) }) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.entry_share))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.entry_delete))
                }
            }
        }

        when (val s = state) {
            EntryUiState.Loading -> Unit
            EntryUiState.Missing -> MonoLabel(
                stringResource(R.string.entry_not_found),
                modifier = Modifier.padding(20.dp),
            )
            is EntryUiState.Loaded -> EntryContent(
                state = s,
                onOpen = { open(context, s.item.entry) },
                onOpenEntry = onOpenEntry,
                onRecatalog = viewModel::recatalog,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.entry_delete_title)) },
            text = {
                val photos = loaded?.photos?.size ?: 0
                Text(
                    if (photos > 0) pluralStringResource(R.plurals.entry_delete_body_photos, photos, photos)
                    else stringResource(R.string.entry_delete_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) {
                    Text(stringResource(R.string.entry_delete).uppercase(), color = NothingRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel).uppercase())
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun EntryContent(
    state: EntryUiState.Loaded,
    onOpen: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onRecatalog: () -> Unit,
) {
    val context = LocalContext.current
    val item = state.item
    val entry = item.entry

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MonoLabel(typeLabel(entry.type))
            val fromApp = entry.sourceApp?.let { stringResource(R.string.from_app, appLabel(context, it)) }
            MonoLabel(
                buildString {
                    append(formatDateTime(entry.createdAt))
                    entry.durationMs?.let { append(" · ").append(formatDuration(it)) }
                    fromApp?.let { append(" · ").append(it) }
                }
            )
        }

        when (entry.type) {
            EntryType.IMAGE -> entry.filePath?.let { path ->
                EntryImage(
                    path = path,
                    contentDescription = entry.title,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpen),
                )
            }
            EntryType.LINK -> entry.url?.let { url -> LinkCard(url = url, text = url, onClick = onOpen) }
            EntryType.TEXT -> Unit
            EntryType.VIDEO -> entry.filePath?.let { path ->
                VideoThumbnail(
                    path = path,
                    durationMs = entry.durationMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 420.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpen),
                )
            }
            EntryType.AUDIO, EntryType.FILE -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = entry.originalName ?: typeLabel(entry.type),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                PillButton(stringResource(R.string.entry_open), onClick = onOpen, outlined = true)
            }
        }

        if (state.photos.isNotEmpty()) {
            Section(stringResource(R.string.section_photos, state.photos.size)) {
                PhotoStrip(state.photos, onOpenEntry)
            }
        }

        state.parent?.let { link ->
            link.url?.let { url ->
                Section(stringResource(R.string.section_from_link)) {
                    LinkCard(url = url, text = link.title ?: url, onClick = { onOpenEntry(link.id) })
                }
            }
        }

        when (entry.type) {
            EntryType.TEXT -> entry.text?.let {
                SelectionContainer { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            else -> entry.title?.let {
                Text(it, style = MaterialTheme.typography.titleLarge)
            }
        }

        entry.summary?.let { Section(stringResource(R.string.section_summary)) { Text(it, style = MaterialTheme.typography.bodyLarge) } }

        if (item.tags.isNotEmpty()) {
            Section(stringResource(R.string.section_tags)) { TagPills(item.tags.map { it.name }) }
        }

        if (entry.type != EntryType.TEXT) {
            entry.text?.let { Section(stringResource(R.string.section_note)) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
        }

        entry.extractedText?.let { extracted ->
            val label = when (entry.type) {
                EntryType.LINK -> R.string.section_page
                EntryType.AUDIO, EntryType.VIDEO -> R.string.section_transcript
                else -> R.string.section_visible_text
            }
            Section(stringResource(label)) {
                SelectionContainer {
                    Text(
                        extracted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        CatalogSection(entry, onRecatalog)
    }
}

@Composable
private fun LinkCard(url: String, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MonoLabel(domainOf(url), color = NothingRed)
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<Entry>, onOpenEntry: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.id }) { photo ->
            photo.filePath?.let { path ->
                EntryImage(
                    path = path,
                    contentDescription = photo.title,
                    modifier = Modifier
                        .size(width = 120.dp, height = 150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenEntry(photo.id) },
                )
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonoLabel(label)
        content()
    }
}

@Composable
private fun CatalogSection(entry: Entry, onRecatalog: () -> Unit) {
    Section(stringResource(R.string.section_catalog)) {
        when (entry.status) {
            CatalogStatus.PENDING -> Text(
                stringResource(R.string.catalog_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CatalogStatus.PROCESSING -> Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WorkingDots()
                Text(stringResource(R.string.catalog_in_progress), style = MaterialTheme.typography.bodyMedium)
            }
            CatalogStatus.DONE -> Text(
                stringResource(
                    R.string.catalog_done,
                    entry.catalogedAt?.let { formatDateTime(it) } ?: "—",
                    entry.catalogModel?.removeSuffix(".gguf") ?: "—",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CatalogStatus.FAILED -> Text(
                stringResource(R.string.catalog_failed, entry.attempts, entry.lastError ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                color = NothingRed,
            )
        }
        if (entry.status == CatalogStatus.DONE || entry.status == CatalogStatus.FAILED) {
            PillButton(
                text = stringResource(R.string.entry_recatalog),
                onClick = onRecatalog,
                outlined = true,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun fileUri(context: Context, path: String): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))

private fun open(context: Context, entry: Entry) {
    val intent = when {
        entry.type == EntryType.LINK && entry.url != null -> Intent(Intent.ACTION_VIEW, Uri.parse(entry.url))
        entry.filePath != null -> Intent(Intent.ACTION_VIEW)
            .setDataAndType(fileUri(context, entry.filePath), entry.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        else -> return
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, entry: Entry) {
    val send = Intent(Intent.ACTION_SEND)
    if (entry.filePath != null) {
        send.type = entry.mimeType ?: "*/*"
        send.putExtra(Intent.EXTRA_STREAM, fileUri(context, entry.filePath))
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        entry.text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
    } else {
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, listOfNotNull(entry.text, entry.url).joinToString("\n"))
        entry.title?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    val chooser = Intent.createChooser(send, null).putExtra(
        Intent.EXTRA_EXCLUDE_COMPONENTS,
        // Sharing back into Dear would only duplicate the entry.
        arrayOf(ComponentName(context, "dev.deiv.dear.ui.share.ShareReceiverActivity")),
    )
    context.startActivity(chooser)
}
