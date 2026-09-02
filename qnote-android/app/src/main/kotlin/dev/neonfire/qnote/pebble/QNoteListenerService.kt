// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.pebble

import android.util.Log
import dev.neonfire.qnote.QNoteApplication
import dev.neonfire.qnote.data.Note
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.DataLogSession
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Receives notes from the watch over both transports.
 *
 * The Pebble app binds this service while the watchapp is open (live
 * AppMessages) and whenever it has spooled datalogging records to hand over,
 * which can be long after the watchapp closed.
 */
class QNoteListenerService : BasePebbleListenerService() {

    private val store get() = QNoteApplication.from(this).noteStore
    private val pebble get() = QNoteApplication.from(this).pebbleRepository
    private val slots get() = QNoteApplication.from(this).categorySlots

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: WatchIdentifier,
    ): ReceiveResult {
        if (watchappUUID != QNotePebble.APP_UUID) return ReceiveResult.Nack

        // Numbers always arrive widened to Int32/UInt32 regardless of the width
        // the watch wrote, so read through a tolerant helper.
        val recordId = data.longAt(QNotePebble.KEY_NOTE_ID) ?: return ReceiveResult.Nack
        val capturedAt = data.longAt(QNotePebble.KEY_NOTE_TS) ?: return ReceiveResult.Nack
        val text = (data[QNotePebble.KEY_NOTE_TEXT] as? PebbleDictionaryItem.Text)?.value
            ?: return ReceiveResult.Nack
        // Absent from a watch older than 1.1.0, which is exactly the same thing
        // as uncategorised.
        val categorySlot = data.longAt(QNotePebble.KEY_NOTE_CAT)?.toInt() ?: 0

        val stored = withContext(Dispatchers.IO) {
            store.upsert(
                Note(
                    id = Note.idFor(watch.value, recordId),
                    watchId = watch.value,
                    recordId = recordId,
                    text = text,
                    capturedAt = capturedAt,
                    receivedAt = System.currentTimeMillis(),
                    truncated = false,
                    edited = false,
                    category = slots.nameFor(categorySlot),
                )
            )
        }
        Log.i(TAG, "note $recordId received live (new=$stored)")

        // Acknowledge only now that the row is committed. The watch marks the
        // note synced on this, which is what lets it be evicted from the watch's
        // 12-slot cache, so a premature ack would lose it.
        pebble.acknowledge(recordId, watch)

        return ReceiveResult.Ack
    }

    override suspend fun onDataLogReceived(
        watchappUUID: UUID,
        session: DataLogSession,
        data: ByteArray,
        itemsLeft: Long,
        watch: WatchIdentifier,
    ): ReceiveResult {
        if (watchappUUID != QNotePebble.APP_UUID) return ReceiveResult.Nack
        if (session.tag != QNotePebble.DATALOG_TAG) {
            Log.w(TAG, "ignoring datalog session with tag ${session.tag}")
            return ReceiveResult.Nack
        }

        val records = try {
            NoteRecordCodec.decodeBatch(data, session.itemSize)
        } catch (e: IllegalArgumentException) {
            // Nacking would make the Pebble app redeliver a batch we can never
            // decode. Drop it and say so.
            Log.e(TAG, "undecodable datalog batch, discarding", e)
            return ReceiveResult.Ack
        }

        val now = System.currentTimeMillis()
        val newCount = withContext(Dispatchers.IO) {
            records.count { record ->
                store.upsert(
                    Note(
                        id = Note.idFor(watch.value, record.id),
                        watchId = watch.value,
                        recordId = record.id,
                        text = record.text,
                        capturedAt = record.timestampUtc,
                        receivedAt = now,
                        truncated = record.truncated,
                        edited = false,
                        // The slot table is append-only, so this resolves to
                        // the name the note was tagged with however long the
                        // record sat in the spool.
                        category = slots.nameFor(record.categorySlot),
                    )
                )
            }
        }
        Log.i(TAG, "datalog batch: ${records.size} records, $newCount new, $itemsLeft left")

        records.forEach { pebble.acknowledge(it.id, watch) }

        return ReceiveResult.Ack
    }

    override suspend fun onDataLogSessionFinished(
        watchappUUID: UUID,
        session: DataLogSession,
        watch: WatchIdentifier,
    ): ReceiveResult = ReceiveResult.Ack

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        // The watch is awake and listening: a good moment to pull anything its
        // AppMessage path could not deliver earlier.
        if (watchappUUID != QNotePebble.APP_UUID) return

        pebble.requestSync(watch)

        // And to hand it the categories. Opening the watchapp starts dictation,
        // so this is racing a person speaking a sentence — a race it wins
        // comfortably, and losing it only costs one note its picker.
        val settings = QNoteApplication.from(this).settings
        pebble.pushCategories(
            blob = slots.blobFor(categoriesByRecentUse()),
            askCategory = settings.askCategoryOnWatch,
            watch = watch,
        )
    }

    /**
     * Category names, most recently used first.
     *
     * The watch only has room for a handful, and the one you reached for last
     * is the one you are most likely to reach for now.
     */
    private fun categoriesByRecentUse(): List<String> =
        store.notes.value
            .mapNotNull { note -> note.category?.let { it to note.capturedAt } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.max() }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

    private fun PebbleDictionary.longAt(key: UInt): Long? = when (val item = this[key]) {
        is PebbleDictionaryItem.UInt32 -> item.value.toLong()
        is PebbleDictionaryItem.Int32 -> item.value.toLong()
        is PebbleDictionaryItem.UInt16 -> item.value.toLong()
        is PebbleDictionaryItem.Int16 -> item.value.toLong()
        is PebbleDictionaryItem.UInt8 -> item.value.toLong()
        is PebbleDictionaryItem.Int8 -> item.value.toLong()
        else -> null
    }

    private companion object {
        const val TAG = "QNoteListener"
    }
}
