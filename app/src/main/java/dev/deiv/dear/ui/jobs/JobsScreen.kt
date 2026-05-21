package dev.deiv.dear.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.deiv.dear.llm.JobRecord
import dev.deiv.dear.llm.JobStatus
import dev.deiv.dear.llm.ModelState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobsScreen(viewModel: JobsViewModel = viewModel()) {
    val current by viewModel.current.collectAsState()
    val pending by viewModel.pendingCount.collectAsState()
    val history by viewModel.history.collectAsState()
    val modelState by viewModel.modelState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Catalog Jobs", style = MaterialTheme.typography.headlineSmall)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Model: $modelState", style = MaterialTheme.typography.labelMedium)
                Text("Pending: $pending", style = MaterialTheme.typography.bodyMedium)
                val cur = current
                if (cur != null) {
                    Text(
                        "Running: entry ${cur.entryId} (${cur.type})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                } else if (pending > 0 && modelState != ModelState.READY) {
                    Text(
                        "Waiting for model to be ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (pending == 0) {
                    Text(
                        "Idle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            "History (last ${history.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        if (history.isEmpty()) {
            Text(
                "No jobs yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history) { rec -> JobRow(rec) }
        }
    }
}

@Composable
private fun JobRow(rec: JobRecord) {
    val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val (label, color) = when (rec.status) {
        JobStatus.SUCCESS -> "✓" to MaterialTheme.colorScheme.primary
        JobStatus.FAILED -> "✗" to MaterialTheme.colorScheme.error
        JobStatus.SKIPPED -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "$label  #${rec.entryId} ${rec.type ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
                Text(
                    "${timeFmt.format(Date(rec.startedAt))} · ${rec.durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rec.message.isNotBlank()) {
                Text(
                    rec.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
