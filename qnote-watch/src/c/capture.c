#include "capture.h"

#include "notes.h"
#include "sync.h"
#include "ui_list.h"

static DictationSession *s_session;
static CaptureResultHandler s_handler;
static QNoteRecord s_record;

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

static void dictation_callback(DictationSession *session, DictationSessionStatus status,
                               char *transcription, void *context) {
  if (status != DictationSessionStatusSuccess) {
    APP_LOG(APP_LOG_LEVEL_INFO, "dictation ended: %d", (int)status);
    if (s_handler) {
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
      sync_submit(&s_record);
      vibes_short_pulse();
      ui_list_reload();
      if (s_handler) {
        s_handler("Saved", true);
      }
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
}
