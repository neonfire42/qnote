// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 neonfire42
#include "util.h"

#include <string.h>

void util_format_age(time_t when, char *buf, size_t buf_size) {
  const time_t now = time(NULL);
  // Clock skew or a note logged a second into the future should read as "now",
  // not as a huge age from an unsigned wrap.
  const int32_t delta = (int32_t)(now - when);

  if (delta < 60) {
    snprintf(buf, buf_size, "now");
  } else if (delta < 3600) {
    snprintf(buf, buf_size, "%dm", (int)(delta / 60));
  } else if (delta < 86400) {
    snprintf(buf, buf_size, "%dh", (int)(delta / 3600));
  } else {
    snprintf(buf, buf_size, "%dd", (int)(delta / 86400));
  }
}

void util_format_stamp(time_t when, char *buf, size_t buf_size) {
  struct tm *tick = localtime(&when);
  strftime(buf, buf_size, clock_is_24h_style() ? "%a %H:%M" : "%a %l:%M %p", tick);
}

void util_first_line(const char *text, size_t text_len, char *buf, size_t buf_size) {
  if (buf_size == 0) {
    return;
  }
  size_t n = text_len;
  for (size_t i = 0; i < text_len; i++) {
    if (text[i] == '\n' || text[i] == '\r' || text[i] == '\0') {
      n = i;
      break;
    }
  }
  if (n > buf_size - 1) {
    n = buf_size - 1;
  }
  memcpy(buf, text, n);
  buf[n] = '\0';
}
