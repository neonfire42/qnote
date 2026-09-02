package dev.neonfire.qnote.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import dev.neonfire.qnote.QNoteApplication
import dev.neonfire.qnote.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val store = QNoteApplication.from(app).noteStore
    private val pebble = QNoteApplication.from(app).pebbleRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Notes matching the current search, newest first. */
    val notes: StateFlow<List<Note>> = combine(store.notes, _query) { notes, query ->
        if (query.isBlank()) notes
        else notes.filter { it.text.contains(query.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allNotes: StateFlow<List<Note>> = store.notes

    fun setQuery(value: String) {
        _query.value = value
    }

    fun noteById(id: String): Note? = store.notes.value.firstOrNull { it.id == id }

    fun toggleSelected(id: String) {
        _selection.value = _selection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun updateText(id: String, text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.updateText(id, text) }
        }
    }

    fun delete(ids: Collection<String>) {
        if (ids.isEmpty()) return
        // Capture the record ids before deleting; afterwards the rows are gone
        // and the watch would never hear about it.
        val doomed = ids.mapNotNull { noteById(it) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(ids) }
            doomed.forEach { pebble.deleteOnWatch(it.recordId) }
            _selection.value = _selection.value - ids.toSet()
            _message.value = if (doomed.size == 1) "Note deleted" else "${doomed.size} notes deleted"
        }
    }

    fun openOnWatch() {
        pebble.openOnWatch { started ->
            _message.value = if (started) "qnote opened on your watch" else "No watch reachable"
        }
    }

    fun syncNow() {
        pebble.requestSync()
        _message.value = "Asked the watch to resend"
    }

    fun messageShown() {
        _message.value = null
    }

    /** All notes as Markdown, for sharing or backing up somewhere else. */
    fun exportMarkdown(): String = buildString {
        appendLine("# qnote")
        appendLine()
        store.notes.value.forEach { note ->
            appendLine("## ${formatAbsolute(note.capturedAt)}")
            appendLine()
            appendLine(note.text)
            appendLine()
        }
    }
}
