// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include <pebble.h>

// Called once ui_category_show() has an answer. Exactly one of these
// describes it:
//
//  - name is non-NULL: the user spoke a brand-new category on the spot, via
//    "New category". It has no slot yet -- slot is meaningless here, and the
//    phone mints one once it hears the name.
//  - name is NULL and slot is not QNOTE_CATEGORY_NONE: an existing, already-
//    known category was chosen.
//  - name is NULL and slot is QNOTE_CATEGORY_NONE: None, or the picker (or
//    the "New category" dictation) was backed out of.
//
// name points at a buffer owned by ui_category.c, valid only for the
// duration of this call.
typedef void (*CategoryPickedHandler)(uint8_t slot, const char *name);

void ui_category_init(void);
void ui_category_deinit(void);

// Offers the categories this watch knows about, plus a "New category" row.
// Backing out is a valid answer, not a cancel: the note is already stored by
// the time this appears, so the handler runs either way and the note syncs
// uncategorised.
void ui_category_show(CategoryPickedHandler handler);
