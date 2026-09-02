package dev.neonfire.qnote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neonfire.qnote.data.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NotesViewModel,
    onOpenNote: (Note) -> Unit,
    onShareText: (String) -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val hasUncategorised by viewModel.hasUncategorised.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()

    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var categorising by remember { mutableStateOf(false) }
    var autoCapture by remember { mutableStateOf(viewModel.autoCapture) }
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    if (categorising) {
        CategoryPickerDialog(
            existing = categories,
            current = null,
            onDismiss = { categorising = false },
            onPick = {
                viewModel.setCategoryForSelection(it)
                categorising = false
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            if (selection.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selection.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { categorising = true }) {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Categorise selected")
                        }
                        IconButton(onClick = { viewModel.delete(selection) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("qnote") },
                    actions = {
                        IconButton(onClick = { searching = !searching }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = viewModel::syncNow) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync now")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export all as Markdown") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onShareText(viewModel.exportMarkdown())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Dictate when app opens") },
                                leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) },
                                trailingIcon = {
                                    Switch(checked = autoCapture, onCheckedChange = null)
                                },
                                onClick = {
                                    autoCapture = !autoCapture
                                    viewModel.setAutoCapture(autoCapture)
                                    menuOpen = false
                                },
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::startCaptureOnWatch,
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                text = { Text("Speak on watch") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (categories.isNotEmpty() || hasUncategorised) {
                CategoryChipRow(
                    categories = categories,
                    hasUncategorised = hasUncategorised,
                    selected = categoryFilter,
                    onSelect = viewModel::setCategoryFilter,
                )
            }

            if (notes.isEmpty()) {
                EmptyState(
                    filtered = query.isNotBlank() || categoryFilter !is CategoryFilter.All,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            selected = note.id in selection,
                            selectionActive = selection.isNotEmpty(),
                            onClick = {
                                if (selection.isNotEmpty()) viewModel.toggleSelected(note.id)
                                else onOpenNote(note)
                            },
                            onLongClick = { viewModel.toggleSelected(note.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Card's own onClick has no long-press variant, so the gesture goes on the
    // modifier and the card stays a plain container.
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatRelative(note.capturedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val marks = buildList {
                    note.category?.let { add(it) }
                    if (note.truncated) add("truncated on watch")
                    if (note.edited) add("edited")
                }
                if (marks.isNotEmpty()) {
                    Text(
                        text = marks.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (filtered) "Nothing in this view" else "No notes yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (!filtered) {
                Text(
                    text = "Speak into your watch, or tap below to\nstart dictation from here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryChipRow(
    categories: List<String>,
    hasUncategorised: Boolean,
    selected: CategoryFilter,
    onSelect: (CategoryFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected is CategoryFilter.All,
            onClick = { onSelect(CategoryFilter.All) },
            label = { Text("All") },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selected == CategoryFilter.Named(category),
                onClick = { onSelect(CategoryFilter.Named(category)) },
                label = { Text(category) },
            )
        }
        if (hasUncategorised) {
            FilterChip(
                selected = selected is CategoryFilter.Uncategorised,
                onClick = { onSelect(CategoryFilter.Uncategorised) },
                label = { Text("Uncategorised") },
            )
        }
    }
}
