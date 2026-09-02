package dev.neonfire.qnote.pebble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one seam that spans two languages: the packed C struct the watch
 * writes and the Kotlin that reads it. A silent disagreement here corrupts
 * every note, so the fixtures are not hand-written — they come from
 * `tools/gen_fixture.c`, which compiles the struct straight out of
 * `qnote-watch/src/c/notes.h`. Regenerate them if that header changes.
 */
class NoteRecordCodecTest {

    // id=1, ts=1788363600, "buy oat milk on the way home" (28 bytes), no flags
    private val plainNote =
        "010000005043986a1c000000" +
            "627579206f6174206d696c6b206f6e207468652077617920686f6d65" +
            "00".repeat(216)

    // id=2, ts=1788363777, "call the dentist\nabout tuesday" (30 bytes), truncated
    private val truncatedNote =
        "020000000144986a1e000100" +
            "63616c6c207468652064656e746973740a61626f75742074756573646179" +
            "00".repeat(214)

    // id=3, ts=1788364000, "café — pick up beans" (23 bytes, 20 chars), synced
    private val utf8Note =
        "03000000e044986a17000200" +
            "636166c3a920e28094207069636b207570206265616e73" +
            "00".repeat(221)

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `fixtures are exactly one record long`() {
        listOf(plainNote, truncatedNote, utf8Note).forEach {
            assertEquals(NoteRecordCodec.RECORD_SIZE, it.hexToBytes().size)
        }
    }

    @Test
    fun `decodes a plain note`() {
        val record = NoteRecordCodec.decode(plainNote.hexToBytes())

        assertEquals(1L, record.id)
        assertEquals(1788363600L, record.timestampUtc)
        assertEquals("buy oat milk on the way home", record.text)
        assertFalse(record.truncated)
    }

    @Test
    fun `keeps newlines and reads the truncated flag`() {
        val record = NoteRecordCodec.decode(truncatedNote.hexToBytes())

        assertEquals(2L, record.id)
        assertEquals("call the dentist\nabout tuesday", record.text)
        assertTrue(record.truncated)
    }

    @Test
    fun `decodes multi-byte UTF-8 by byte length not character count`() {
        val record = NoteRecordCodec.decode(utf8Note.hexToBytes())

        assertEquals(3L, record.id)
        assertEquals("café — pick up beans", record.text)
        // 20 characters, but 23 bytes: proof text_len is a byte count.
        assertEquals(20, record.text.length)
        assertEquals(23, record.text.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `decodes a datalog batch of several records`() {
        val batch = (plainNote + truncatedNote + utf8Note).hexToBytes()

        val records = NoteRecordCodec.decodeBatch(batch, NoteRecordCodec.RECORD_SIZE)

        assertEquals(listOf(1L, 2L, 3L), records.map { it.id })
        assertEquals("buy oat milk on the way home", records[0].text)
        assertEquals("café — pick up beans", records[2].text)
    }

    @Test
    fun `ignores a trailing partial record rather than losing the whole batch`() {
        val batch = (plainNote.hexToBytes() + ByteArray(37))

        val records = NoteRecordCodec.decodeBatch(batch, NoteRecordCodec.RECORD_SIZE)

        assertEquals(1, records.size)
        assertEquals(1L, records[0].id)
    }

    @Test
    fun `an unexpected item size is rejected outright`() {
        val error = runCatching {
            NoteRecordCodec.decodeBatch(plainNote.hexToBytes(), itemSize = 128)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `a corrupt text length cannot read past the record`() {
        val bytes = plainNote.hexToBytes()
        bytes[8] = 0xFF.toByte()  // text_len = 65535
        bytes[9] = 0xFF.toByte()

        val record = NoteRecordCodec.decode(bytes)

        assertEquals(NoteRecordCodec.TEXT_MAX, record.text.toByteArray(Charsets.UTF_8).size)
    }
}
