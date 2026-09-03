// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers [sharedTextFrom], the pure part of turning Android's share sheet into
 * a note. Robolectric only for a real [Intent] to read extras from -- nothing
 * here needs a running Activity.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private fun sendIntent(text: String? = null, subject: String? = null) =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    @Test
    fun `plain shared text is used as-is`() {
        assertEquals("buy oat milk", sharedTextFrom(sendIntent(text = "buy oat milk")))
    }

    @Test
    fun `a subject and text are folded together`() {
        val intent = sendIntent(text = "https://example.com/recipe", subject = "Oat milk recipe")

        assertEquals(
            "Oat milk recipe\n\nhttps://example.com/recipe",
            sharedTextFrom(intent),
        )
    }

    @Test
    fun `a subject already present in the text is not duplicated`() {
        val intent = sendIntent(text = "Oat milk recipe: see the attached link", subject = "Oat milk recipe")

        assertEquals("Oat milk recipe: see the attached link", sharedTextFrom(intent))
    }

    @Test
    fun `a subject with no text is used on its own`() {
        assertEquals("Oat milk recipe", sharedTextFrom(sendIntent(subject = "Oat milk recipe")))
    }

    @Test
    fun `an intent with neither field is not a note`() {
        assertNull(sharedTextFrom(sendIntent()))
    }

    @Test
    fun `an intent that is not a text share is ignored`() {
        val notShare = Intent(Intent.ACTION_VIEW).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "buy oat milk")
        }
        val wrongType = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, "buy oat milk")
        }

        assertNull(sharedTextFrom(notShare))
        assertNull(sharedTextFrom(wrongType))
    }
}
