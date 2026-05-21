package dev.deiv.dear.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.deiv.dear.DearApp
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var noteText by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("New Entry", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = noteText,
            onValueChange = {
                noteText = it
                saved = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            placeholder = { Text("Write a note...") },
        )

        Button(
            onClick = {
                scope.launch {
                    val repo = (context.applicationContext as DearApp).entryRepository
                    repo.addText(noteText.trim())
                    noteText = ""
                    saved = true
                }
            },
            enabled = noteText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Note")
        }

        if (saved) {
            Text(
                "Note saved!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TODO: Camera capture button
        Button(
            onClick = { /* TODO: Launch CameraX capture */ },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Take Photo")
        }
    }
}
