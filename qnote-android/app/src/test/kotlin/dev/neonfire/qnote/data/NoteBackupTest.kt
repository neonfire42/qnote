// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the one property a backup file exists for: restoring it should put
 * back exactly what was exported, and a file this version cannot make sense of
 * should say so rather than crash.
 *
 * Run under Robolectric rather than plain JUnit: `org.json` is stubbed to throw
 * under the Android SDK jar unit tests otherwise link against, and this file
 * only needs real JSON, not an Android Context.
 */
@RunWith(RobolectricTestRunner::class)
class NoteBackupTest {

    private fun note(id: String, category: String? = null) = Note(
        id = id,
        watchId = "watch-1",
        recordId = id.substringAfterLast(':').toLong(),
        text = "buy oat milk",
        capturedAt = 1_788_360_000L,
        receivedAt = 1_788_360_005_000L,
        truncated = false,
        edited = true,
        category = category,
    )

    @Test
    fun `a note round-trips through encode and decode unchanged`() {
        val original = note("watch-1:4", category = "Errands")

        val json = NoteBackup.encode(
            notes = listOf(original),
            categorySlots = listOf("Errands", "Ideas"),
            autoCapture = true,
            askCategoryOnWatch = false,
        )
        val restored = NoteBackup.decode(json)

        assertEquals(listOf(original), restored.notes)
        assertEquals(listOf("Errands", "Ideas"), restored.categorySlots)
        assertEquals(true, restored.autoCapture)
        assertEquals(false, restored.askCategoryOnWatch)
    }

    @Test
    fun `an uncategorised note keeps a null category through the round trip`() {
        val json = NoteBackup.encode(
            notes = listOf(note("watch-1:1")),
            categorySlots = emptyList(),
            autoCapture = false,
            askCategoryOnWatch = true,
        )

        assertNull(NoteBackup.decode(json).notes.single().category)
    }

    @Test
    fun `settings absent from an old file decode as null, not false`() {
        // A hand-trimmed file with no settings object at all -- simulating a
        // future or partial export -- must not read back as "both off".
        val json = """{"format":1,"category_slots":[],"notes":[]}"""

        val decoded = NoteBackup.decode(json)

        assertNull(decoded.autoCapture)
        assertNull(decoded.askCategoryOnWatch)
    }

    @Test
    fun `plain text is not a qnote backup`() {
        val error = runCatching { NoteBackup.decode("not json at all") }.exceptionOrNull()

        assertTrue(error is NoteBackup.FormatException)
    }

    @Test
    fun `valid JSON with no format field is not a qnote backup`() {
        val error = runCatching { NoteBackup.decode("""{"notes":[]}""") }.exceptionOrNull()

        assertTrue(error is NoteBackup.FormatException)
    }

    @Test
    fun `a format from the future is refused rather than misread`() {
        val json = """{"format":99,"category_slots":[],"notes":[]}"""

        val error = runCatching { NoteBackup.decode(json) }.exceptionOrNull()

        assertTrue(error is NoteBackup.FormatException)
    }

    @Test
    fun `a note missing a required field is reported, not thrown as a raw JSONException`() {
        // "text" is missing entirely.
        val json = """
            {"format":1,"category_slots":[],"notes":[
                {"id":"w:1","watch_id":"w","record_id":1,"captured_at":0}
            ]}
        """.trimIndent()

        val error = runCatching { NoteBackup.decode(json) }.exceptionOrNull()

        assertTrue(error is NoteBackup.FormatException)
    }
}
