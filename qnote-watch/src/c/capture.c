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
// the note it holds. Also covers the "New category" sub-dictation below, which
// happens entirely within this same window.
static bool s_awaiting_category;

// True while s_session is being used to type a category name rather than
// capture a note -- read by dictation_callback() to route its result
// correctly, since both uses share the one session (see below).
static bool s_dictating_category_name;
static CategoryNameHandler s_category_name_handler;

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
// straight from the transcription, or once the picker has an answer -- the
// note is stored either way, so this only ever adds the tag and hands the
// record to both transports.
//
// new_category_name is non-NULL exactly when the user spoke a brand-new
// category on the spot: the watch has no slot for it, so slot is left at
// QNOTE_CATEGORY_NONE on the record and the name rides to the phone as a
// separate field of the same live AppMessage. The phone mints the real slot
// and hands it back on its next category push -- this note's own datalogged
// copy, though, only ever carries a number, so if it reaches the phone solely
// through the spool (out of range the whole time), it arrives uncategorised.
// That is a corner of the 256-byte record leaving no room for a name, not a
// bug: the same note synced live would have kept its category.
static void finish_capture(uint8_t slot, const char *new_category_name) {
  s_awaiting_category = false;

  const char *display_name = new_category_name;
  if (!display_name && slot != QNOTE_CATEGORY_NONE) {
    notes_set_category(s_record.id, slot);
    s_record.category_slot = slot;
    display_name = categories_name_for(slot);
  }

  sync_submit(&s_record, new_category_name);
  vibes_short_pulse();
  ui_list_reload();

  if (!s_handler) {
    return;
  }
  if (display_name) {
    snprintf(s_saved_message, sizeof(s_saved_message), "Saved to\n%s", display_name);
    s_handler(s_saved_message, true);
  } else {
    s_handler("Saved", true);
  }
}

static void on_category_picked(uint8_t slot, const char *name) { finish_capture(slot, name); }

static void dictation_callback(DictationSession *session, DictationSessionStatus status,
                               char *transcription, void *context) {
  s_in_progress = false;
  // Our windows are back on screen, so the countdown means something again.
  idle_resume();

  if (s_dictating_category_name) {
    s_dictating_category_name = false;
    CategoryNameHandler handler = s_category_name_handler;
    s_category_name_handler = NULL;
    const bool got_name = status == DictationSessionStatusSuccess && transcription[0] != '\0';
    // TEMPORARY DIAGNOSTIC (remove once the reported bug is found): confirms
    // this callback actually fires for the category-name dictation, and
    // whether it is reporting success. Double pulse = got a name; long pulse
    // = did not (wrong status, or an empty transcription despite Success).
    if (got_name) {
      vibes_double_pulse();
    } else {
      vibes_long_pulse();
    }
    if (handler) {
      handler(got_name ? transcription : NULL);
    }
    return;
  }

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
      finish_capture(QNOTE_CATEGORY_NONE, NULL);
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

// Deliberately reuses s_session rather than a second DictationSession object.
// An earlier version gave the category picker its own session, created once
// at startup and started independently of this one -- correct by the SDK's
// documented contract (only one session may be *started* at a time), but in
// practice a transcription answer reached both sessions' callbacks at once,
// producing a stray note out of text that was only ever meant to be a
// category name. Every other dictation in this app reuses one session across
// many captures without issue, so this mode flag brings category-name entry
// in line with that already-proven pattern instead of the untested
// two-objects one.
void capture_dictate_category_name(CategoryNameHandler handler) {
  s_dictating_category_name = true;
  s_category_name_handler = handler;
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
  s_dictating_category_name = false;
  s_category_name_handler = NULL;
}
