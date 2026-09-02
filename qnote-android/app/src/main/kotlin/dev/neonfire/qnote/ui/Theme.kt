package dev.neonfire.qnote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * qnote is dark, always.
 *
 * The accent is `#00AAFF` — `GColorVividCerulean`, the exact colour the
 * watchapp uses for its menu highlight and timestamps, so the two halves of
 * the app look like one thing.
 */
private val Cerulean = Color(0xFF00AAFF)

private val QNoteDarkColors = darkColorScheme(
    primary = Cerulean,
    onPrimary = Color(0xFF00243D),
    primaryContainer = Color(0xFF00405F),
    onPrimaryContainer = Color(0xFFCDE9FF),

    secondary = Cerulean,
    onSecondary = Color(0xFF00243D),
    // Used for the selected state of note cards and category chips.
    secondaryContainer = Color(0xFF00394F),
    onSecondaryContainer = Color(0xFFCDE9FF),

    background = Color(0xFF101418),
    onBackground = Color(0xFFE4E9EE),
    surface = Color(0xFF141A1F),
    onSurface = Color(0xFFE4E9EE),
    // Note cards sit on this, one step up from the page.
    surfaceVariant = Color(0xFF1E252C),
    onSurfaceVariant = Color(0xFF9FADBA),

    outline = Color(0xFF3A444E),
    outlineVariant = Color(0xFF2A333B),

    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0906),
)

@Composable
fun QNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = QNoteDarkColors, content = content)
}
