// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#pragma once

#include <pebble.h>

// Maximum UTF-8 bytes of transcription we keep per note. Also the buffer size
// handed to dictation_session_create(), so the watch never transcribes more
// than it can store.
#define QNOTE_TEXT_MAX 244

// Notes cached on the watch. Persist gives us 256 B per value and ~4 KB total,
// so 12 records (3 KB) plus the counter keys is the practical ceiling. The
// phone is the archive; this is a capture buffer.
#define QNOTE_CACHE_MAX 12

// Wire format, shared byte-for-byte with the Android companion. Both transports
// (datalogging and AppMessage) describe the same record, and datalogging needs a
// fixed item size, so this struct is the contract. Little-endian (ARM).
// NoteRecordCodec.kt on the Android side must match this exactly.
typedef struct __attribute__((packed)) {
  uint32_t id;             // monotonic, from the persisted counter
  uint32_t timestamp_utc;  // time(NULL) at capture
  uint16_t text_len;       // bytes used in text[]; not NUL-terminated
  uint8_t flags;           // see QNOTE_FLAG_*
  uint8_t category_slot;   // 0 = uncategorised; otherwise a phone-assigned slot
  char text[QNOTE_TEXT_MAX];  // UTF-8, zero-padded
} QNoteRecord;

_Static_assert(sizeof(QNoteRecord) == 256, "QNoteRecord must be exactly 256 bytes");

// Dictation filled the buffer and the transcription was cut short.
#define QNOTE_FLAG_TRUNCATED (1 << 0)
// Watch-local: the phone acknowledged this note. Travels over the wire because
// it lives in the same struct; the companion ignores it.
#define QNOTE_FLAG_SYNCED (1 << 1)
// Watch-local: the note has been handed to datalogging. The spool is the path
// that survives being out of range, and a note now waits for its category
// before it is submitted — so this records which notes never got that far and
// need spooling on the next launch.
#define QNOTE_FLAG_SPOOLED (1 << 2)

// category_slot is a number, not a name: one byte is all the record had spare.
// The phone owns the slot table and assigns each category name a slot it never
// reuses, so a note captured out of range still resolves to the right name days
// later, whatever the category list did in between. 0 means uncategorised, and
// is what a watch that never heard from the phone always sends.
#define QNOTE_CATEGORY_NONE 0

typedef enum {
  QNOTE_ADD_OK,
  // The cache is full and the note we would overwrite has not reached the phone
  // yet. Refusing beats silently dropping a note the user spoke.
  QNOTE_ADD_FULL_UNSYNCED,
} QNoteAddResult;

void notes_init(void);

// Number of cached notes, 0..QNOTE_CACHE_MAX.
int notes_count(void);

// index 0 is the newest note. Returns false if index is out of range.
bool notes_get(int index, QNoteRecord *out);

// Stores a note and assigns it an id. text is copied and truncated to
// QNOTE_TEXT_MAX bytes. The note starts uncategorised. On success out_record
// holds the stored record.
QNoteAddResult notes_add(const char *text, QNoteRecord *out_record);

// Tags a stored note with a category slot. Called between notes_add() and
// sync_submit() when the user picks one, so the slot is on the record before
// either transport carries it. No-op if the id is unknown.
void notes_set_category(uint32_t id, uint8_t slot);

// Marks a note synced so it becomes evictable. No-op if the id is unknown.
void notes_mark_synced(uint32_t id);

// Removes a note by id, closing the gap. No-op if the id is unknown.
void notes_delete_by_id(uint32_t id);

// Fills out with the oldest note still missing a phone acknowledgement.
// Returns false when everything has synced.
bool notes_next_unsynced(QNoteRecord *out);

// Marks a note as handed to datalogging. No-op if the id is unknown.
void notes_mark_spooled(uint32_t id);

// Fills out with the oldest note that never reached the datalogging spool and
// has not otherwise been acknowledged. Returns false when there is none.
bool notes_next_unspooled(QNoteRecord *out);
