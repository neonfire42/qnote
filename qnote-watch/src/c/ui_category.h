// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include <pebble.h>

// Receives the chosen slot, or QNOTE_CATEGORY_NONE when the picker was backed
// out of. Called exactly once per ui_category_show().
typedef void (*CategoryPickedHandler)(uint8_t slot);

void ui_category_init(void);
void ui_category_deinit(void);

// Offers the categories this watch knows about. Backing out is a valid answer,
// not a cancel: the note is already stored by the time this appears, so the
// handler runs either way and the note syncs uncategorised.
void ui_category_show(CategoryPickedHandler handler);
