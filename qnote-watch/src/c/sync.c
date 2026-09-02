// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "sync.h"

#include <string.h>

// Datalogging tag. The companion sees it in DataLogSession.tag and uses it to
// tell qnote records apart from any other session the watch might send.
#define QNOTE_DL_TAG 0x716E7431  // "qnt1"

static DataLoggingSessionRef s_log;
static SyncChangedHandler s_on_changed;
static SyncCaptureHandler s_on_capture_request;

// Id of the note currently in the AppMessage outbox. 0 means idle. The ack from
// the phone arrives separately, so we only use this to avoid overlapping sends.
static uint32_t s_inflight_id;

static QNoteRecord s_pending;

bool sync_is_busy(void) { return s_inflight_id != 0; }

static void send_record(const QNoteRecord *rec) {
  if (s_inflight_id != 0) {
    return;  // One at a time; sync_flush() picks the rest up later.
  }
  if (!connection_service_peek_pebble_app_connection()) {
    return;  // Datalogging already holds it, so there is nothing to lose here.
  }

  DictionaryIterator *iter;
  const AppMessageResult begin = app_message_outbox_begin(&iter);
  if (begin != APP_MSG_OK) {
    APP_LOG(APP_LOG_LEVEL_WARNING, "outbox_begin failed: %d", (int)begin);
    return;
  }

  // text[] is not NUL-terminated in the record, so stage a terminated copy.
  // Static rather than on the stack: Pebble's app stack is small and a 245-byte
  // frame under the AppMessage call chain is not worth the risk.
  static char text[QNOTE_TEXT_MAX + 1];
  const size_t len = rec->text_len > QNOTE_TEXT_MAX ? QNOTE_TEXT_MAX : rec->text_len;
  memcpy(text, rec->text, len);
  text[len] = '\0';

  dict_write_uint32(iter, MESSAGE_KEY_NOTE_ID, rec->id);
  dict_write_uint32(iter, MESSAGE_KEY_NOTE_TS, rec->timestamp_utc);
  dict_write_cstring(iter, MESSAGE_KEY_NOTE_TEXT, text);

  const AppMessageResult result = app_message_outbox_send();
  if (result == APP_MSG_OK) {
    s_inflight_id = rec->id;
  } else {
    APP_LOG(APP_LOG_LEVEL_WARNING, "outbox_send failed: %d", (int)result);
  }
}

void sync_flush(void) {
  if (s_inflight_id != 0) {
    return;
  }
  if (notes_next_unsynced(&s_pending)) {
    send_record(&s_pending);
  }
}

void sync_submit(const QNoteRecord *rec) {
  if (s_log) {
    const DataLoggingResult result = data_logging_log(s_log, rec, 1);
    if (result != DATA_LOGGING_SUCCESS) {
      APP_LOG(APP_LOG_LEVEL_ERROR, "data_logging_log failed: %d", (int)result);
    }
  }
  send_record(rec);
}

static void inbox_received(DictionaryIterator *iter, void *context) {
  bool changed = false;
  bool advance = false;

  Tuple *ack = dict_find(iter, MESSAGE_KEY_ACK_ID);
  if (ack) {
    notes_mark_synced(ack->value->uint32);
    changed = true;
    // That note is settled, so the queue can move on to the next one.
    advance = true;
  }

  Tuple *del = dict_find(iter, MESSAGE_KEY_DELETE_ID);
  if (del) {
    notes_delete_by_id(del->value->uint32);
    changed = true;
  }

  if (changed && s_on_changed) {
    s_on_changed();
  }

  // Handled last: the phone asking for a resend should see the effect of the
  // ack it sent in the same message.
  if (advance || dict_find(iter, MESSAGE_KEY_SYNC_REQUEST)) {
    sync_flush();
  }

  // The companion app was opened and wants a note straight away. This is a
  // deliberate request rather than an inference from launch_reason(), so
  // merely opening the watchapp from the phone never trips the microphone.
  if (dict_find(iter, MESSAGE_KEY_START_CAPTURE) && s_on_capture_request) {
    s_on_capture_request();
  }
}

static void inbox_dropped(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "inbox dropped: %d", (int)reason);
}

static void outbox_sent(DictionaryIterator *iter, void *context) {
  // Delivered to the phone. The companion still has to store it and send back
  // ACK_ID before we mark the note synced and let it become evictable.
  //
  // Deliberately no flush here: the note we just sent is still unsynced, so
  // sync_flush() would pick the very same one and resend it forever. The next
  // note goes out when ACK_ID arrives and clears this one.
  s_inflight_id = 0;
}

static void outbox_failed(DictionaryIterator *iter, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "outbox failed: %d", (int)reason);
  s_inflight_id = 0;
  // No immediate retry: the datalogging copy is the durable path, and retrying
  // in a tight loop while disconnected only burns battery.
}

static void connection_changed(bool connected) {
  if (connected) {
    sync_flush();
  }
}

void sync_init(SyncChangedHandler on_changed, SyncCaptureHandler on_capture_request) {
  s_on_changed = on_changed;
  s_on_capture_request = on_capture_request;
  s_inflight_id = 0;

  // resume=true so notes spooled on earlier launches keep their session and
  // still reach the phone.
  s_log = data_logging_create(QNOTE_DL_TAG, DATA_LOGGING_BYTE_ARRAY, sizeof(QNoteRecord), true);
  if (!s_log) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "data_logging_create failed");
  }

  app_message_register_inbox_received(inbox_received);
  app_message_register_inbox_dropped(inbox_dropped);
  app_message_register_outbox_sent(outbox_sent);
  app_message_register_outbox_failed(outbox_failed);

  const AppMessageResult opened =
      app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());
  if (opened != APP_MSG_OK) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "app_message_open failed: %d", (int)opened);
  }

  connection_service_subscribe((ConnectionHandlers){
      .pebble_app_connection_handler = connection_changed,
  });

  sync_flush();
}

void sync_deinit(void) {
  connection_service_unsubscribe();
  // The datalogging session is deliberately not finished: leaving it open lets
  // the watch deliver spooled notes after the app closes.
  s_log = NULL;
  s_on_changed = NULL;
  s_on_capture_request = NULL;
}
