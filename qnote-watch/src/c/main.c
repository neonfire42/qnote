// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include <pebble.h>

#include "capture.h"
#include "categories.h"
#include "idle.h"
#include "notes.h"
#include "sync.h"
#include "ui_category.h"
#include "ui_detail.h"
#include "ui_list.h"

static void on_capture_result(const char *message, bool success) {
  ui_list_toast(message, success);
}

// The phone acknowledged, deleted, or otherwise changed a note.
static void on_sync_changed(void) { ui_list_reload(); }

// The companion app opened and asked for a note right away.
static void on_capture_requested(void) { capture_start(); }

static void init(void) {
  notes_init();
  categories_init();
  ui_list_init();
  ui_detail_init();
  ui_category_init();
  capture_init(on_capture_result);
  sync_init(on_sync_changed, on_capture_requested);
  idle_init();

  window_stack_push(ui_list_get_window(), true);

  // Opening qnote means capturing a note, whatever route got you here —
  // launcher, Quick Launch, or the phone. The list is still one Back press
  // away, and dismissing dictation is silent, so browsing costs one button.
  capture_start();
}

static void deinit(void) {
  idle_deinit();
  sync_deinit();
  capture_deinit();
  ui_category_deinit();
  ui_detail_deinit();
  ui_list_deinit();
}

int main(void) {
  init();
  app_event_loop();
  deinit();
  return 0;
}
