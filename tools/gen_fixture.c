// Emits the exact bytes the watch writes for a note, as a hex string for the
// Android codec test (qnote-android/.../NoteRecordCodecTest.kt).
//
// The struct below is a verbatim copy of QNoteRecord in
// qnote-watch/src/c/notes.h. Every field is a fixed-width type inside a packed
// struct, so a little-endian host lays it out identically to the ARM target,
// and the watch build's own _Static_assert pins the 256-byte size.
//
//   cc -o /tmp/gen_fixture tools/gen_fixture.c && /tmp/gen_fixture
//
// If notes.h changes, rerun this and paste the output into the test.

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#define QNOTE_TEXT_MAX 244
#define QNOTE_FLAG_TRUNCATED (1 << 0)
#define QNOTE_FLAG_SYNCED (1 << 1)

typedef struct __attribute__((packed)) {
  uint32_t id;
  uint32_t timestamp_utc;
  uint16_t text_len;
  uint8_t flags;
  uint8_t reserved;
  char text[QNOTE_TEXT_MAX];
} QNoteRecord;

_Static_assert(sizeof(QNoteRecord) == 256, "QNoteRecord must be exactly 256 bytes");

static void emit(const char *label, uint32_t id, uint32_t ts, const char *text, uint8_t flags) {
  QNoteRecord rec;
  memset(&rec, 0, sizeof(rec));
  rec.id = id;
  rec.timestamp_utc = ts;
  rec.flags = flags;
  size_t len = strlen(text);
  if (len > QNOTE_TEXT_MAX) {
    len = QNOTE_TEXT_MAX;
  }
  memcpy(rec.text, text, len);
  rec.text_len = (uint16_t)len;

  printf("// %s\n\"", label);
  const unsigned char *bytes = (const unsigned char *)&rec;
  for (size_t i = 0; i < sizeof(rec); i++) {
    printf("%02x", bytes[i]);
  }
  printf("\"\n\n");
}

int main(void) {
  printf("sizeof(QNoteRecord) = %zu\n\n", sizeof(QNoteRecord));
  emit("id=1 ts=1788363600 \"buy oat milk on the way home\"", 1, 1788363600u,
       "buy oat milk on the way home", 0);
  emit("id=2 ts=1788363777 two lines, truncated flag set", 2, 1788363777u,
       "call the dentist\nabout tuesday", QNOTE_FLAG_TRUNCATED);
  emit("id=3 ts=1788364000 multi-byte UTF-8", 3, 1788364000u,
       "caf\xc3\xa9 \xe2\x80\x94 pick up beans", QNOTE_FLAG_SYNCED);
  return 0;
}
