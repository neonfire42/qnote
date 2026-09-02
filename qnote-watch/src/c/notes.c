#include "notes.h"

#include <string.h>

// Persist keys. Records live at PERSIST_NOTE_BASE + slot.
#define PERSIST_COUNT 1
#define PERSIST_NEXT_ID 2
#define PERSIST_HEAD 3
#define PERSIST_NOTE_BASE 100

// Ring buffer: s_head is the slot holding the newest note. Logical index 0 is
// the newest, so index n maps to slot (head - n) wrapped. Storing a new note
// advances the head and overwrites the oldest slot, which is why notes_add()
// checks that slot's sync flag before clobbering it.
static int s_count;
static int s_head;
static uint32_t s_next_id;

// One shared 256 B staging buffer. Pebble's stack is small and QNoteRecord is
// exactly a persist value, so we never put one on the stack.
static QNoteRecord s_scratch;

// Largest length <= max that does not cut a UTF-8 character in half. Dictation
// returns UTF-8, and a blind memcpy at the byte cap can split a multi-byte
// character, which would reach the phone as a broken glyph.
static size_t truncate_utf8(const char *s, size_t max) {
  size_t i = max;
  // Walk back over continuation bytes (10xxxxxx) to the character's lead byte.
  while (i > 0 && ((unsigned char)s[i] & 0xC0) == 0x80) {
    i--;
  }
  const unsigned char lead = (unsigned char)s[i];
  size_t need = 1;
  if ((lead & 0xE0) == 0xC0) {
    need = 2;
  } else if ((lead & 0xF0) == 0xE0) {
    need = 3;
  } else if ((lead & 0xF8) == 0xF0) {
    need = 4;
  }
  // Keep the whole character or drop it entirely; never half of it.
  return (i + need <= max) ? max : i;
}

static int slot_for_index(int index) {
  return ((s_head - index) % QNOTE_CACHE_MAX + QNOTE_CACHE_MAX) % QNOTE_CACHE_MAX;
}

static bool read_slot(int slot, QNoteRecord *out) {
  const int key = PERSIST_NOTE_BASE + slot;
  if (!persist_exists(key)) {
    return false;
  }
  return persist_read_data(key, out, sizeof(QNoteRecord)) == sizeof(QNoteRecord);
}

static void write_slot(int slot, const QNoteRecord *rec) {
  persist_write_data(PERSIST_NOTE_BASE + slot, rec, sizeof(QNoteRecord));
}

void notes_init(void) {
  s_count = persist_exists(PERSIST_COUNT) ? persist_read_int(PERSIST_COUNT) : 0;
  s_head = persist_exists(PERSIST_HEAD) ? persist_read_int(PERSIST_HEAD) : -1;
  s_next_id = persist_exists(PERSIST_NEXT_ID) ? (uint32_t)persist_read_int(PERSIST_NEXT_ID) : 1;

  // Guard against a persist layout change or a partial write leaving nonsense
  // behind; a reset cache beats reading garbage into the UI.
  if (s_count < 0 || s_count > QNOTE_CACHE_MAX || s_head < -1 || s_head >= QNOTE_CACHE_MAX) {
    APP_LOG(APP_LOG_LEVEL_WARNING, "note cache invalid (count=%d head=%d), resetting", s_count,
            s_head);
    s_count = 0;
    s_head = -1;
  }
  if (s_next_id == 0) {
    s_next_id = 1;
  }
}

int notes_count(void) { return s_count; }

bool notes_get(int index, QNoteRecord *out) {
  if (index < 0 || index >= s_count) {
    return false;
  }
  return read_slot(slot_for_index(index), out);
}

QNoteAddResult notes_add(const char *text, QNoteRecord *out_record) {
  const int target = (s_head + 1) % QNOTE_CACHE_MAX;

  // Only the oldest note is ever at risk, and only once the ring is full.
  if (s_count == QNOTE_CACHE_MAX) {
    if (read_slot(target, &s_scratch) && !(s_scratch.flags & QNOTE_FLAG_SYNCED)) {
      return QNOTE_ADD_FULL_UNSYNCED;
    }
  }

  memset(&s_scratch, 0, sizeof(s_scratch));
  s_scratch.id = s_next_id;
  s_scratch.timestamp_utc = (uint32_t)time(NULL);

  size_t len = strlen(text);
  if (len > QNOTE_TEXT_MAX) {
    len = truncate_utf8(text, QNOTE_TEXT_MAX);
    s_scratch.flags |= QNOTE_FLAG_TRUNCATED;
  }
  memcpy(s_scratch.text, text, len);
  s_scratch.text_len = (uint16_t)len;

  write_slot(target, &s_scratch);
  s_head = target;
  if (s_count < QNOTE_CACHE_MAX) {
    s_count++;
  }
  s_next_id++;

  persist_write_int(PERSIST_HEAD, s_head);
  persist_write_int(PERSIST_COUNT, s_count);
  persist_write_int(PERSIST_NEXT_ID, (int)s_next_id);

  if (out_record) {
    memcpy(out_record, &s_scratch, sizeof(QNoteRecord));
  }
  return QNOTE_ADD_OK;
}

// Returns the logical index of a note id, or -1.
static int index_of_id(uint32_t id) {
  for (int i = 0; i < s_count; i++) {
    if (read_slot(slot_for_index(i), &s_scratch) && s_scratch.id == id) {
      return i;
    }
  }
  return -1;
}

void notes_mark_synced(uint32_t id) {
  const int index = index_of_id(id);
  if (index < 0) {
    return;
  }
  // index_of_id left the matching record in s_scratch.
  if (s_scratch.flags & QNOTE_FLAG_SYNCED) {
    return;  // Already flagged; skip the flash write.
  }
  s_scratch.flags |= QNOTE_FLAG_SYNCED;
  write_slot(slot_for_index(index), &s_scratch);
}

void notes_delete_by_id(uint32_t id) {
  const int index = index_of_id(id);
  if (index < 0) {
    return;
  }

  // Close the gap by pulling every older note one step toward the head, so the
  // ring stays contiguous behind s_head.
  for (int i = index; i < s_count - 1; i++) {
    if (read_slot(slot_for_index(i + 1), &s_scratch)) {
      write_slot(slot_for_index(i), &s_scratch);
    }
  }
  persist_delete(PERSIST_NOTE_BASE + slot_for_index(s_count - 1));
  s_count--;
  persist_write_int(PERSIST_COUNT, s_count);
}

bool notes_next_unsynced(QNoteRecord *out) {
  // Oldest first, so the phone receives notes in the order they were spoken.
  for (int i = s_count - 1; i >= 0; i--) {
    if (notes_get(i, out) && !(out->flags & QNOTE_FLAG_SYNCED)) {
      return true;
    }
  }
  return false;
}
