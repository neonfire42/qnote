// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.pebble

import java.util.UUID

/** Shared constants for talking to the qnote watchapp. */
object QNotePebble {

    /** Must match `uuid` in qnote-watch/package.json. */
    val APP_UUID: UUID = UUID.fromString("1ca95d44-145f-40d4-8a80-c34ccf7f0119")

    // AppMessage keys. The SDK assigns these from the `messageKeys` array in
    // package.json, in order, starting at 10000 — verified in the built
    // appinfo.json. They must stay in that order on both sides.
    const val KEY_NOTE_ID = 10000u
    const val KEY_NOTE_TS = 10001u
    const val KEY_NOTE_TEXT = 10002u
    const val KEY_ACK_ID = 10003u
    const val KEY_DELETE_ID = 10004u
    const val KEY_SYNC_REQUEST = 10005u
    const val KEY_START_CAPTURE = 10006u

    /** Tag passed to `data_logging_create()` on the watch: "qnt1". */
    const val DATALOG_TAG = 0x716E7431L
}
