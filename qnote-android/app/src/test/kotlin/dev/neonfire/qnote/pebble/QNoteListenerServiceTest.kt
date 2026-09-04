// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.pebble

import dev.neonfire.qnote.data.CategorySlots
import dev.neonfire.qnote.data.Note
import dev.neonfire.qnote.data.NoteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers [resolveIncomingCategory] -- which name a live note should be stored
 * under, given the numeric slot every watch sends and the new-category text a
 * 1.2.0+ watch sends only when it just minted a category on the spot -- and
 * [storeLiveNote], which corrects for the datalog spool racing that live
 * message and sometimes winning.
 */
@RunWith(RobolectricTestRunner::class)
class QNoteListenerServiceTest {

    private lateinit var slots: CategorySlots
    private lateinit var store: NoteStore

    private fun note(recordId: Long, category: String? = null) = Note(
        id = Note.idFor("watch-1", recordId),
        watchId = "watch-1",
        recordId = recordId,
        text = "buy oat milk",
        capturedAt = recordId,
        receivedAt = 0L,
        truncated = false,
        edited = false,
        category = category,
    )

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("qnote", 0).edit().clear().commit()
        context.deleteDatabase("qnote.db")
        slots = CategorySlots(context)
        store = NoteStore(context)
    }

    @Test
    fun `an existing slot resolves through the slot table, as before`() {
        val slot = slots.slotFor("Errands")

        assertEquals("Errands", resolveIncomingCategory(slots, slot, newCategoryName = null))
    }

    @Test
    fun `slot 0 with no new name is uncategorised`() {
        assertNull(resolveIncomingCategory(slots, categorySlot = 0, newCategoryName = null))
    }

    @Test
    fun `a freshly spoken category is minted a slot and used directly`() {
        val category = resolveIncomingCategory(slots, categorySlot = 0, newCategoryName = "Recipes")

        assertEquals("Recipes", category)
        // The mint has to actually happen here -- it is what lets the watch
        // learn the official slot on the next category push.
        assertEquals("Recipes", slots.nameFor(slots.slotFor("Recipes")))
    }

    @Test
    fun `a new category name always wins over the numeric slot`() {
        // A watch that predates this feature never sends both, but a stale or
        // buggy message pairing both should still resolve unambiguously: the
        // watch only ever sends a name when it has no slot for it, so the
        // name is the more specific answer.
        val existingSlot = slots.slotFor("Errands")

        val category = resolveIncomingCategory(slots, existingSlot, newCategoryName = "Recipes")

        assertEquals("Recipes", category)
    }

    @Test
    fun `blank or whitespace-only new category names fall back to the slot`() {
        val slot = slots.slotFor("Errands")

        assertEquals("Errands", resolveIncomingCategory(slots, slot, newCategoryName = "   "))
        assertEquals("Errands", resolveIncomingCategory(slots, slot, newCategoryName = ""))
    }

    @Test
    fun `a new category name is trimmed before it is minted or stored`() {
        val category = resolveIncomingCategory(slots, 0, newCategoryName = "  Recipes  ")

        assertEquals("Recipes", category)
    }

    @Test
    fun `a new row is simply inserted with its category, no extra write needed`() {
        val inserted = storeLiveNote(store, note(1, category = "Recipes"), hasNewCategoryName = true)

        assertTrue(inserted)
        assertEquals("Recipes", store.find(Note.idFor("watch-1", 1))?.category)
    }

    @Test
    fun `an uncategorised row from a datalog replay is corrected once the live category arrives`() {
        // The bug this guards against: the datalog spool reaches the phone
        // first with this same note, uncategorised (it has no field for a
        // name the watch had no slot for). Without the fix, upsert()'s
        // CONFLICT_IGNORE would leave that row exactly as it arrived.
        store.upsert(note(1, category = null))

        val inserted = storeLiveNote(store, note(1, category = "Recipes"), hasNewCategoryName = true)

        assertFalse(inserted)
        assertEquals("Recipes", store.find(Note.idFor("watch-1", 1))?.category)
    }

    @Test
    fun `an existing row is left alone when this message carries no new category`() {
        // The ordinary case a plain retry or a datalog replay of an
        // already-synced note goes through: nothing here should ever
        // overwrite a category the user set by hand on the phone.
        store.upsert(note(1, category = null))
        store.updateCategory(Note.idFor("watch-1", 1), "Ideas")

        val inserted = storeLiveNote(store, note(1, category = "Errands"), hasNewCategoryName = false)

        assertFalse(inserted)
        assertEquals("Ideas", store.find(Note.idFor("watch-1", 1))?.category)
    }
}
