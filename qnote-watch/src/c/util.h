// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include <pebble.h>

// qnote palette. Emery is a 64-colour display, so these all land exactly.
#define QNOTE_COLOR_ACCENT GColorVividCerulean
#define QNOTE_COLOR_BG GColorWhite
#define QNOTE_COLOR_FG GColorBlack

// Writes a compact relative age ("now", "4m", "2h", "3d") into buf.
void util_format_age(time_t when, char *buf, size_t buf_size);

// Writes an absolute stamp ("Mon 14:05") into buf.
void util_format_stamp(time_t when, char *buf, size_t buf_size);

// Copies at most buf_size-1 bytes of a note's first line into buf, so a
// multi-line transcription still gives the list a one-line title.
void util_first_line(const char *text, size_t text_len, char *buf, size_t buf_size);
