// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include <pebble.h>

// The categories the phone has told this watch about.
//
// Categories are created and owned on the phone — that is where the keyboard
// is, and where the archive lives. The watch only needs enough of the list to
// offer it after a dictation, so the companion pushes a compact snapshot over
// AppMessage and this module keeps it.
//
// The wire form is one entry per line, "<slot>\t<name>\n", so the slot travels
// with the name and the order carries no meaning. The phone sends the entries
// it thinks are most useful first and simply stops when the blob is full.

// Bytes of category list we keep. A persist value tops out at 256 bytes, and
// the note ring already spends 3 KB of the app's ~4 KB budget, so this stays
// deliberately small.
#define QNOTE_CAT_BLOB_MAX 192

// Most categories the picker will ever show. The menu is scrolled with two
// buttons, so a longer list would be worse, not better.
#define QNOTE_CAT_MAX 12

// Longest category name the watch will take dictation for. Generous for a
// short label, tiny next to the AppMessage payload it travels in.
#define QNOTE_CATEGORY_NAME_MAX 32

void categories_init(void);

// Replaces the list from a CATEGORIES message. Copies, persists, then parses.
void categories_set_blob(const char *blob);

// Records whether the watch should offer the picker after a dictation.
void categories_set_ask(bool ask);

int categories_count(void);

// index 0 is the first category the phone sent. Returns false if out of range.
bool categories_get(int index, uint8_t *out_slot, const char **out_name);

// True when a picker is worth showing: the phone asked for one. Unlike
// earlier versions this no longer requires an existing category -- the picker
// always offers "New category", so it is useful even the very first time.
bool categories_should_ask(void);

// Name for a slot, or NULL if this watch has not been told about it.
const char *categories_name_for(uint8_t slot);
