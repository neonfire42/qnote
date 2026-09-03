// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "capture.h"

#include <stdio.h>

#include "categories.h"
#include "idle.h"
#include "notes.h"
#include "sync.h"
#include "ui_category.h"
#include "ui_list.h"

static DictationSession *s_session;
static CaptureResultHandler s_handler;

// True between dictation_session_start() and its callback. The watchapp now
// starts a capture on launch, and the phone may send START_CAPTURE moments
// later, so the second request has to be dropped rather than restarting a
// session the user is already speaking into.
static bool s_in_progress;

// True between storing a note and the category picker answering. capture_start()
// bails while this is set: the phone can send START_CAPTURE at any moment, and
// a second dictation would overwrite s_record while the picker still refers to
// the note it holds.
static bool s_awaiting_category;

static QNoteRecord s_record;

// "Saved" on its own, or with the category appended when one was picked. Static
// because ui_list_toast() only keeps the pointer.
static char s_saved_message[40];

// Dictation allocates and owns the transcription buffer; we only choose its
// size. Matching the record means the system truncates for us and a note can
// never arrive longer than it can be stored.
#define CAPTURE_BUFFER_SIZE (QNOTE_TEXT_MAX + 1)

static const char *message_for_status(DictationSessionStatus status) {
  switch (status) {
    case DictationSessionStatusFailureTranscriptionRejected:
    case DictationSessionStatusFailureTranscriptionRejectedWithError:
      return "Discarded";
    case DictationSessionStatusFailureSystemAborted:
      return "Could not hear that";
    case DictationSessionStatusFailureNoSpeechDetected:
      return "No speech detected";
    case DictationSessionStatusFailureConnectivityError:
      return "Phone unreachable";
    case DictationSessionStatusFailureDisabled:
      return "Voice is off in\nPebble settings";
    case DictationSessionStatusFailureInternalError:
    case DictationSessionStatusFailureRecognizerError:
    default:
      return "Transcription failed";
  }
}

// Sends the note the record buffer holds and reports it. Reached either
// straight from the transcription or, when the picker was shown, once it has an
// answer — the note is stored either way, so this only ever adds the tag and
// hands the record to both transports.
static void finish_capture(uint8_t slot) {
  s_awaiting_category = false;

  const char *name = NULL;
  if (slot != QNOTE_CATEGORY_NONE) {
    notes_set_category(s_record.id, slot);
    s_record.category_slot = slot;
    name = categories_name_for(slot);
  }

  sync_submit(&s_record);
  vibes_short_pulse();
  ui_list_reload();

  if (!s_handler) {
    return;
  }
  if (name) {
    snprintf(s_saved_message, sizeof(s_saved_message), "Saved to\n%s", name);
    s_handler(s_saved_message, true);
  } else {
    s_handler("Saved", true);
  }
}

static void on_category_picked(uint8_t slot) { finish_capture(slot); }

static void dictation_callback(DictationSession *session, DictationSessionStatus status,
                               char *transcription, void *context) {
  s_in_progress = false;
  // Our windows are back on screen, so the countdown means something again.
  idle_resume();

  if (status != DictationSessionStatusSuccess) {
    APP_LOG(APP_LOG_LEVEL_INFO, "dictation ended: %d", (int)status);
    // Backing out of dictation is how you reach the list now that every launch
    // opens the microphone, so it is a normal navigation step, not an error.
    const bool dismissed = status == DictationSessionStatusFailureTranscriptionRejected;
    if (s_handler && !dismissed) {
      s_handler(message_for_status(status), false);
    }
    return;
  }

  switch (notes_add(transcription, &s_record)) {
    case QNOTE_ADD_FULL_UNSYNCED:
      // Every cached slot is still waiting on the phone. Say so rather than
      // overwriting a note the user spoke and never got to keep.
      if (s_handler) {
        s_handler("Watch full —\nopen qnote on phone", false);
      }
      return;

    case QNOTE_ADD_OK:
      // The note is in persist already, so the picker is safe to show: backing
      // out, or the app idling out with it open, costs the tag and nothing
      // else — sync_flush() sends the note uncategorised on the next launch.
      if (categories_should_ask()) {
        s_awaiting_category = true;
        ui_category_show(on_category_picked);
        return;
      }
      finish_capture(QNOTE_CATEGORY_NONE);
      return;
  }
}

void capture_start(void) {
  if (!s_session) {
    if (s_handler) {
      s_handler("No microphone", false);
    }
    return;
  }
  if (s_in_progress || s_awaiting_category) {
    return;
  }
  s_in_progress = true;
  // The dictation UI belongs to the system and can sit there as long as it
  // likes; closing qnote out from under it would be wrong.
  idle_suspend();
  dictation_session_start(s_session);
}

void capture_init(CaptureResultHandler handler) {
  s_handler = handler;
  s_session = dictation_session_create(CAPTURE_BUFFER_SIZE, dictation_callback, NULL);
  if (!s_session) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "dictation_session_create failed");
    return;
  }
  // Confirmation stays on: a note is worth reading back before it is kept.
  dictation_session_enable_confirmation(s_session, true);
  // Our own messages are more specific than the system dialogs.
  dictation_session_enable_error_dialogs(s_session, false);
}

void capture_deinit(void) {
  if (s_session) {
    dictation_session_destroy(s_session);
    s_session = NULL;
  }
  s_handler = NULL;
  s_in_progress = false;
  s_awaiting_category = false;
}
