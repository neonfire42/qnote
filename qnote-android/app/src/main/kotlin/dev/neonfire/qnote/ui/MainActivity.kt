package dev.neonfire.qnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neonfire.qnote.QNoteApplication
import io.rebble.pebblekit2.client.ui.PebbleAppPermissionDialog

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    QNoteApp(
                        onShareText = ::shareText,
                        onCopyText = ::copyText,
                    )
                }
            }
        }
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share note"))
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("qnote", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun QNoteApp(
    onShareText: (String) -> Unit,
    onCopyText: (String) -> Unit,
) {
    val viewModel: NotesViewModel = viewModel()
    var openNoteId by remember { mutableStateOf<String?>(null) }

    val pebble = QNoteApplication.from(
        androidx.compose.ui.platform.LocalContext.current
    ).pebbleRepository

    // Auto-select is disabled in PebbleRepository, so on first run the user
    // chooses which Pebble app may exchange their notes with us.
    var askForPebbleApp by remember { mutableStateOf(!pebble.hasSelectedPebbleApp()) }
    if (askForPebbleApp) {
        PebbleAppPermissionDialog(
            pebble.appPicker,
            onDismiss = { askForPebbleApp = false },
            rationaleText = {
                Text(
                    "qnote needs to talk to your Pebble app to receive the notes " +
                        "you dictate on your watch.",
                    Modifier.padding(top = 8.dp),
                )
            },
        )
    }

    val note = openNoteId?.let { viewModel.noteById(it) }
    if (note == null) {
        // Covers both "nothing open" and a note deleted while its screen was up.
        if (openNoteId != null) openNoteId = null
        NoteListScreen(
            viewModel = viewModel,
            onOpenNote = { openNoteId = it.id },
            onShareText = onShareText,
        )
    } else {
        NoteDetailScreen(
            note = note,
            onBack = { openNoteId = null },
            onSave = { viewModel.updateText(note.id, it) },
            onDelete = {
                viewModel.delete(listOf(note.id))
                openNoteId = null
            },
            onShareText = onShareText,
            onCopyText = onCopyText,
        )
    }
}
