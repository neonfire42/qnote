package dev.neonfire.qnote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.neonfire.qnote.data.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    note: Note,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onShareText: (String) -> Unit,
    onCopyText: (String) -> Unit,
) {
    // Keyed on the note id so opening a different note resets the editor
    // instead of carrying the previous note's draft across.
    var text by remember(note.id) { mutableStateOf(note.text) }
    val dirty = text != note.text

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatAbsolute(note.capturedAt)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Save on the way out; nobody expects an edit to be
                        // thrown away by the back arrow.
                        if (dirty) onSave(text)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (dirty) {
                        IconButton(onClick = { onSave(text) }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                    IconButton(onClick = { onCopyText(text) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = { onShareText(text) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
            )

            val footnotes = buildList {
                add("Captured ${formatRelative(note.capturedAt)}")
                if (note.truncated) {
                    add("The watch cut this off at its 244-byte limit.")
                }
                if (note.edited) add("Edited on this phone.")
            }
            Text(
                text = footnotes.joinToString("\n"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
