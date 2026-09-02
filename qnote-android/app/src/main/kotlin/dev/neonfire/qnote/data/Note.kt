package dev.neonfire.qnote.data

/**
 * A note as the phone holds it.
 *
 * [id] is `"<watchId>:<recordId>"`. The watch's record id is only unique per
 * watch, and the same note can arrive twice — once live over AppMessage and
 * again from the datalogging spool, which the Pebble app may even replay — so
 * this pair is the deduplication key, not a generated row id.
 */
data class Note(
    val id: String,
    val watchId: String,
    val recordId: Long,
    val text: String,
    /** Seconds since epoch, as recorded on the watch when it was spoken. */
    val capturedAt: Long,
    /** Milliseconds since epoch, when the phone stored it. */
    val receivedAt: Long,
    /** The watch's buffer cut the transcription short. */
    val truncated: Boolean,
    /** True once the text differs from what the watch sent. */
    val edited: Boolean,
) {
    val title: String
        get() = text.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: "(empty note)"

    companion object {
        fun idFor(watchId: String, recordId: Long) = "$watchId:$recordId"
    }
}
