// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include "notes.h"

void ui_detail_init(void);
void ui_detail_deinit(void);

// Pushes the detail screen for the note at the given list index.
void ui_detail_show(int note_index);
