// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "ui_category.h"

#include "categories.h"
#include "notes.h"
#include "util.h"

static Window *s_window;
static MenuLayer *s_menu;
static CategoryPickedHandler s_handler;

// The answer, and whether it has been delivered yet. Every way out of this
// window runs through .disappear, so Back and Select share one exit path and
// the handler cannot fire twice or not at all.
static uint8_t s_chosen;
static bool s_delivered;

// Row 0 is "None", so the categories start one below it.
#define ROW_NONE 0

static uint16_t get_num_rows(MenuLayer *menu, uint16_t section, void *context) {
  return 1 + categories_count();
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

  const char *name = NULL;
  if (categories_get(index->row - 1, NULL, &name)) {
    menu_cell_basic_draw(ctx, cell, name, NULL, NULL);
  }
}

static void select_click(MenuLayer *menu, MenuIndex *index, void *context) {
  uint8_t slot = QNOTE_CATEGORY_NONE;
  if (index->row != ROW_NONE) {
    categories_get(index->row - 1, &slot, NULL);
  }
  s_chosen = slot;
  // Delivery happens in .disappear, which this pop triggers.
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

// The single exit. Select sets s_chosen and pops; Back pops with s_chosen still
// at QNOTE_CATEGORY_NONE, which is the right answer for "skip this".
static void window_disappear(Window *window) {
  if (s_delivered) {
    return;
  }
  s_delivered = true;
  if (s_handler) {
    s_handler(s_chosen);
  }
}

void ui_category_show(CategoryPickedHandler handler) {
  s_handler = handler;
  s_chosen = QNOTE_CATEGORY_NONE;
  s_delivered = false;
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
