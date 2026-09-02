// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.neonfire.qnote.QNoteApplication
import dev.neonfire.qnote.data.Note
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens to PNGs for the Play listing.
 *
 * These are genuine renders of the shipping composables, driven through the
 * real [NotesViewModel] and a real SQLite store — not mockups. This machine has
 * no `/dev/kvm`, so an Android emulator is not an option, and a hand-drawn
 * approximation would misrepresent the app in a store listing.
 *
 * The qualifiers pin 360x640dp at xxhdpi, which is exactly 1080x1920 px: the
 * standard Play phone screenshot size.
 *
 * Output lands in `app/build/outputs/roborazzi/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class StoreScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: QNoteApplication
    private lateinit var viewModel: NotesViewModel

    @Before
    fun seedNotes() {
        app = RuntimeEnvironment.getApplication() as QNoteApplication
        val store = app.noteStore

        // Ages are relative to now so the rendered "3m ago" labels stay
        // sensible whenever the assets are regenerated.
        val now = System.currentTimeMillis() / 1000
        listOf(
            note(4, "ring the plumber back about the boiler service", "Errands", now - 3 * 60),
            note(
                3,
                "idea: open the talk with the emulator demo, then show the watch",
                "Ideas",
                now - 52 * 60,
            ),
            note(2, "buy oat milk on the way home", "Errands", now - 140 * 60),
            note(1, "call the dentist about moving tuesday's appointment", null, now - 25 * 3600),
        ).forEach(store::upsert)

        viewModel = NotesViewModel(app)
    }

    @Test
    fun `01 note list`() {
        capture("01-note-list") {
            NoteListScreen(viewModel, onOpenNote = {}, onShareText = {})
        }
    }

    @Test
    fun `02 filtered by category`() {
        viewModel.setCategoryFilter(CategoryFilter.Named("Errands"))
        capture("02-category-filter") {
            NoteListScreen(viewModel, onOpenNote = {}, onShareText = {})
        }
    }

    @Test
    fun `03 note detail`() {
        val note = requireNotNull(viewModel.noteById(Note.idFor(WATCH, 3)))
        capture("03-note-detail") {
            NoteDetailScreen(
                note = note,
                categories = listOf("Errands", "Ideas"),
                onBack = {},
                onSave = {},
                onSetCategory = {},
                onDelete = {},
                onShareText = {},
                onCopyText = {},
            )
        }
    }

    /**
     * The multi-select state, showing the categorise and delete actions.
     *
     * This replaces what would have been a shot of the category picker: an
     * AlertDialog opens a second window that never reports idle under
     * Robolectric, so Espresso times out waiting for it. The selection toolbar
     * lives in the main window and shows the same feature.
     */
    @Test
    fun `04 select and categorise`() {
        viewModel.toggleSelected(Note.idFor(WATCH, 2))
        viewModel.toggleSelected(Note.idFor(WATCH, 1))
        capture("04-multi-select") {
            NoteListScreen(viewModel, onOpenNote = {}, onShareText = {})
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            QNoteTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun note(id: Long, text: String, category: String?, capturedAt: Long) = Note(
        id = Note.idFor(WATCH, id),
        watchId = WATCH,
        recordId = id,
        text = text,
        capturedAt = capturedAt,
        receivedAt = System.currentTimeMillis(),
        truncated = false,
        edited = false,
        category = category,
    )

    private companion object {
        const val WATCH = "pebble-time-2"
    }
}
