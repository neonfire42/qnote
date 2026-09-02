// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.pebble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for the fixed 256-byte record the watch writes.
 *
 * The layout must stay byte-for-byte identical to `QNoteRecord` in
 * `qnote-watch/src/c/notes.h`:
 *
 * ```
 * offset  size  field
 *      0     4  id             uint32, little-endian
 *      4     4  timestamp_utc  uint32, seconds since epoch
 *      8     2  text_len       uint16, bytes used in text
 *     10     1  flags          uint8
 *     11     1  reserved       uint8, always 0
 *     12   244  text           UTF-8, zero-padded
 * ```
 *
 * Datalogging hands us whole batches of these, so the size is a contract rather
 * than a convenience: `DataLogSession.itemSize` must equal [RECORD_SIZE].
 */
object NoteRecordCodec {

    const val RECORD_SIZE = 256
    const val TEXT_MAX = 244

    private const val OFFSET_TEXT = 12

    /** The transcription filled the watch's buffer and was cut short. */
    const val FLAG_TRUNCATED = 0x01

    /** Watch-local bookkeeping; meaningless on this side, kept for fidelity. */
    const val FLAG_SYNCED = 0x02

    data class Record(
        val id: Long,
        val timestampUtc: Long,
        val text: String,
        val truncated: Boolean,
    )

    /**
     * Decodes every whole record in a datalogging batch. A trailing partial
     * record is ignored rather than throwing: a malformed tail should not cost
     * us the notes that decoded cleanly.
     */
    fun decodeBatch(data: ByteArray, itemSize: Int = RECORD_SIZE): List<Record> {
        require(itemSize == RECORD_SIZE) {
            "unexpected datalog item size $itemSize, expected $RECORD_SIZE"
        }
        val count = data.size / itemSize
        return (0 until count).map { decode(data, it * itemSize) }
    }

    /** Decodes the single record starting at [offset]. */
    fun decode(data: ByteArray, offset: Int = 0): Record {
        require(offset + RECORD_SIZE <= data.size) {
            "record at $offset runs past the end of a ${data.size}-byte buffer"
        }

        val buffer = ByteBuffer.wrap(data, offset, RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val id = buffer.int.toUInt().toLong()
        val timestamp = buffer.int.toUInt().toLong()
        // Clamp rather than trust: a corrupted length must not read past the
        // record into the next one.
        val textLen = buffer.short.toUShort().toInt().coerceIn(0, TEXT_MAX)
        val flags = buffer.get().toInt() and 0xFF

        val text = String(data, offset + OFFSET_TEXT, textLen, Charsets.UTF_8)

        return Record(
            id = id,
            timestampUtc = timestamp,
            text = text,
            truncated = flags and FLAG_TRUNCATED != 0,
        )
    }
}
