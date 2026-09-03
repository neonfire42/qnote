// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Guards the property the watch's one category byte rests on.
 *
 * A note captured out of range can reach the phone days later, after the
 * category list has changed. Its slot number has to still mean what it meant
 * when it was spoken, which is only true if slots are never reassigned.
 */
@RunWith(RobolectricTestRunner::class)
class CategorySlotsTest {

    private lateinit var slots: CategorySlots

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("qnote", 0).edit().clear().commit()
        slots = CategorySlots(context)
    }

    @Test
    fun `slots are assigned in the order names are first seen`() {
        assertEquals(1, slots.slotFor("Errands"))
        assertEquals(2, slots.slotFor("Ideas"))
        assertEquals(3, slots.slotFor("Work"))
    }

    @Test
    fun `asking twice for the same name gives the same slot`() {
        val first = slots.slotFor("Errands")

        assertEquals(first, slots.slotFor("Errands"))
        assertEquals("Errands", slots.nameFor(first))
    }

    @Test
    fun `a retired category keeps its slot and never hands it on`() {
        val errands = slots.slotFor("Errands")
        slots.slotFor("Ideas")

        // "Errands" is retired: the last note carrying it was deleted, so it no
        // longer appears in the live list the app derives from the notes.
        slots.sync(listOf("Ideas", "Reading"))

        // The new name gets a fresh slot rather than the retired one...
        assertEquals(3, slots.slotFor("Reading"))
        // ...and a note tagged "Errands" on the watch weeks ago still resolves.
        assertEquals("Errands", slots.nameFor(errands))
    }

    @Test
    fun `slot zero and unassigned slots have no name`() {
        slots.slotFor("Errands")

        assertNull(slots.nameFor(0))
        assertNull(slots.nameFor(99))
    }

    @Test
    fun `blank names are never given a slot`() {
        assertEquals(CategorySlots.NONE, slots.slotFor("   "))
        assertEquals(CategorySlots.NONE, slots.slotFor(""))
    }

    @Test
    fun `the pushed blob is the wire form the watchapp parses`() {
        slots.slotFor("Errands")
        slots.slotFor("Ideas")

        assertEquals("2\tIdeas\n1\tErrands\n", slots.blobFor(listOf("Ideas", "Errands")))
    }

    @Test
    fun `restoring onto an empty table adopts the backup's exact slot numbers`() {
        // Simulates a fresh install: nothing has been assigned locally yet, so
        // a note still unsynced on the watch under these numbers must resolve
        // the same way it did on the old phone.
        slots.restoreTable(listOf("Errands", "Ideas", "Work"))

        assertEquals("Errands", slots.nameFor(1))
        assertEquals("Ideas", slots.nameFor(2))
        assertEquals("Work", slots.nameFor(3))
        assertEquals(listOf("Errands", "Ideas", "Work"), slots.allNames())
    }

    @Test
    fun `restoring onto a table that already has entries merges by name instead`() {
        // This phone already has its own history -- Errands is slot 1 here --
        // so the backup's numbering (where Ideas was slot 1) cannot be adopted
        // wholesale without colliding with it.
        slots.slotFor("Errands")

        slots.restoreTable(listOf("Ideas", "Errands"))

        assertEquals("Errands", slots.nameFor(1))
        assertEquals("Ideas", slots.nameFor(2))
    }

    @Test
    fun `the blob is capped in bytes and drops from the end`() {
        // Two-byte characters, so a character count would overshoot the cap
        // that the watch's buffer is actually measured in.
        val names = (1..40).map { "café-$it" }

        val blob = slots.blobFor(names)
        val bytes = blob.toByteArray(Charsets.UTF_8).size

        assertTrue("$bytes bytes", bytes <= CategorySlots.QNOTE_CAT_BLOB_MAX)
        // Kept from the front of the priority order, so the caller controls
        // what survives.
        assertTrue(blob.startsWith("1\tcafé-1\n"))
        assertTrue(blob.lines().size < names.size)
    }
}
