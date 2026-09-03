// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "idle.h"

static AppTimer *s_timer;
static bool s_suspended;

static void on_expired(void *data) {
  s_timer = NULL;
  APP_LOG(APP_LOG_LEVEL_INFO, "idle for %d minutes, closing", QNOTE_IDLE_TIMEOUT_MS / 60000);
  // Popping the last window ends the app, and the firmware falls back to the
  // watchface. There is no API to ask for the watchface directly.
  window_stack_pop_all(false);
}

static void schedule(void) {
  if (s_suspended) {
    return;
  }
  if (s_timer) {
    app_timer_reschedule(s_timer, QNOTE_IDLE_TIMEOUT_MS);
  } else {
    s_timer = app_timer_register(QNOTE_IDLE_TIMEOUT_MS, on_expired, NULL);
  }
}

static void cancel(void) {
  if (s_timer) {
    app_timer_cancel(s_timer);
    s_timer = NULL;
  }
}

void idle_init(void) {
  s_suspended = false;
  schedule();
}

void idle_deinit(void) {
  cancel();
  s_suspended = false;
}

void idle_poke(void) { schedule(); }

void idle_suspend(void) {
  s_suspended = true;
  cancel();
}

void idle_resume(void) {
  s_suspended = false;
  schedule();
}
