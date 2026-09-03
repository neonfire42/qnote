// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "ui_list.h"

#include <string.h>

#include "capture.h"
#include "idle.h"
#include "notes.h"
#include "ui_detail.h"
#include "util.h"

#define SECTION_CAPTURE 0
#define SECTION_NOTES 1
#define NUM_SECTIONS 2

#define TOAST_MS 1600
#define TOAST_HEIGHT 56

#define SYNC_DOT_RADIUS 4
#define SYNC_DOT_INSET 12

static Window *s_window;
static MenuLayer *s_menu;
static TextLayer *s_toast_layer;
static AppTimer *s_toast_timer;

// Redrawn on demand from persist rather than kept in RAM: 12 records is 3 KB,
// and the menu only ever asks for one row at a time.
static QNoteRecord s_row;
static char s_row_title[48];
static char s_row_subtitle[24];

static uint16_t get_num_sections(MenuLayer *menu, void *context) { return NUM_SECTIONS; }

static uint16_t get_num_rows(MenuLayer *menu, uint16_t section, void *context) {
  if (section == SECTION_CAPTURE) {
    return 1;
  }
  // Always at least one row, so an empty cache can explain itself.
  const int count = notes_count();
  return count > 0 ? count : 1;
}

static int16_t get_header_height(MenuLayer *menu, uint16_t section, void *context) {
  // No header above the capture action; it reads as a button on its own.
  return section == SECTION_CAPTURE ? 0 : MENU_CELL_BASIC_HEADER_HEIGHT;
}

static void draw_header(GContext *ctx, const Layer *cell, uint16_t section, void *context) {
  if (section == SECTION_NOTES) {
    menu_cell_basic_header_draw(ctx, cell, "Notes");
  }
}

static void draw_row(GContext *ctx, const Layer *cell, MenuIndex *index, void *context) {
  if (index->section == SECTION_CAPTURE) {
    menu_cell_basic_draw(ctx, cell, "New note", "Speak to capture", NULL);
    return;
  }

  if (notes_count() == 0) {
    menu_cell_basic_draw(ctx, cell, "Nothing yet", "Notes appear here", NULL);
    return;
  }

  if (!notes_get(index->row, &s_row)) {
    return;
  }

  util_first_line(s_row.text, s_row.text_len, s_row_title, sizeof(s_row_title));
  util_format_age((time_t)s_row.timestamp_utc, s_row_subtitle, sizeof(s_row_subtitle));

  menu_cell_basic_draw(ctx, cell, s_row_title, s_row_subtitle, NULL);

  // Sync marker, drawn rather than typed: the Pebble system font has no filled
  // or hollow circle glyph and renders one as tofu. Filled means the phone has
  // the note; an outline means it is still only on the watch.
  const GRect cell_bounds = layer_get_bounds(cell);
  const GPoint dot = GPoint(cell_bounds.size.w - SYNC_DOT_INSET,
                            cell_bounds.size.h - SYNC_DOT_INSET);
  const GColor ink = menu_cell_layer_is_highlighted(cell) ? GColorWhite : QNOTE_COLOR_ACCENT;

  if (s_row.flags & QNOTE_FLAG_SYNCED) {
    graphics_context_set_fill_color(ctx, ink);
    graphics_fill_circle(ctx, dot, SYNC_DOT_RADIUS);
  } else {
    graphics_context_set_stroke_color(ctx, ink);
    graphics_draw_circle(ctx, dot, SYNC_DOT_RADIUS);
  }
}

// Moving the selection is the only signal we get for the up and down buttons:
// MenuLayer owns those, so there is no click handler of ours to hang this on.
static void selection_changed(MenuLayer *menu, MenuIndex new_index, MenuIndex old_index,
                              void *context) {
  idle_poke();
}

static void select_click(MenuLayer *menu, MenuIndex *index, void *context) {
  idle_poke();
  if (index->section == SECTION_CAPTURE) {
    capture_start();
  } else if (notes_count() > 0) {
    ui_detail_show(index->row);
  }
}

static void hide_toast(void *data) {
  s_toast_timer = NULL;
  layer_set_hidden(text_layer_get_layer(s_toast_layer), true);
}

void ui_list_toast(const char *message, bool success) {
  // A dictation result can land after the window is gone (the app idles out
  // while the system dictation UI is up), and the layer is destroyed by then.
  if (!s_toast_layer) {
    return;
  }
  text_layer_set_text(s_toast_layer, message);
  text_layer_set_background_color(s_toast_layer, success ? QNOTE_COLOR_ACCENT : GColorDarkCandyAppleRed);
  layer_set_hidden(text_layer_get_layer(s_toast_layer), false);

  if (s_toast_timer) {
    app_timer_reschedule(s_toast_timer, TOAST_MS);
  } else {
    s_toast_timer = app_timer_register(TOAST_MS, hide_toast, NULL);
  }
}

void ui_list_reload(void) {
  if (s_menu) {
    menu_layer_reload_data(s_menu);
  }
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  const GRect bounds = layer_get_bounds(root);

  s_menu = menu_layer_create(bounds);
  menu_layer_set_callbacks(s_menu, NULL,
                           (MenuLayerCallbacks){
                               .get_num_sections = get_num_sections,
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

  // Toast sits above the menu and starts hidden.
  s_toast_layer = text_layer_create(
      GRect(0, bounds.size.h - TOAST_HEIGHT, bounds.size.w, TOAST_HEIGHT));
  text_layer_set_font(s_toast_layer, fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD));
  text_layer_set_text_color(s_toast_layer, GColorWhite);
  text_layer_set_text_alignment(s_toast_layer, GTextAlignmentCenter);
  text_layer_set_overflow_mode(s_toast_layer, GTextOverflowModeWordWrap);
  layer_set_hidden(text_layer_get_layer(s_toast_layer), true);
  layer_add_child(root, text_layer_get_layer(s_toast_layer));
}

static void window_unload(Window *window) {
  if (s_toast_timer) {
    app_timer_cancel(s_toast_timer);
    s_toast_timer = NULL;
  }
  text_layer_destroy(s_toast_layer);
  s_toast_layer = NULL;
  menu_layer_destroy(s_menu);
  s_menu = NULL;
}

// Ages in the subtitles go stale while a note is open, so refresh on return.
static void window_appear(Window *window) { ui_list_reload(); }

Window *ui_list_get_window(void) { return s_window; }

void ui_list_init(void) {
  s_window = window_create();
  window_set_background_color(s_window, QNOTE_COLOR_BG);
  window_set_window_handlers(s_window, (WindowHandlers){
                                           .load = window_load,
                                           .appear = window_appear,
                                           .unload = window_unload,
                                       });
}

void ui_list_deinit(void) { window_destroy(s_window); }
