// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import dev.neonfire.qnote.data.Note

/** Which slice of the note list the category chip row is showing. */
sealed interface CategoryFilter {

    fun matches(note: Note): Boolean

    data object All : CategoryFilter {
        override fun matches(note: Note) = true
    }

    data object Uncategorised : CategoryFilter {
        override fun matches(note: Note) = note.category == null
    }

    data class Named(val category: String) : CategoryFilter {
        override fun matches(note: Note) = note.category == category
    }
}
