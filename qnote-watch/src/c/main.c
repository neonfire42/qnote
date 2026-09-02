#include <pebble.h>

#include "capture.h"
#include "notes.h"
#include "sync.h"
#include "ui_detail.h"
#include "ui_list.h"

static void on_capture_result(const char *message, bool success) {
  ui_list_toast(message, success);
}

// The phone acknowledged, deleted, or otherwise changed a note.
static void on_sync_changed(void) { ui_list_reload(); }

static void init(void) {
  notes_init();
  ui_list_init();
  ui_detail_init();
  capture_init(on_capture_result);
  sync_init(on_sync_changed);

  window_stack_push(ui_list_get_window(), true);

  // Quick Launch is the whole point of a capture app: hold the configured
  // button and start speaking, no menu in between. Every other launch reason
  // (launcher, the phone opening the app, a dev install) shows the list, which
  // is what someone who did not ask for the microphone expects.
  if (launch_reason() == APP_LAUNCH_QUICK_LAUNCH) {
    capture_start();
  }
}

static void deinit(void) {
  sync_deinit();
  capture_deinit();
  ui_detail_deinit();
  ui_list_deinit();
}

int main(void) {
  init();
  app_event_loop();
  deinit();
  return 0;
}
