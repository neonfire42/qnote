// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers [NoteStore.upsertAll], added so a datalogging batch does one merge
 * and one [NoteStore.notes] update instead of one per record. The property
 * that matters is that it behaves exactly like calling [NoteStore.upsert] in a
 * loop -- new rows land, existing ones are never overwritten -- just faster.
 */
@RunWith(RobolectricTestRunner::class)
class NoteStoreTest {

    private lateinit var store: NoteStore

    private fun note(recordId: Long, text: String, capturedAt: Long = recordId) = Note(
        id = Note.idFor("watch-1", recordId),
        watchId = "watch-1",
        recordId = recordId,
        text = text,
        capturedAt = capturedAt,
        receivedAt = 0L,
        truncated = false,
        edited = false,
    )

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("qnote.db")
        store = NoteStore(context)
    }

    @Test
    fun `every note in the batch lands`() {
        val inserted = store.upsertAll(
            listOf(note(1, "buy oat milk"), note(2, "call the dentist"), note(3, "water the plants")),
        )

        assertEquals(3, inserted)
        assertEquals(3, store.notes.value.size)
        assertEquals(setOf("buy oat milk", "call the dentist", "water the plants"),
            store.notes.value.map { it.text }.toSet())
    }

    @Test
    fun `a note already on the phone is not overwritten by the batch`() {
        store.upsert(note(1, "original text"))
        store.updateText(Note.idFor("watch-1", 1), "edited on the phone")

        // Same id and record id as the one already stored, as a datalog replay
        // of an old note would arrive.
        val inserted = store.upsertAll(listOf(note(1, "original text"), note(2, "a new note")))

        assertEquals(1, inserted)
        assertEquals("edited on the phone", store.find(Note.idFor("watch-1", 1))?.text)
    }

    @Test
    fun `an empty batch is a no-op`() {
        store.upsert(note(1, "already here"))

        val inserted = store.upsertAll(emptyList())

        assertEquals(0, inserted)
        assertEquals(1, store.notes.value.size)
    }

    @Test
    fun `duplicate ids within the same batch only count once`() {
        // A malformed or replayed batch should not be able to inflate the
        // "new" count past what was actually inserted.
        val inserted = store.upsertAll(listOf(note(1, "first"), note(1, "first again")))

        assertEquals(1, inserted)
        assertFalse(store.notes.value.isEmpty())
    }
}
