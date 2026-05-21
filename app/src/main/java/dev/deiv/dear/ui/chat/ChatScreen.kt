package dev.deiv.dear.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.deiv.dear.llm.ModelState
import dev.deiv.dear.llm.ModelStats

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val messages by viewModel.messages.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val stats by viewModel.stats.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(modelState) {
        if (modelState == ModelState.READY) viewModel.refreshStats()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (modelState) {
            ModelState.NOT_FOUND -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "No model installed.",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Open Settings to download or import a model.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
            }
            ModelState.LOADING -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Loading model...",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ModelState.ERROR -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        "Failed to load model.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            ModelState.READY -> {
                stats?.let { ModelStatsBar(it, onRefresh = { viewModel.refreshStats() }) }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message -> ChatBubble(message) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about your entries...") },
                maxLines = 3,
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.send(input.trim())
                        input = ""
                    }
                },
                enabled = modelState == ModelState.READY && input.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ModelStatsBar(stats: ModelStats, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "● Ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            Text(stats.modelName, style = MaterialTheme.typography.bodySmall)
            Text(
                "Size ${stats.modelSizeMB} MB · Process RAM ${stats.processRamMB} MB · Device ${stats.deviceAvailRamMB}/${stats.deviceTotalRamMB} MB free${if (stats.lowMemory) " · LOW" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.lastTokenCount > 0) {
                Text(
                    "Last gen: %d tok · %d ms · %.1f tok/s".format(
                        stats.lastTokenCount, stats.lastDurationMs, stats.tokensPerSec,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val color = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = color)) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
