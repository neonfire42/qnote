// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Watch timestamps are seconds since epoch; Java wants milliseconds. */
private fun Long.secondsToMillis() = TimeUnit.SECONDS.toMillis(this)

fun formatAbsolute(capturedAtSeconds: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
        .format(Date(capturedAtSeconds.secondsToMillis()))

fun formatRelative(capturedAtSeconds: Long, now: Long = System.currentTimeMillis()): String {
    val deltaMinutes = TimeUnit.MILLISECONDS.toMinutes(now - capturedAtSeconds.secondsToMillis())
    return when {
        deltaMinutes < 1 -> "just now"
        deltaMinutes < 60 -> "${deltaMinutes}m ago"
        deltaMinutes < 60 * 24 -> "${deltaMinutes / 60}h ago"
        deltaMinutes < 60 * 24 * 7 -> "${deltaMinutes / (60 * 24)}d ago"
        else -> formatAbsolute(capturedAtSeconds)
    }
}
