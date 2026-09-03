// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
        // Cold start only. savedInstanceState is non-null on a rotation or a
        // process restore, and re-opening the microphone then would be a
        // surprise rather than a shortcut.
        val coldStart = savedInstanceState == null

        setContent {
            QNoteTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    QNoteApp(
                        coldStart = coldStart,
                        onShareText = ::shareText,
                        onCopyText = ::copyText,
                        onOpenUrl = ::openUrl,
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

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("qnote", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun QNoteApp(
    coldStart: Boolean,
    onShareText: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val viewModel: NotesViewModel = viewModel()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var openNoteId by remember { mutableStateOf<String?>(null) }
    var showingAbout by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val pebble = QNoteApplication.from(context).pebbleRepository

    // Storage Access Framework: the user picks where the file goes, and reads
    // it back from wherever they picked it up, rather than qnote reading or
    // writing anywhere on its own. Backup/restore is a deliberate export, not
    // a background sync.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.backupTo(context.contentResolver, it) } }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.restoreFrom(context.contentResolver, it) } }

    // Auto-select is disabled in PebbleRepository, so on first run the user
    // chooses which Pebble app may exchange their notes with us. The check
    // reads DataStore, so it runs in an effect rather than during composition.
    var askForPebbleApp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        askForPebbleApp = !pebble.hasSelectedPebbleApp()
    }
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

    LaunchedEffect(coldStart) {
        if (coldStart && viewModel.autoCapture && pebble.hasSelectedPebbleApp()) {
            viewModel.startCaptureOnWatch()
        }
    }

    if (showingAbout) {
        AboutScreen(
            onBack = { showingAbout = false },
            onOpenSource = { onOpenUrl(QNOTE_SOURCE_URL) },
        )
        return
    }

    val note = openNoteId?.let { viewModel.noteById(it) }
    if (note == null) {
        // Covers both "nothing open" and a note deleted while its screen was up.
        if (openNoteId != null) openNoteId = null
        NoteListScreen(
            viewModel = viewModel,
            onOpenNote = { openNoteId = it.id },
            onShareText = onShareText,
            onOpenAbout = { showingAbout = true },
            onBackup = { backupLauncher.launch("qnote-backup.json") },
            onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
        )
    } else {
        NoteDetailScreen(
            note = note,
            categories = categories,
            onBack = { openNoteId = null },
            onSave = { viewModel.updateText(note.id, it) },
            onSetCategory = { viewModel.setCategory(note.id, it) },
            onDelete = {
                viewModel.delete(listOf(note.id))
                openNoteId = null
            },
            onShareText = onShareText,
            onCopyText = onCopyText,
        )
    }
}
