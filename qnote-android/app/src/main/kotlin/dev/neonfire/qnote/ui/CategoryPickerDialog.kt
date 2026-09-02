package dev.neonfire.qnote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Picks a category for one note or for a selection.
 *
 * There is no separate "manage categories" screen: a category is just a string
 * on a note, so typing a new name here creates it and clearing the last note
 * that uses one retires it.
 *
 * @param current the note's category when editing a single note, null otherwise.
 * @param onPick receives the chosen name, or null to clear the category.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerDialog(
    existing: List<String>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val newName = typed.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category") },
        text = {
            Column {
                if (existing.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        existing.forEach { category ->
                            FilterChip(
                                selected = category == current,
                                onClick = { onPick(category) },
                                label = { Text(category) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("New category") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (existing.isEmpty()) 0.dp else 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(newName) },
                enabled = newName.isNotEmpty(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            Row {
                // Only worth offering when there is something to clear.
                if (current != null) {
                    TextButton(onClick = { onPick(null) }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
