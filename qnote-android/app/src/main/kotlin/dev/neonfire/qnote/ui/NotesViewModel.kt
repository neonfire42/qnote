// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val store = QNoteApplication.from(app).noteStore
    private val pebble = QNoteApplication.from(app).pebbleRepository
    private val settings = QNoteApplication.from(app).settings

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _categoryFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    val categoryFilter: StateFlow<CategoryFilter> = _categoryFilter.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Notes matching the current search and category, newest first. */
    val notes: StateFlow<List<Note>> =
        combine(store.notes, _query, _categoryFilter) { notes, query, filter ->
            notes
                .filter { filter.matches(it) }
                .filter {
                    query.isBlank() || it.text.contains(query.trim(), ignoreCase = true)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Every category currently in use, alphabetically. Categories are not a
     * separate table — a category exists exactly as long as a note carries it,
     * so deleting the last note in one retires it without any cleanup.
     */
    val categories: StateFlow<List<String>> = store.notes.map { notes ->
        notes.mapNotNull { it.category }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when at least one note has no category, so the chip is worth showing. */
    val hasUncategorised: StateFlow<Boolean> = store.notes.map { notes ->
        notes.any { it.category == null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setCategoryFilter(filter: CategoryFilter) {
        _categoryFilter.value = filter
    }

    init {
        // Deleting the last note in a category retires that category. Without
        // this the filter would keep pointing at a name that no longer exists
        // and the list would sit empty for no visible reason.
        viewModelScope.launch {
            categories.collect { available ->
                val current = _categoryFilter.value
                if (current is CategoryFilter.Named && current.category !in available) {
                    _categoryFilter.value = CategoryFilter.All
                }
            }
        }
    }

    fun setCategory(id: String, category: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.updateCategory(id, category) }
        }
    }

    /** Categorises everything currently selected in one go. */
    fun setCategoryForSelection(category: String?) {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ids.forEach { store.updateCategory(it, category) } }
            _selection.value = emptySet()
            _message.value = when {
                category == null -> "Category cleared"
                ids.size == 1 -> "Moved to $category"
                else -> "${ids.size} notes moved to $category"
            }
        }
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

    /**
     * Opens qnote on the watch and starts dictation there. Also fired once on a
     * cold start of this app when [autoCapture] is on, which is the whole point
     * of the setting: open the phone app, start talking.
     */
    fun startCaptureOnWatch() {
        pebble.startCaptureOnWatch { started ->
            _message.value =
                if (started) "Listening on your watch" else "No watch reachable"
        }
    }

    /** Whether opening this app should start dictation on the watch. */
    val autoCapture: Boolean
        get() = settings.autoCapture

    fun setAutoCapture(enabled: Boolean) {
        settings.autoCapture = enabled
        _message.value =
            if (enabled) "Will start dictation when qnote opens" else "Auto-dictation off"
    }

    fun syncNow() {
        pebble.requestSync()
        _message.value = "Asked the watch to resend"
    }

    fun messageShown() {
        _message.value = null
    }

    /**
     * All notes as Markdown, grouped under their category so the export keeps
     * the structure the user built rather than flattening it back to a list.
     */
    fun exportMarkdown(): String = buildString {
        appendLine("# qnote")
        appendLine()
        store.notes.value
            .groupBy { it.category }
            .toSortedMap(nullsLast(naturalOrder<String>()))
            .forEach { (category, notes) ->
                appendLine("## ${category ?: "Uncategorised"}")
                appendLine()
                notes.forEach { note ->
                    appendLine("### ${formatAbsolute(note.capturedAt)}")
                    appendLine()
                    appendLine(note.text)
                    appendLine()
                }
            }
    }
}
