// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include "notes.h"

// Called whenever the phone changes the note cache, so the list can redraw.
typedef void (*SyncChangedHandler)(void);

// Called when the companion app asks the watch to start dictating.
typedef void (*SyncCaptureHandler)(void);

void sync_init(SyncChangedHandler on_changed, SyncCaptureHandler on_capture_request);
void sync_deinit(void);

// Spools a note through datalogging (survives being out of range, delivered
// even if the app is closed) and, when the phone is reachable, sends it over
// AppMessage too so it lands immediately. The companion deduplicates by id.
void sync_submit(const QNoteRecord *rec);

// Sends the oldest note the phone has not acknowledged, if any.
void sync_flush(void);

// True while an AppMessage is in flight.
bool sync_is_busy(void);
