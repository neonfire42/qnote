// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "ui_category.h"

#include "capture.h"
#include "categories.h"
#include "idle.h"
#include "notes.h"
#include "util.h"

static Window *s_window;
static MenuLayer *s_menu;
static CategoryPickedHandler s_handler;

// Whether the answer has been delivered yet, guarding deliver_and_close()
// against firing twice for the same ui_category_show() call.
static bool s_delivered;

// True from the moment "New category" starts its dictation until that
// dictation's callback runs. Needed because, per the SDK,
// WindowHandlers.disappear fires "when another window is pushed, or this
// window is popped" -- not only on a real exit. Starting the category-name
// dictation pushes ITS window on top of this one, which is a .disappear
// event too, and window_disappear() only exists to catch Back; without this
// guard it would treat "covered by our own child dictation" as Back and
// deliver None before the user has said anything.
static bool s_awaiting_new_name;

// Row 0 is "None"; the last row, computed from the live category count, is
// "New category".
#define ROW_NONE 0

static int row_new(void) { return 1 + categories_count(); }

static uint16_t get_num_rows(MenuLayer *menu, uint16_t section, void *context) {
  return 2 + categories_count();
}

static int16_t get_header_height(MenuLayer *menu, uint16_t section, void *context) {
  return MENU_CELL_BASIC_HEADER_HEIGHT;
}

static void draw_header(GContext *ctx, const Layer *cell, uint16_t section, void *context) {
  menu_cell_basic_header_draw(ctx, cell, "Category");
}

static void draw_row(GContext *ctx, const Layer *cell, MenuIndex *index, void *context) {
  if (index->row == ROW_NONE) {
    menu_cell_basic_draw(ctx, cell, "None", "Keep it uncategorised", NULL);
    return;
  }
  if (index->row == row_new()) {
    menu_cell_basic_draw(ctx, cell, "New category", "Speak a name", NULL);
    return;
  }

  const char *name = NULL;
  if (categories_get(index->row - 1, NULL, &name)) {
    menu_cell_basic_draw(ctx, cell, name, NULL, NULL);
  }
}

// Delivers the answer exactly once and closes the picker. The single call
// site every path funnels through, so nothing has to depend on whether
// window_stack_remove() below re-triggers .disappear -- it may not: .disappear
// fires on a *visibility* change, and by the time this runs the dictation
// window it may have opened has already closed, leaving this window already
// the visible one. Popping an already-visible window is not guaranteed to be
// a visibility change worth reporting, so relying on that second .disappear
// to deliver (as an earlier version did) could silently drop the answer.
static void deliver_and_close(uint8_t slot, const char *name) {
  if (s_delivered) {
    return;
  }
  s_delivered = true;
  s_awaiting_new_name = false;
  if (s_handler) {
    s_handler(slot, name);
  }
  window_stack_remove(s_window, true);
}

static void on_category_name_dictated(const char *name);

static void selection_changed(MenuLayer *menu, MenuIndex new_index, MenuIndex old_index,
                              void *context) {
  idle_poke();
}

static void select_click(MenuLayer *menu, MenuIndex *index, void *context) {
  idle_poke();

  if (index->row == row_new()) {
    // The picker window stays on the stack underneath; dictation's own UI
    // takes the screen from here and this window's turn comes once it is
    // done, in on_category_name_dictated().
    s_awaiting_new_name = true;
    capture_dictate_category_name(on_category_name_dictated);
    return;
  }

  uint8_t slot = QNOTE_CATEGORY_NONE;
  if (index->row != ROW_NONE) {
    categories_get(index->row - 1, &slot, NULL);
  }
  deliver_and_close(slot, NULL);
}

static void on_category_name_dictated(const char *name) {
  deliver_and_close(QNOTE_CATEGORY_NONE, name);
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);

  s_menu = menu_layer_create(layer_get_bounds(root));
  menu_layer_set_callbacks(s_menu, NULL,
                           (MenuLayerCallbacks){
                               .get_num_rows = get_num_rows,
                               .get_header_height = get_header_height,
                               .draw_header = draw_header,
                               .draw_row = draw_row,
                               .select_click = select_click,
                               .selection_changed = selection_changed,
                           });
  menu_layer_set_normal_colors(s_menu, QNOTE_COLOR_BG, QNOTE_COLOR_FG);
  menu_layer_set_highlight_colors(s_menu, QNOTE_COLOR_ACCENT, GColorWhite);
  menu_layer_set_click_config_onto_window(s_menu, window);
  layer_add_child(root, menu_layer_get_layer(s_menu));
}

static void window_unload(Window *window) {
  menu_layer_destroy(s_menu);
  s_menu = NULL;
}

// Catches the one exit deliver_and_close() cannot see coming: the Back
// button, which pops this window through the system's own click handling
// rather than any call site of ours. Every other way out already delivered
// explicitly by the time this runs, so the guard below makes this a no-op
// for all of them -- including the "covered by our own child dictation"
// window-push, which is a .disappear event too and would otherwise read as
// Back and hand over None before the user has said anything.
static void window_disappear(Window *window) {
  if (s_awaiting_new_name) {
    return;
  }
  deliver_and_close(QNOTE_CATEGORY_NONE, NULL);
}

void ui_category_show(CategoryPickedHandler handler) {
  s_handler = handler;
  s_delivered = false;
  s_awaiting_new_name = false;
  window_stack_push(s_window, true);
}

void ui_category_init(void) {
  s_window = window_create();
  window_set_background_color(s_window, QNOTE_COLOR_BG);
  window_set_window_handlers(s_window, (WindowHandlers){
                                           .load = window_load,
                                           .disappear = window_disappear,
                                           .unload = window_unload,
                                       });
}

void ui_category_deinit(void) { window_destroy(s_window); }
