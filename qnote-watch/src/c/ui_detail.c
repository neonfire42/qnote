// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "ui_detail.h"

#include <stdio.h>
#include <string.h>

#include "categories.h"
#include "sync.h"
#include "ui_list.h"
#include "util.h"

#define DETAIL_MARGIN 6

static Window *s_window;
static ScrollLayer *s_scroll;
static TextLayer *s_stamp_layer;
static TextLayer *s_body_layer;
static ActionMenuLevel *s_action_root;

// The note currently on screen. Held by value because a phone-driven delete can
// change the cache underneath us while the detail window is up.
static QNoteRecord s_note;
static char s_stamp[40];
static char s_body[QNOTE_TEXT_MAX + 1];

static void perform_delete(ActionMenu *menu, const ActionMenuItem *action, void *context) {
  const uint32_t id = s_note.id;
  notes_delete_by_id(id);

  // Tell the phone as well, so deleting on the watch is not silently undone the
  // next time the companion syncs.
  DictionaryIterator *iter;
  if (app_message_outbox_begin(&iter) == APP_MSG_OK) {
    dict_write_uint32(iter, MESSAGE_KEY_DELETE_ID, id);
    app_message_outbox_send();
  }

  vibes_short_pulse();
  ui_list_reload();
  window_stack_remove(s_window, true);
}

static void detail_select(ClickRecognizerRef recognizer, void *context) {
  action_menu_open(&(ActionMenuConfig){
      .root_level = s_action_root,
      .colors = {.background = QNOTE_COLOR_ACCENT, .foreground = GColorWhite},
      .align = ActionMenuAlignCenter,
  });
}

// ScrollLayer owns UP/DOWN; this hook adds SELECT on top of them.
static void detail_click_config(void *context) {
  window_single_click_subscribe(BUTTON_ID_SELECT, detail_select);
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  const GRect bounds = layer_get_bounds(root);
  const int16_t text_w = bounds.size.w - 2 * DETAIL_MARGIN;

  s_scroll = scroll_layer_create(bounds);
  scroll_layer_set_shadow_hidden(s_scroll, true);
  scroll_layer_set_callbacks(s_scroll, (ScrollLayerCallbacks){
                                           .click_config_provider = detail_click_config,
                                       });
  scroll_layer_set_click_config_onto_window(s_scroll, window);

  s_stamp_layer = text_layer_create(GRect(DETAIL_MARGIN, DETAIL_MARGIN, text_w, 20));
  text_layer_set_font(s_stamp_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD));
  text_layer_set_text_color(s_stamp_layer, QNOTE_COLOR_ACCENT);
  text_layer_set_background_color(s_stamp_layer, GColorClear);
  scroll_layer_add_child(s_scroll, text_layer_get_layer(s_stamp_layer));

  s_body_layer = text_layer_create(GRect(DETAIL_MARGIN, DETAIL_MARGIN + 22, text_w, 20));
  text_layer_set_font(s_body_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
  text_layer_set_text_color(s_body_layer, QNOTE_COLOR_FG);
  text_layer_set_background_color(s_body_layer, GColorClear);
  text_layer_set_overflow_mode(s_body_layer, GTextOverflowModeWordWrap);
  scroll_layer_add_child(s_scroll, text_layer_get_layer(s_body_layer));

  layer_add_child(root, scroll_layer_get_layer(s_scroll));
}

static void window_unload(Window *window) {
  text_layer_destroy(s_body_layer);
  text_layer_destroy(s_stamp_layer);
  scroll_layer_destroy(s_scroll);
}

// .appear rather than .load: the window is created once and reused, so this is
// the hook that fires for every note the user opens.
static void window_appear(Window *window) {
  util_format_stamp((time_t)s_note.timestamp_utc, s_stamp, sizeof(s_stamp));

  // Append the category to the stamp line rather than adding a layer: it is one
  // short word, and the header already reads as the note's metadata.
  const char *category = categories_name_for(s_note.category_slot);
  if (category) {
    const size_t used = strlen(s_stamp);
    snprintf(s_stamp + used, sizeof(s_stamp) - used, " \xc2\xb7 %s", category);
  }

  text_layer_set_text(s_stamp_layer, s_stamp);
  text_layer_set_text(s_body_layer, s_body);

  const GRect bounds = layer_get_bounds(window_get_root_layer(window));
  const int16_t text_w = bounds.size.w - 2 * DETAIL_MARGIN;

  // TextLayer will not size itself, so measure the wrapped text and grow both
  // the layer and the scroll content to match.
  const GSize used = graphics_text_layout_get_content_size(
      s_body, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD), GRect(0, 0, text_w, 2000),
      GTextOverflowModeWordWrap, GTextAlignmentLeft);

  layer_set_frame(text_layer_get_layer(s_body_layer),
                  GRect(DETAIL_MARGIN, DETAIL_MARGIN + 22, text_w, used.h + 8));
  scroll_layer_set_content_size(s_scroll,
                                GSize(bounds.size.w, DETAIL_MARGIN + 22 + used.h + 16));
  scroll_layer_set_content_offset(s_scroll, GPointZero, false);
}

void ui_detail_show(int note_index) {
  if (!notes_get(note_index, &s_note)) {
    return;
  }
  const size_t len = s_note.text_len > QNOTE_TEXT_MAX ? QNOTE_TEXT_MAX : s_note.text_len;
  memcpy(s_body, s_note.text, len);
  s_body[len] = '\0';

  window_stack_push(s_window, true);
}

void ui_detail_init(void) {
  s_action_root = action_menu_level_create(1);
  action_menu_level_add_action(s_action_root, "Delete", perform_delete, NULL);

  s_window = window_create();
  window_set_background_color(s_window, QNOTE_COLOR_BG);
  window_set_window_handlers(s_window, (WindowHandlers){
                                           .load = window_load,
                                           .appear = window_appear,
                                           .unload = window_unload,
                                       });
}

void ui_detail_deinit(void) {
  window_destroy(s_window);
  action_menu_hierarchy_destroy(s_action_root, NULL, NULL);
}
