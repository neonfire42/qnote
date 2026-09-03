// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import dev.neonfire.qnote.QNoteApplication
import dev.neonfire.qnote.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for [NotesViewModel] logic that does not need a running Activity.
 *
 * [matchesQuery] is checked directly against the function rather than the
 * reactive [NotesViewModel.notes] flow it feeds: that flow is a
 * `stateIn(..., WhileSubscribed(5_000), ...)`, and driving its collection
 * lifecycle just to check a string match would test Compose/coroutine
 * plumbing that [ui.NoteListScreen] already exercises, not the predicate
 * itself.
 */
@RunWith(RobolectricTestRunner::class)
class NotesViewModelTest {

    private lateinit var app: QNoteApplication
    private lateinit var viewModel: NotesViewModel

    private fun note(text: String, category: String? = null) = Note(
        id = "watch-1:1",
        watchId = "watch-1",
        recordId = 1,
        text = text,
        capturedAt = 0,
        receivedAt = 0,
        truncated = false,
        edited = false,
        category = category,
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication() as QNoteApplication
        viewModel = NotesViewModel(app)
    }

    /**
     * Waits for a note matching [text] to appear in the store.
     *
     * [NotesViewModel.saveSharedText] writes on `Dispatchers.IO`, a real
     * background thread outside any test scheduler, so the alternative to a
     * short poll is injecting a dispatcher into the view model for this one
     * call path. Given how fast Robolectric's SQLite is, the poll resolves in
     * a handful of milliseconds in practice; [timeoutMs] is the failure mode,
     * not the expected path.
     */
    private fun awaitNoteContaining(text: String, timeoutMs: Long = 2_000): Note {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            app.noteStore.notes.value.firstOrNull { it.text == text }?.let { return it }
            Thread.sleep(20)
        }
        error("no note with text \"$text\" appeared within ${timeoutMs}ms")
    }

    @Test
    fun `a blank query matches everything`() {
        assertTrue(viewModel.matchesQuery(note("buy oat milk"), ""))
        assertTrue(viewModel.matchesQuery(note("buy oat milk"), "   "))
    }

    @Test
    fun `matches text in the note body, as before`() {
        assertTrue(viewModel.matchesQuery(note("buy oat milk"), "oat"))
        assertFalse(viewModel.matchesQuery(note("buy oat milk"), "dentist"))
    }

    @Test
    fun `matches the note's category, not just its body`() {
        val note = note("ring the plumber back", category = "Errands")

        assertTrue(viewModel.matchesQuery(note, "errands"))
    }

    @Test
    fun `category matching is case-insensitive, same as text matching`() {
        val note = note("ring the plumber back", category = "Errands")

        assertTrue(viewModel.matchesQuery(note, "ERRANDS"))
    }

    @Test
    fun `an uncategorised note does not match on category`() {
        val note = note("buy oat milk", category = null)

        assertFalse(viewModel.matchesQuery(note, "errands"))
    }

    @Test
    fun `a query matching neither text nor category fails`() {
        val note = note("buy oat milk", category = "Errands")

        assertFalse(viewModel.matchesQuery(note, "dentist"))
    }

    @Test
    fun `shared text is saved under the phone's own watch id, trimmed`() {
        viewModel.saveSharedText("  buy oat milk  ")

        val saved = awaitNoteContaining("buy oat milk")
        assertEquals("phone", saved.watchId)
        assertFalse(saved.truncated)
        assertFalse(saved.edited)
    }

    @Test
    fun `blank shared text is not saved`() {
        viewModel.saveSharedText("   ")

        // A short, generous wait for a write that should never happen, rather
        // than a race against an async path that only fails by doing nothing.
        Thread.sleep(200)
        assertTrue(app.noteStore.notes.value.isEmpty())
    }
}
