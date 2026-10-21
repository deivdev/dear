package dev.deiv.dear.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.deiv.dear.R
import dev.deiv.dear.data.model.ChatSession
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.llm.ModelState
import dev.deiv.dear.ui.common.Dot
import dev.deiv.dear.ui.common.EntryImage
import dev.deiv.dear.ui.common.MonoLabel
import dev.deiv.dear.ui.common.PillButton
import dev.deiv.dear.ui.common.WorkingDots
import dev.deiv.dear.ui.common.domainOf
import dev.deiv.dear.ui.common.formatDateTime
import dev.deiv.dear.ui.common.typeLabel

@Composable
fun AskScreen(
    onOpenSettings: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: AskViewModel = viewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val streaming by viewModel.streaming.collectAsState()
    val status by viewModel.modelStatus.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var showSessions by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val generating = streaming != null

    // Follow the reply as it streams in.
    val itemCount = messages.size + if (generating) 1 else 0
    LaunchedEffect(itemCount, streaming?.text?.length, streaming?.refs?.size) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Title and gear live in the shared top bar; this row is about the conversation.
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(
                text = sessions.firstOrNull { it.id == sessionId }?.title ?: stringResource(R.string.ask_new_session),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            IconButton(onClick = { showSessions = true }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.ask_sessions))
            }
            IconButton(onClick = viewModel::newSession) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ask_new_session))
            }
        }

        ModelStatusLine(status, onOpenSettings)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (messages.isEmpty() && !generating && status.state != ModelState.NOT_INSTALLED) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.ask_empty),
                        modifier = Modifier.padding(top = 24.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                if (message.isUser) {
                    UserBubble(message.text)
                } else {
                    DearReply(message.text, message.refs, message.isError, streaming = false, onOpenEntry)
                }
            }
            streaming?.let { reply ->
                item(key = "streaming") {
                    DearReply(reply.text, reply.refs, isError = false, streaming = true, onOpenEntry)
                }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            generating = generating,
            enabled = status.state != ModelState.NOT_INSTALLED && status.state != ModelState.LOADING,
            onSend = {
                viewModel.send(input)
                input = ""
            },
            onStop = viewModel::stop,
        )
    }

    if (showSessions) {
        SessionsSheet(
            sessions = sessions,
            currentId = sessionId,
            onOpen = {
                viewModel.openSession(it)
                showSessions = false
            },
            onDelete = viewModel::deleteSession,
            onDismiss = { showSessions = false },
        )
    }
}

/** Which model answers and how fast: "● gemma-4-E2B-it-Q4_0 · 14.2 tok/s". */
@Composable
private fun ModelStatusLine(status: ModelStatus, onOpenSettings: () -> Unit) {
    if (status.state == ModelState.NOT_INSTALLED) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MonoLabel(stringResource(R.string.model_missing_title), color = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.model_missing_body), style = MaterialTheme.typography.bodyMedium)
                PillButton(stringResource(R.string.open_settings), onClick = onOpenSettings)
            }
        }
        return
    }
    val name = status.modelName ?: "—"
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status.state) {
            ModelState.LOADING -> WorkingDots(dotSize = 4.dp)
            ModelState.READY -> Dot(MaterialTheme.colorScheme.tertiary, size = 5.dp)
            ModelState.ERROR -> Dot(MaterialTheme.colorScheme.error, size = 5.dp)
            else -> Dot(MaterialTheme.colorScheme.onSurfaceVariant, size = 5.dp)
        }
        val text = when (status.state) {
            ModelState.LOADING -> stringResource(R.string.model_status_loading, name)
            ModelState.ERROR -> stringResource(R.string.model_error)
            else -> status.tokensPerSec?.let { stringResource(R.string.model_status_speed, name, it) }
                ?: stringResource(R.string.model_status_idle, name)
        }
        MonoLabel(text, color = if (status.state == ModelState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun DearReply(
    text: String,
    refs: List<Entry>,
    isError: Boolean,
    streaming: Boolean,
    onOpenEntry: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonoLabel(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.tertiary)
        if (text.isEmpty() && streaming) {
            WorkingDots(modifier = Modifier.padding(vertical = 6.dp))
        } else {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (refs.isNotEmpty()) {
            MonoLabel(stringResource(R.string.ask_references), modifier = Modifier.padding(top = 4.dp))
            refs.forEach { ReferenceCard(it, onClick = { onOpenEntry(it.id) }) }
        }
    }
}

@Composable
private fun ReferenceCard(entry: Entry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.type == EntryType.IMAGE && entry.filePath != null) {
                EntryImage(
                    path = entry.filePath,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MonoLabel(
                    if (entry.type == EntryType.LINK && entry.url != null) domainOf(entry.url) else typeLabel(entry.type),
                )
                Text(
                    text = entry.title ?: entry.text ?: entry.url ?: entry.originalName ?: "#${entry.id}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.ask_hint)) },
            maxLines = 4,
            enabled = enabled,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.tertiary,
            ),
        )
        val canSend = enabled && value.isNotBlank()
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (generating || canSend) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = generating || canSend, onClick = if (generating) onStop else onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (generating) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.ask_send),
                tint = if (generating || canSend) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsSheet(
    sessions: List<ChatSession>,
    currentId: Long?,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.ask_sessions),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (sessions.isEmpty()) {
                MonoLabel(stringResource(R.string.ask_no_sessions), modifier = Modifier.padding(vertical = 12.dp))
            }
            LazyColumn {
                items(sessions, key = { it.id }) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpen(session.id) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(if (session.id == currentId) MaterialTheme.colorScheme.tertiary else Color.Transparent)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                session.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MonoLabel(formatDateTime(session.updatedAt))
                        }
                        IconButton(onClick = { onDelete(session.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.ask_delete_session),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
