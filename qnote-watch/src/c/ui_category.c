// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "ui_category.h"

#include <string.h>

#include "capture.h"
#include "categories.h"
#include "idle.h"
#include "notes.h"
#include "util.h"

static Window *s_window;
static MenuLayer *s_menu;
static CategoryPickedHandler s_handler;

// The answer, and whether it has been delivered yet. Back, an existing pick,
// and a completed "New category" dictation all share one exit path
// (window_disappear) so the handler cannot fire twice or not at all.
static uint8_t s_chosen;
static bool s_delivered;

// True from the moment "New category" starts its dictation until that
// dictation's callback runs. Needed because, per the SDK,
// WindowHandlers.disappear fires "when another window is pushed, or this
// window is popped" -- not only on a real exit. Starting the category-name
// dictation pushes ITS window on top of this one, which is a .disappear
// event too, and without this guard that would deliver an answer (None, with
// nothing spoken yet) before the user has said anything.
static bool s_awaiting_new_name;

// Set by on_category_name_dictated() on success, read by window_disappear().
// Empty means "no new name this time" -- cheaper than a separate bool, and
// there is no such thing as an empty category name to confuse it with
// (notes_add()-style zero-length input never reaches here).
static char s_new_category_name[QNOTE_CATEGORY_NAME_MAX + 1];

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
  s_chosen = slot;
  // Delivery happens in .disappear, which this pop triggers.
  window_stack_remove(s_window, true);
}

static void on_category_name_dictated(const char *name) {
  // Cleared before popping, so the .disappear this triggers is recognised as
  // the real exit rather than another "covered by a child window" event.
  s_awaiting_new_name = false;

  if (name) {
    strncpy(s_new_category_name, name, sizeof(s_new_category_name) - 1);
    s_new_category_name[sizeof(s_new_category_name) - 1] = '\0';
  }
  // Whatever happened -- spoken, dismissed, misheard -- the picker is done.
  // Dictation failing here costs only the category, the same as backing out
  // of the picker itself, so there is nothing to fall back to but None: it
  // does not reopen the menu for another attempt.
  window_stack_remove(s_window, true);
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

// The single exit. Select on an existing category sets s_chosen and pops; the
// "New category" dictation callback pops after (maybe) filling
// s_new_category_name; Back pops with neither set, which is the right answer
// for "skip this".
static void window_disappear(Window *window) {
  if (s_awaiting_new_name) {
    // Covered by the category-name dictation's own window, not a real exit.
    return;
  }
  if (s_delivered) {
    return;
  }
  s_delivered = true;
  if (s_handler) {
    s_handler(s_chosen, s_new_category_name[0] != '\0' ? s_new_category_name : NULL);
  }
}

void ui_category_show(CategoryPickedHandler handler) {
  s_handler = handler;
  s_chosen = QNOTE_CATEGORY_NONE;
  s_new_category_name[0] = '\0';
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
