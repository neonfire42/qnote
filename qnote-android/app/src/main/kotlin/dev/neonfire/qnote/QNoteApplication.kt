// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote

import android.app.Application
import android.content.Context
import dev.neonfire.qnote.data.CategorySlots
import dev.neonfire.qnote.data.NoteStore
import dev.neonfire.qnote.data.Settings
import dev.neonfire.qnote.pebble.PebbleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Process-wide singletons. The listener service and the UI are separate entry
 * points into the same process and both need the store, so it hangs off the
 * Application rather than a DI framework this app is too small to justify.
 */
class QNoteApplication : Application() {

    val noteStore: NoteStore by lazy { NoteStore(this) }
    val pebbleRepository: PebbleRepository by lazy { PebbleRepository(this) }
    val settings: Settings by lazy { Settings(this) }
    val categorySlots: CategorySlots by lazy { CategorySlots(this) }

    override fun onCreate() {
        super.onCreate()
        // NoteStore's constructor opens the database file and loads every note
        // synchronously (see its class doc). Left alone, the first thing to
        // touch this property is NotesViewModel's constructor, called from
        // Compose while building the first frame -- so that disk read landed
        // on the main thread on every cold start.
        //
        // Touching the lazy here instead races that, on a background thread,
        // from the earliest point the process offers: Application.onCreate()
        // runs before any Activity does. `noteStore`'s default lazy is
        // thread-safe by locking, so whichever thread gets there first pays
        // the cost and the other one simply finds the result waiting. On a
        // phone that has opened qnote before -- which is every launch except
        // the one right after installing it -- this finishes well ahead of
        // the first frame. It is a head start, not a guarantee: an
        // adversarial scheduler could still have the main thread win the race
        // and block on the lock, exactly as it always has.
        CoroutineScope(Dispatchers.IO).launch { noteStore }
    }

    companion object {
        fun from(context: Context): QNoteApplication =
            context.applicationContext as QNoteApplication
    }
}
