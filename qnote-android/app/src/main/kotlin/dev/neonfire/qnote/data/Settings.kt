// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
package dev.neonfire.qnote.data

import android.content.Context

/**
 * The handful of user preferences qnote has. SharedPreferences rather than
 * DataStore: these are read synchronously during a cold start, before the first
 * frame, and there is no benefit to making that asynchronous for two booleans.
 */
class Settings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("qnote", Context.MODE_PRIVATE)

    /**
     * Start dictation on the watch as soon as this app opens.
     *
     * On by default: qnote exists to capture a thought quickly, and opening the
     * phone app is one of the two fast paths to that (Quick Launch on the watch
     * is the other). Turn it off from the overflow menu if you mostly open the
     * app to read.
     */
    var autoCapture: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPTURE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAPTURE, value).apply()

    /**
     * Offer a category picker on the watch after a note is confirmed.
     *
     * On by default: the categories are already there, and the moment you know
     * which one a note belongs in is the moment you speak it. Back on the watch
     * skips the picker in one press, and this switch removes it entirely for
     * anyone who would rather keep the capture path at zero menus.
     */
    var askCategoryOnWatch: Boolean
        get() = prefs.getBoolean(KEY_ASK_CATEGORY, true)
        set(value) = prefs.edit().putBoolean(KEY_ASK_CATEGORY, value).apply()

    private companion object {
        const val KEY_AUTO_CAPTURE = "auto_capture"
        const val KEY_ASK_CATEGORY = "ask_category_on_watch"
    }
}
