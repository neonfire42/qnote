// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notes on disk.
 *
 * Plain SQLite rather than Room: the schema is one table, and skipping an
 * annotation processor keeps the build free of the Kotlin/KSP version pinning
 * that Room would drag in.
 *
 * Writes are synchronous and the caller decides which thread to be on. That
 * matters for [upsert]: the listener service must not acknowledge a note to the
 * Pebble app until the row is actually committed.
 *
 * Construction itself is synchronous too — it opens the database and loads
 * every row before returning — which is why
 * [dev.neonfire.qnote.QNoteApplication.onCreate] touches this class on a
 * background thread ahead of time rather than leaving the first touch to
 * whatever asks for it first.
 */
class NoteStore(context: Context) {

    private val helper = Helper(context.applicationContext)

    private val _notes = MutableStateFlow<List<Note>>(emptyList())

    /** Every note, newest capture first. Re-read after each mutation. */
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _notes.value = queryAll()
    }

    /**
     * Inserts a note, or fills in a row that already exists.
     *
     * Existing rows are deliberately not overwritten: the same note can arrive
     * again from the datalogging replay long after the user edited it here, and
     * the phone's copy is the one worth keeping.
     *
     * @return true if this call created the row.
     */
    fun upsert(note: Note): Boolean {
        val rowId = helper.writableDatabase.insertWithOnConflict(
            TABLE, null, valuesFor(note), SQLiteDatabase.CONFLICT_IGNORE,
        )
        refresh()
        return rowId != -1L
    }

    /**
     * Inserts many notes in one transaction, refreshing [notes] once at the
     * end rather than once per note.
     *
     * Exists for a datalogging batch: a dozen spooled records used to mean a
     * dozen full-table [queryAll] scans and a dozen [notes] emissions — one per
     * call to [upsert] — for a change that only needs to be visible once the
     * whole batch has landed.
     *
     * @return how many of [notes] were new rows, same meaning as [upsert]'s
     *   return value, summed.
     */
    fun upsertAll(notes: Collection<Note>): Int {
        if (notes.isEmpty()) return 0
        val db = helper.writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            notes.forEach { note ->
                val rowId =
                    db.insertWithOnConflict(TABLE, null, valuesFor(note), SQLiteDatabase.CONFLICT_IGNORE)
                if (rowId != -1L) inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
        return inserted
    }

    private fun valuesFor(note: Note) = ContentValues().apply {
        put(COL_ID, note.id)
        put(COL_WATCH_ID, note.watchId)
        put(COL_RECORD_ID, note.recordId)
        put(COL_TEXT, note.text)
        put(COL_CAPTURED_AT, note.capturedAt)
        put(COL_RECEIVED_AT, note.receivedAt)
        put(COL_TRUNCATED, if (note.truncated) 1 else 0)
        put(COL_EDITED, if (note.edited) 1 else 0)
        put(COL_CATEGORY, note.category)
    }

    fun updateText(id: String, text: String) {
        val values = ContentValues().apply {
            put(COL_TEXT, text)
            put(COL_EDITED, 1)
        }
        helper.writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id))
        refresh()
    }

    fun delete(id: String) {
        helper.writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id))
        refresh()
    }

    fun delete(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.delete(TABLE, "$COL_ID = ?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }

    /** Assigns or clears a note's category. Blank is stored as null. */
    fun updateCategory(id: String, category: String?) {
        val values = ContentValues().apply {
            val cleaned = category?.trim()?.takeIf { it.isNotEmpty() }
            if (cleaned == null) putNull(COL_CATEGORY) else put(COL_CATEGORY, cleaned)
        }
        helper.writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id))
        refresh()
    }

    fun find(id: String): Note? =
        helper.readableDatabase
            .query(TABLE, null, "$COL_ID = ?", arrayOf(id), null, null, null)
            .use { if (it.moveToFirst()) it.toNote() else null }

    private fun queryAll(): List<Note> =
        helper.readableDatabase
            .query(TABLE, null, null, null, null, null, "$COL_CAPTURED_AT DESC, $COL_RECORD_ID DESC")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toNote())
                }
            }

    private fun Cursor.toNote() = Note(
        id = getString(getColumnIndexOrThrow(COL_ID)),
        watchId = getString(getColumnIndexOrThrow(COL_WATCH_ID)),
        recordId = getLong(getColumnIndexOrThrow(COL_RECORD_ID)),
        text = getString(getColumnIndexOrThrow(COL_TEXT)),
        capturedAt = getLong(getColumnIndexOrThrow(COL_CAPTURED_AT)),
        receivedAt = getLong(getColumnIndexOrThrow(COL_RECEIVED_AT)),
        truncated = getInt(getColumnIndexOrThrow(COL_TRUNCATED)) != 0,
        edited = getInt(getColumnIndexOrThrow(COL_EDITED)) != 0,
        category = getString(getColumnIndexOrThrow(COL_CATEGORY)),
    )

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_WATCH_ID TEXT NOT NULL,
                    $COL_RECORD_ID INTEGER NOT NULL,
                    $COL_TEXT TEXT NOT NULL,
                    $COL_CAPTURED_AT INTEGER NOT NULL,
                    $COL_RECEIVED_AT INTEGER NOT NULL,
                    $COL_TRUNCATED INTEGER NOT NULL DEFAULT 0,
                    $COL_EDITED INTEGER NOT NULL DEFAULT 0,
                    $COL_CATEGORY TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_captured ON $TABLE ($COL_CAPTURED_AT DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // v1 -> v2 added categories. Existing notes become uncategorised,
            // which is why the column is nullable rather than NOT NULL.
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_CATEGORY TEXT")
            }
        }
    }

    companion object {
        private const val DB_NAME = "qnote.db"
        private const val DB_VERSION = 2

        private const val TABLE = "notes"
        private const val COL_ID = "id"
        private const val COL_WATCH_ID = "watch_id"
        private const val COL_RECORD_ID = "record_id"
        private const val COL_TEXT = "text"
        private const val COL_CAPTURED_AT = "captured_at"
        private const val COL_RECEIVED_AT = "received_at"
        private const val COL_TRUNCATED = "truncated"
        private const val COL_EDITED = "edited"
        private const val COL_CATEGORY = "category"
    }
}
