// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote

import android.app.Application
import android.content.Context
import dev.neonfire.qnote.data.NoteStore
import dev.neonfire.qnote.data.Settings
import dev.neonfire.qnote.pebble.PebbleRepository

/**
 * Process-wide singletons. The listener service and the UI are separate entry
 * points into the same process and both need the store, so it hangs off the
 * Application rather than a DI framework this app is too small to justify.
 */
class QNoteApplication : Application() {

    val noteStore: NoteStore by lazy { NoteStore(this) }
    val pebbleRepository: PebbleRepository by lazy { PebbleRepository(this) }
    val settings: Settings by lazy { Settings(this) }

    companion object {
        fun from(context: Context): QNoteApplication =
            context.applicationContext as QNoteApplication
    }
}
