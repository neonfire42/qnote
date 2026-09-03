// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import dev.neonfire.qnote.QNoteApplication
import dev.neonfire.qnote.data.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A message for the snackbar.
 *
 * [undoId] is set only for a swipe delete, which is held back for a few seconds
 * so it can be taken back. Everything else is a plain report.
 */
data class Snack(val text: String, val undoId: String? = null)

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val store = QNoteApplication.from(app).noteStore
    private val pebble = QNoteApplication.from(app).pebbleRepository
    private val settings = QNoteApplication.from(app).settings
    private val slots = QNoteApplication.from(app).categorySlots

    /**
     * For work that must finish even though this view model is going away —
     * committing a delete whose undo window never closed. [viewModelScope] is
     * cancelled in [onCleared], which is precisely when that work starts.
     */
    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _categoryFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    val categoryFilter: StateFlow<CategoryFilter> = _categoryFilter.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _message = MutableStateFlow<Snack?>(null)
    val message: StateFlow<Snack?> = _message.asStateFlow()

    /**
     * Swipe-deleted notes whose undo window is still open. They are gone from
     * the list but still in the database, and nothing has been said to the
     * watch yet — an undo has to leave no trace of a half-finished delete.
     */
    private val _pendingDelete = MutableStateFlow<Set<String>>(emptySet())
    private val pendingJobs = mutableMapOf<String, Job>()

    /** Notes matching the current search and category, newest first. */
    val notes: StateFlow<List<Note>> =
        combine(
            store.notes,
            _query,
            _categoryFilter,
            _pendingDelete,
        ) { notes, query, filter, pending ->
            notes
                .filterNot { it.id in pending }
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

        // Keep the watch's copy of the list current: a category created here
        // should be offerable on the watch next time it opens, and the slot
        // table has to learn the name either way.
        viewModelScope.launch {
            categories.collect { pushCategoriesToWatch() }
        }
    }

    /**
     * Sends the watch the categories it should offer.
     *
     * Also assigns slots to any new name, which is why it runs even with no
     * watch reachable — the slot table is what makes a number sent back days
     * later still mean the right category.
     */
    private fun pushCategoriesToWatch() {
        pebble.pushCategories(
            slots.blobFor(categoriesByRecentUse()),
            settings.askCategoryOnWatch,
        )
    }

    /**
     * Category names, most recently used first. The watch has room for a
     * handful, and the one you reached for last is the one you are most likely
     * to reach for now.
     */
    private fun categoriesByRecentUse(): List<String> =
        store.notes.value
            .mapNotNull { note -> note.category?.let { it to note.capturedAt } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.max() }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

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
            _message.value = Snack(
                when {
                    category == null -> "Category cleared"
                    ids.size == 1 -> "Moved to $category"
                    else -> "${ids.size} notes moved to $category"
                },
            )
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
            _message.value =
                Snack(if (doomed.size == 1) "Note deleted" else "${doomed.size} notes deleted")
        }
    }

    /**
     * Deletes a swiped note, but not yet.
     *
     * The row leaves the list at once while the database row and the watch's
     * cached copy stay put until the undo window closes. Deleting is
     * destructive on both sides — it also drops the note from the watch's
     * 12-slot cache — and a swipe is easy to do by accident, so nothing
     * irreversible happens until the offer expires.
     */
    fun deleteWithUndo(id: String) {
        if (id in _pendingDelete.value) return

        _pendingDelete.value = _pendingDelete.value + id
        pendingJobs[id] = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            commitPending(id)
        }
        _message.value = Snack("Note deleted", undoId = id)
    }

    fun undoDelete(id: String) {
        pendingJobs.remove(id)?.cancel()
        _pendingDelete.value = _pendingDelete.value - id
    }

    private suspend fun commitPending(id: String) {
        val doomed = noteById(id)
        withContext(Dispatchers.IO) { store.delete(id) }
        doomed?.let { pebble.deleteOnWatch(it.recordId) }
        pendingJobs.remove(id)
        _pendingDelete.value = _pendingDelete.value - id
    }

    override fun onCleared() {
        super.onCleared()
        // Leaving the app is not an undo. viewModelScope is already cancelled
        // by now, so the commit runs on a scope that outlives it.
        val outstanding = _pendingDelete.value
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        if (outstanding.isEmpty()) return

        val doomed = outstanding.mapNotNull { noteById(it) }
        detachedScope.launch {
            store.delete(outstanding)
            doomed.forEach { pebble.deleteOnWatch(it.recordId) }
        }
    }

    /**
     * Opens qnote on the watch and starts dictation there. Normally this is the
     * "Speak on watch" button; it also fires once on a cold start of this app
     * for anyone who has opted into [autoCapture].
     *
     * The category list rides in the same message as the start request, so the
     * watch has it before the microphone opens.
     */
    fun startCaptureOnWatch() {
        pebble.startCaptureOnWatch(
            categories = slots.blobFor(categoriesByRecentUse()),
            askCategory = settings.askCategoryOnWatch,
        ) { started ->
            _message.value =
                Snack(if (started) "Listening on your watch" else "No watch reachable")
        }
    }

    /** Whether opening this app should start dictation on the watch. */
    val autoCapture: Boolean
        get() = settings.autoCapture

    fun setAutoCapture(enabled: Boolean) {
        settings.autoCapture = enabled
        _message.value = Snack(
            if (enabled) "Will start dictation when qnote opens" else "Auto-dictation off",
        )
    }

    /** Whether the watch offers a category picker after a note is confirmed. */
    val askCategoryOnWatch: Boolean
        get() = settings.askCategoryOnWatch

    fun setAskCategoryOnWatch(enabled: Boolean) {
        settings.askCategoryOnWatch = enabled
        pushCategoriesToWatch()
        _message.value = Snack(
            if (enabled) "The watch will ask for a category" else "The watch will not ask",
        )
    }

    fun syncNow() {
        pebble.requestSync()
        _message.value = Snack("Asked the watch to resend")
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

    private companion object {
        /** How long a swiped note can still be taken back. */
        const val UNDO_WINDOW_MS = 5_000L
    }
}
