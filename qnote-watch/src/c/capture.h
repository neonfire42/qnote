// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include <pebble.h>

// Reports the outcome of a capture attempt so the UI can show a brief message.
typedef void (*CaptureResultHandler)(const char *message, bool success);

void capture_init(CaptureResultHandler handler);
void capture_deinit(void);

// Opens the system dictation UI. The result arrives asynchronously via the
// handler; a stored note also reaches the list through ui_list_reload().
void capture_start(void);

// Called once capture_dictate_category_name()'s dictation has an answer.
// name is NULL if nothing usable was spoken (declined, misheard, cancelled).
// Points at a buffer owned by capture.c, valid only for this call.
typedef void (*CategoryNameHandler)(const char *name);

// Opens the SAME dictation session capture_start() uses, but in a mode where
// a successful transcription is handed to handler as a category name instead
// of becoming a note. There is deliberately only ever one DictationSession
// object in this app -- see the comment on s_dictating_category_name in
// capture.c for why a second one is worth avoiding.
void capture_dictate_category_name(CategoryNameHandler handler);
