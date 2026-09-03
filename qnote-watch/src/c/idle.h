// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include <pebble.h>

// Closes qnote when it has been sitting untouched.
//
// A watchapp holds the screen until something takes it away, and qnote is
// opened dozens of times a day for a few seconds each — so one left on the note
// list is nearly always one the wearer walked away from. Firmware returns to
// the watchface when the last window is popped, which is where they wanted to
// be anyway.
//
// 90 seconds: long enough to read the longest note qnote can hold, short
// enough that a forgotten app is not still there at the next glance.
#define QNOTE_IDLE_TIMEOUT_MS (90 * 1000)

void idle_init(void);
void idle_deinit(void);

// Restart the countdown. Called from the places that mean a person is present:
// moving the selection, opening a note, picking a category.
void idle_poke(void);

// Stop and restart counting around something that owns the screen instead of
// us — dictation, where the system UI is up and our windows are not what the
// wearer is looking at.
void idle_suspend(void);
void idle_resume(void);
