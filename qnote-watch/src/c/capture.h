#pragma once

#include <pebble.h>

// Reports the outcome of a capture attempt so the UI can show a brief message.
typedef void (*CaptureResultHandler)(const char *message, bool success);

void capture_init(CaptureResultHandler handler);
void capture_deinit(void);

// Opens the system dictation UI. The result arrives asynchronously via the
// handler; a stored note also reaches the list through ui_list_reload().
void capture_start(void);
