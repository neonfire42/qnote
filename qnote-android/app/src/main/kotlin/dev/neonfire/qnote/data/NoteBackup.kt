// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads and writes the JSON file behind "Back up notes" / "Restore notes".
 *
 * Separate from the Markdown export, which is for reading and sharing and is
 * deliberately lossy — it drops ids, sync flags, and turns timestamps into
 * display text. This format exists to round-trip losslessly, which is the
 * only thing that makes a restore onto a new phone actually equivalent to the
 * device it replaces.
 *
 * [category_slots] rides along for a reason that is easy to miss: the slot
 * table in [CategorySlots] is append-only precisely so a note still sitting
 * unsynced in the watch's cache resolves to the right name whenever it finally
 * arrives. Restore that note's phone onto a fresh install without also
 * restoring the table, and the numbering starts over from slot 1 — the note
 * would arrive tagged with a number that used to mean something else.
 *
 * Deliberately free of any Android dependency (no Context, no Uri) so it can
 * be unit-tested as plain strings in and out; the file IO lives in
 * [dev.neonfire.qnote.ui.NotesViewModel].
 */
object NoteBackup {

    const val FORMAT_VERSION = 1

    /** What a decoded backup file contains. */
    data class Contents(
        val notes: List<Note>,
        val categorySlots: List<String>,
        /** Null when the file predates this field, or omits it. */
        val autoCapture: Boolean?,
        val askCategoryOnWatch: Boolean?,
    )

    /** The file is not a qnote backup, or is too damaged to trust. */
    class FormatException(message: String) : Exception(message)

    fun encode(
        notes: List<Note>,
        categorySlots: List<String>,
        autoCapture: Boolean,
        askCategoryOnWatch: Boolean,
    ): String {
        val root = JSONObject()
        root.put("format", FORMAT_VERSION)
        root.put("exported_at", System.currentTimeMillis() / 1000)
        root.put("category_slots", JSONArray(categorySlots))
        root.put(
            "settings",
            JSONObject().apply {
                put("auto_capture", autoCapture)
                put("ask_category_on_watch", askCategoryOnWatch)
            },
        )
        root.put("notes", JSONArray(notes.map { it.toJson() }))
        return root.toString(2)
    }

    /** @throws FormatException if [json] is not a backup this version can read. */
    fun decode(json: String): Contents {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw FormatException("That file is not a qnote backup.")
        }

        val format = root.optInt("format", -1)
        if (format != FORMAT_VERSION) {
            throw FormatException(
                if (format < 0) "That file is not a qnote backup."
                else "This backup is from a newer version of qnote that this one cannot read.",
            )
        }

        val slots = root.optJSONArray("category_slots").toStringList()

        val notesArray = root.optJSONArray("notes") ?: JSONArray()
        val notes = try {
            (0 until notesArray.length()).map { notesArray.getJSONObject(it).toNote() }
        } catch (e: JSONException) {
            throw FormatException("A note in that backup is missing something it needs.")
        }

        val settings = root.optJSONObject("settings")
        return Contents(
            notes = notes,
            categorySlots = slots,
            autoCapture = settings?.optBooleanOrNull("auto_capture"),
            askCategoryOnWatch = settings?.optBooleanOrNull("ask_category_on_watch"),
        )
    }

    private fun Note.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("watch_id", watchId)
        put("record_id", recordId)
        put("text", text)
        put("captured_at", capturedAt)
        put("received_at", receivedAt)
        put("truncated", truncated)
        put("edited", edited)
        put("category", category ?: JSONObject.NULL)
    }

    private fun JSONObject.toNote(): Note = Note(
        id = getString("id"),
        watchId = getString("watch_id"),
        recordId = getLong("record_id"),
        text = getString("text"),
        capturedAt = getLong("captured_at"),
        // Absent in a hand-edited or very old file: not load-bearing for
        // anything but the "received" display, so default rather than reject.
        receivedAt = optLong("received_at", 0L),
        truncated = optBoolean("truncated", false),
        edited = optBoolean("edited", false),
        category = if (isNull("category")) null else getString("category"),
    )

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }
    }

    // Distinguishes "absent" (null, keep this phone's current setting) from a
    // present false, which JSONObject.optBoolean cannot: it defaults to false
    // for both.
    private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
        if (has(name)) getBoolean(name) else null
}
