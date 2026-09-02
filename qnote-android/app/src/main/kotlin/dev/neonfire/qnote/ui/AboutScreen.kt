// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.neonfire.qnote.BuildConfig
import dev.neonfire.qnote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val QNOTE_SOURCE_URL = "https://github.com/neonfire42/qnote"

/**
 * About and licence.
 *
 * This screen is not decoration: GPLv3 requires the licence text to be conveyed
 * along with a distributed binary, and qnote's APK is handed to people directly
 * rather than through a store that would surface it. So the full licence ships
 * in `res/raw/gpl3.txt` and is readable here, offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val context = LocalContext.current

    // 34 KB off the main thread; the screen renders with the notice while it loads.
    val licence by produceState(initialValue = "", context) {
        value = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.gpl3).bufferedReader().use { it.readText() }
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("About qnote") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("qnote ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Voice notes for Pebble.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Text(
                text = "Copyright © 2026 neonfire42\n\n" +
                    "This program is free software: you can redistribute it and/or modify it " +
                    "under the terms of the GNU General Public License as published by the " +
                    "Free Software Foundation, either version 3 of the License, or (at your " +
                    "option) any later version.\n\n" +
                    "This program is distributed in the hope that it will be useful, but " +
                    "WITHOUT ANY WARRANTY; without even the implied warranty of " +
                    "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU " +
                    "General Public License below for more details.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )

            TextButton(onClick = onOpenSource, modifier = Modifier.padding(top = 8.dp)) {
                Text("Source code: $QNOTE_SOURCE_URL")
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Text(
                text = licence,
                // Monospace and horizontally scrollable: the GPL is formatted
                // with fixed-width columns and reflowing it makes it worse.
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}
