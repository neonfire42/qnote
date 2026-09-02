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
        if (watchappUUID == QNotePebble.APP_UUID) {
            pebble.requestSync(watch)
        }
    }

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
