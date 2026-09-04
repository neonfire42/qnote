// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.pebble

import dev.neonfire.qnote.data.CategorySlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers [resolveIncomingCategory]: which name a live note should be stored
 * under, given the numeric slot every watch sends and the new-category text a
 * 1.2.0+ watch sends only when it just minted a category on the spot.
 */
@RunWith(RobolectricTestRunner::class)
class QNoteListenerServiceTest {

    private lateinit var slots: CategorySlots

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("qnote", 0).edit().clear().commit()
        slots = CategorySlots(context)
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
}
