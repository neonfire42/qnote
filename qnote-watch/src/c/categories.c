// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "categories.h"

#include <string.h>

// Persist keys. notes.c owns 1-3 and 100+; these sit in the gap between.
#define PERSIST_CAT_BLOB 10
#define PERSIST_CAT_ASK 11

typedef struct {
  uint8_t slot;
  // Points into s_blob rather than owning a copy: parsing rewrites the
  // separators to NUL in place, so every name is already a C string sitting in
  // the buffer we keep anyway.
  const char *name;
} CategoryEntry;

static char s_blob[QNOTE_CAT_BLOB_MAX + 1];
static CategoryEntry s_entries[QNOTE_CAT_MAX];
static int s_count;
static bool s_ask = true;

// Splits s_blob into entries, destroying the separators as it goes. A malformed
// line ends the parse rather than being skipped: the blob is written by one
// known sender, so a surprise means the rest is not trustworthy either.
static void parse(void) {
  s_count = 0;

  char *p = s_blob;
  while (*p && s_count < QNOTE_CAT_MAX) {
    if (*p < '0' || *p > '9') {
      break;
    }
    int slot = 0;
    while (*p >= '0' && *p <= '9') {
      slot = slot * 10 + (*p - '0');
      p++;
    }
    if (*p != '\t' || slot <= 0 || slot > 255) {
      break;
    }
    *p++ = '\0';

    const char *name = p;
    while (*p && *p != '\n') {
      p++;
    }
    if (*p == '\n') {
      *p++ = '\0';
    }

    if (*name) {
      s_entries[s_count].slot = (uint8_t)slot;
      s_entries[s_count].name = name;
      s_count++;
    }
  }
}

void categories_init(void) {
  s_blob[0] = '\0';
  s_count = 0;

  if (persist_exists(PERSIST_CAT_BLOB)) {
    persist_read_string(PERSIST_CAT_BLOB, s_blob, sizeof(s_blob));
    parse();
  }
  if (persist_exists(PERSIST_CAT_ASK)) {
    s_ask = persist_read_bool(PERSIST_CAT_ASK);
  }
}

void categories_set_blob(const char *blob) {
  if (!blob) {
    return;
  }
  strncpy(s_blob, blob, sizeof(s_blob) - 1);
  s_blob[sizeof(s_blob) - 1] = '\0';

  // Persist before parsing: parse() rewrites the separators in place, and what
  // we want on disk is the wire form we can parse again next launch.
  const status_t written = persist_write_string(PERSIST_CAT_BLOB, s_blob);
  if (written < 0) {
    // Out of persist space, most likely because the note ring is full. The list
    // still works for this session; the phone re-sends it on every app open.
    APP_LOG(APP_LOG_LEVEL_WARNING, "category list not persisted: %d", (int)written);
  }

  parse();
  APP_LOG(APP_LOG_LEVEL_INFO, "categories: %d received", s_count);
}

void categories_set_ask(bool ask) {
  if (s_ask == ask) {
    return;
  }
  s_ask = ask;
  persist_write_bool(PERSIST_CAT_ASK, ask);
}

int categories_count(void) { return s_count; }

bool categories_get(int index, uint8_t *out_slot, const char **out_name) {
  if (index < 0 || index >= s_count) {
    return false;
  }
  if (out_slot) {
    *out_slot = s_entries[index].slot;
  }
  if (out_name) {
    *out_name = s_entries[index].name;
  }
  return true;
}

bool categories_should_ask(void) { return s_ask && s_count > 0; }

const char *categories_name_for(uint8_t slot) {
  if (slot == 0) {
    return NULL;
  }
  for (int i = 0; i < s_count; i++) {
    if (s_entries[i].slot == slot) {
      return s_entries[i].name;
    }
  }
  // A note tagged before the list changed, or before this watch was told about
  // it. The phone still resolves the slot correctly; the watch just cannot name
  // it here.
  return NULL;
}
