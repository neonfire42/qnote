// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import dev.neonfire.qnote.QNoteApplication
import dev.neonfire.qnote.data.Note
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers [NotesViewModel.matchesQuery] directly, against the function rather
 * than the reactive [NotesViewModel.notes] flow it feeds: that flow is a
 * `stateIn(..., WhileSubscribed(5_000), ...)`, and driving its collection
 * lifecycle just to check a string match would test Compose/coroutine
 * plumbing that [ui.NoteListScreen] already exercises, not the predicate
 * itself.
 */
@RunWith(RobolectricTestRunner::class)
class NotesViewModelTest {

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
        val app = RuntimeEnvironment.getApplication() as QNoteApplication
        viewModel = NotesViewModel(app)
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
}
