// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.data

import android.content.Context

/**
 * The phone's map from category name to the one-byte slot the watch record can
 * carry.
 *
 * A note tagged on the watch travels back as a number, because `category_slot`
 * is the single spare byte in the 256-byte record. A number is only meaningful
 * against a table both sides agree on, and the watch's copy of that table can
 * be arbitrarily old: a note captured out of range reaches the phone whenever
 * the datalogging spool next drains, which may be days later and several
 * category changes on.
 *
 * So the table is **append-only**. A name gets a slot the first time it is
 * seen and keeps it forever; deleting the last note in a category retires the
 * name from the UI but never frees its slot for someone else. That makes an old
 * slot number resolve correctly no matter what happened in between, which a
 * positional index into the live category list could not do.
 *
 * Slot 0 is reserved for "uncategorised" and is never assigned.
 */
class CategorySlots(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("qnote", Context.MODE_PRIVATE)

    /** Names in slot order: position 0 is slot 1. Retired names stay put. */
    private fun table(): List<String> =
        prefs.getString(KEY_TABLE, "")
            ?.split('\n')
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun save(table: List<String>) {
        prefs.edit().putString(KEY_TABLE, table.joinToString("\n")).apply()
    }

    /**
     * The slot for [name], assigning one if this is the first time it is seen.
     *
     * Returns 0 once all 255 slots are spent. Notes are then uncategorised
     * rather than mis-filed, which is the better of the two failures; 255
     * categories on a watch that shows twelve is not a case worth more
     * machinery than this sentence.
     */
    fun slotFor(name: String): Int {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return NONE

        val current = table()
        val existing = current.indexOf(cleaned)
        if (existing >= 0) return existing + 1
        if (current.size >= MAX_SLOTS) return NONE

        save(current + cleaned)
        return current.size + 1
    }

    /** The name bound to [slot], or null for 0 and for slots never assigned. */
    fun nameFor(slot: Int): String? = table().getOrNull(slot - 1)

    /** Gives a slot to every name that does not have one yet. */
    fun sync(names: List<String>) {
        names.forEach { slotFor(it) }
    }

    /** Every name in slot order, for a backup: position 0 is slot 1. */
    fun allNames(): List<String> = table()

    /**
     * Restores a slot table from a backup.
     *
     * Onto an empty table — the common case, setting up a new phone — the
     * backup's slot numbers are adopted exactly. That is what keeps a note
     * still sitting unsynced in the watch's cache resolving to the right name
     * once it finally arrives: the watch has no idea a restore even happened,
     * so its old slot numbers only mean anything if this table still agrees
     * with them.
     *
     * Onto a table that already has entries of its own, slot numbers from two
     * separate histories cannot be reconciled in general, so this falls back
     * to [sync] and only learns names it does not already know. A note tagged
     * on the watch under an old number before the restore may resolve to the
     * wrong category, or none, until it next hears back from this phone —
     * accepted rather than built around, since restoring onto a phone that
     * already has its own notes is the unusual case.
     */
    fun restoreTable(names: List<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (table().isEmpty()) {
            save(cleaned)
        } else {
            sync(cleaned)
        }
    }

    /**
     * The list to push to the watch, as the `"<slot>\t<name>\n"` lines the
     * watchapp parses, truncated to [QNOTE_CAT_BLOB_MAX] bytes.
     *
     * [names] is taken in priority order and entries are dropped from the end,
     * so the caller decides what survives the cap — the watch shows a short
     * scrolling menu, not the archive.
     */
    fun blobFor(names: List<String>): String = buildString {
        // Counted in bytes, not characters: the watch's buffer is bytes, and a
        // category name may well be an accented word or an emoji.
        var bytes = 0
        for (name in names) {
            val slot = slotFor(name)
            if (slot == NONE) continue
            val line = "$slot\t$name\n"
            val size = line.toByteArray(Charsets.UTF_8).size
            if (bytes + size > QNOTE_CAT_BLOB_MAX) break
            append(line)
            bytes += size
        }
    }

    companion object {
        /** No category. Never assigned to a name. */
        const val NONE = 0

        private const val MAX_SLOTS = 255

        /** Must match `QNOTE_CAT_BLOB_MAX` in `qnote-watch/src/c/categories.h`. */
        const val QNOTE_CAT_BLOB_MAX = 192

        private const val KEY_TABLE = "category_slots"
    }
}
