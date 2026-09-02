#pragma once

#include <pebble.h>

void ui_list_init(void);
void ui_list_deinit(void);

Window *ui_list_get_window(void);

// Redraws the list after the note cache changes, from either end.
void ui_list_reload(void);

// Shows a short-lived message over the list (saved, sync errors, and so on).
void ui_list_toast(const char *message, bool success);
