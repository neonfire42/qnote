// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import dev.neonfire.qnote.QNoteApplication
import dev.neonfire.qnote.data.Note
import dev.neonfire.qnote.data.NoteBackup
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
                .filter { matchesQuery(it, query) }
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

    /**
     * Whether [note] matches free-text search [query].
     *
     * Checked against the category too, not just the body: the chip row above
     * the list narrows by category already, but someone typing "errands" into
     * the search box has no reason to expect it to fail just because the word
     * itself is not written anywhere in the note.
     *
     * `internal` rather than `private` so [NotesViewModelTest] can exercise it
     * directly, without needing to drive the reactive [notes] flow through its
     * `WhileSubscribed` collection lifecycle for what is really a pure
     * function of one note and one string.
     */
    internal fun matchesQuery(note: Note, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        return note.text.contains(trimmed, ignoreCase = true) ||
            note.category?.contains(trimmed, ignoreCase = true) == true
    }

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

    /**
     * Saves text shared in from another app -- "Share" on a link, a selection,
     * anything with a text/plain target -- as a new note.
     *
     * A note like this never came from a watch, so it has no watch record id
     * to key off. It gets a watch id of its own ([SHARED_WATCH_ID]) and the
     * current time in milliseconds as its record id: unique enough for
     * something a person triggers by hand through a share sheet, and it can
     * never collide with a real watch's ids since those live under their own
     * watch id entirely.
     */
    fun saveSharedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            store.upsert(
                Note(
                    id = Note.idFor(SHARED_WATCH_ID, now),
                    watchId = SHARED_WATCH_ID,
                    recordId = now,
                    text = trimmed,
                    capturedAt = now / 1000,
                    receivedAt = now,
                    truncated = false,
                    edited = false,
                ),
            )
            _message.value = Snack("Saved to qnote")
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
    /**
     * Writes every note to [uri] as JSON, in the format [NoteBackup] can read
     * back. Unlike Markdown export this round-trips: ids, sync flags and the
     * category slot table all survive, which is what makes "Restore notes"
     * onto a new phone actually equivalent to the one it replaces.
     */
    fun backupTo(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = NoteBackup.encode(
                notes = store.notes.value,
                categorySlots = slots.allNames(),
                autoCapture = settings.autoCapture,
                askCategoryOnWatch = settings.askCategoryOnWatch,
            )
            val wrote = runCatching {
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            _message.value = Snack(if (wrote) "Notes backed up" else "Could not write that file")
        }
    }

    /**
     * Reads a backup from [uri] and merges it in.
     *
     * Merges rather than replaces, through the same [dev.neonfire.qnote.data.NoteStore.upsert]
     * the watch's own datalog replay uses: a note already here — even one
     * edited since the backup was taken — is left alone rather than
     * overwritten, so restoring is always safe to run more than once.
     */
    fun restoreFrom(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = runCatching {
                resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                _message.value = Snack("Could not read that file")
                return@launch
            }

            val backup = try {
                NoteBackup.decode(text)
            } catch (e: NoteBackup.FormatException) {
                _message.value = Snack(e.message ?: "That file is not a qnote backup.")
                return@launch
            }

            slots.restoreTable(backup.categorySlots)
            val added = backup.notes.count { store.upsert(it) }
            backup.autoCapture?.let { settings.autoCapture = it }
            backup.askCategoryOnWatch?.let { settings.askCategoryOnWatch = it }
            // The restored table, or newly-learned names, may not be what the
            // watch currently has.
            pushCategoriesToWatch()

            val skipped = backup.notes.size - added
            _message.value = Snack(
                when {
                    backup.notes.isEmpty() -> "That backup had no notes"
                    skipped == 0 -> "Restored $added notes"
                    added == 0 -> "Already had all $skipped notes"
                    else -> "Restored $added notes, $skipped already here"
                },
            )
        }
    }

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

        /** Watch id for a note that came from Android's share sheet, not a watch. */
        const val SHARED_WATCH_ID = "phone"
    }
}
