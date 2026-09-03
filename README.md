# qnote

Speak a note into a Pebble watch; manage the list on Android.

Two halves that talk over [PebbleKit Android 2](https://github.com/pebble-dev/PebbleKitAndroid2):

- **`qnote-watch/`** — a Pebble watchapp in C, targeting emery (Pebble Time 2).
  Opening it starts a new note: speak, confirm, done.
- **`qnote-android/`** — a Kotlin/Compose companion that receives the notes,
  keeps them, and lets you search, categorise, edit, share and export them.
  Swipe a note left to delete it (with a few seconds to take it back) or right
  to file it. It is also a share target: "Share" a link or a selection from
  any app and it lands here as a note too.

Opening the watchapp goes straight to capture — dictation starts immediately, so
there is no menu between a thought and a recorded note. Back gets you to the
list; dismissing dictation is silent, so browsing costs one press.

The phone app opens on the note list, with **Speak on watch** a button away. It
can start dictation on the watch the moment it opens, which makes either device
one action from talking, but that is off until you ask for it under **Dictate
when app opens** in the overflow menu — a watch that starts listening because
you tapped a phone icon is a surprise the first time it happens.

## Installing

qnote is two installs, and you need both — the watchapp captures notes, the
phone app is where they live.

**1. The watchapp** — from the [Pebble app store](https://apps.rePebble.com/2d83367c2e4a408a807bd2e5), or sideload
[`qnote.pbw`](https://github.com/neonfire42/qnote/releases/latest/download/qnote.pbw) through
the Pebble mobile app.

**2. The Android companion** — download
[`qnote.apk`](https://github.com/neonfire42/qnote/releases/latest/download/qnote.apk) on your phone.

It is **not on the Google Play Store**, so Android will warn that it came from an
unknown source. Allow your browser (or the Files app) to install apps when
prompted, then confirm. Open qnote once afterwards and grant it access to your
Pebble app when it asks — it only talks to the one you pick.

**You need:**

- A Pebble with a microphone. That is every model except the original Pebble and
  Pebble Steel.
- The Pebble mobile app 1.0.7.7 or newer, or microPebble 1.0.0-alpha35+.
- **Android.** There is no iOS companion, so qnote cannot store notes from an
  iPhone.

## How it works

Pebble has no raw microphone API. The only route to text is the system
**Dictation API**: `dictation_session_start()` hands control to the phone's
Pebble app, which runs the speech service and returns a string. qnote calls that
API — it does not process audio itself. This means dictation needs a connected
phone, and works on every mic-equipped Pebble (all platforms except aplite).

Notes reach the phone over **two transports at once**:

| | AppMessage | Datalogging |
|---|---|---|
| When | While the watchapp is open and the phone is connected | Whenever the watch can reach the phone, even with the app closed |
| Role | Instant delivery and phone→watch traffic | Durable spool for notes captured out of range |

Both carry the same 256-byte record, and the companion deduplicates on
`"<watchId>:<recordId>"`. The Pebble app is explicitly allowed to replay a
datalog batch, so that key is load-bearing rather than defensive.

The watch caches only **12 notes**. Pebble persist storage is 256 bytes per value
and roughly 4 KB in total, so the phone is the archive and the watch is a capture
buffer. If all 12 slots are still unacknowledged, qnote refuses a new capture
rather than overwriting a note you spoke.

Since the phone is the archive, losing the phone means losing the archive.
qnote does not use Android's Auto Backup to soften that — `allowBackup` is off,
so the note database never leaves the device on its own. That is a deliberate
trade: Auto Backup would otherwise upload every note to the user's Google Drive
account with no way to exclude it, which sits badly next to a Pebble pairing
the app already treats as sensitive (auto-select is off there for the same
reason — see **Pairing it with a real watch** below).

**Back up notes** / **Restore notes**, in the overflow menu, are the deliberate
replacement. Backup writes a JSON file wherever the user picks — losslessly,
unlike Markdown export, which is for reading and drops ids, sync flags, and
turns timestamps into display text. Restore reads that file back and **merges**
it in through the same `NoteStore.upsert()` the watch's own datalog replay
uses: a note already on the phone, even one edited since the backup was taken,
is left alone rather than overwritten, so restoring is always safe to run more
than once. The file also carries the category slot table — worth doing because
that table is append-only (see **Categories** below), and a note still
unsynced in the watch's cache is tagged with a slot number that only means the
right thing if the table restoring onto a new phone still agrees with it.

### The wire format

`QNoteRecord`, defined in `qnote-watch/src/c/notes.h` and decoded by
`NoteRecordCodec.kt`. Datalogging needs a fixed item size, so the layout is a
contract:

| offset | size | field |
|---|---|---|
| 0 | 4 | `id` — uint32, monotonic per watch |
| 4 | 4 | `timestamp_utc` — uint32 seconds |
| 8 | 2 | `text_len` — uint16, **bytes** not characters |
| 10 | 1 | `flags` — bit 0 truncated, bit 1 synced, bit 2 spooled |
| 11 | 1 | `category_slot` — 0 uncategorised, else a phone-assigned slot |
| 12 | 244 | `text` — UTF-8, zero-padded |

Little-endian. A watch build fails on `_Static_assert` if it is not exactly 256
bytes. Truncation backs off to a UTF-8 character boundary so a multi-byte
character is never cut in half.

`tools/gen_fixture.c` compiles that same struct and prints the bytes; the output
is pasted into `NoteRecordCodecTest.kt`. Regenerate it whenever `notes.h`
changes:

```sh
cc -o /tmp/gen_fixture tools/gen_fixture.c && /tmp/gen_fixture
```

## Building

### Watch

```sh
cd qnote-watch
pebble build                      # -> build/qnote-watch.pbw
pebble install --emulator emery
```

### Android

Needs **JDK 21** and an Android SDK with **platform 37**. `local.properties` points
at the SDK; the Gradle wrapper handles the rest.

```sh
cd qnote-android
./gradlew testDebugUnitTest       # the C <-> Kotlin record contract
./gradlew assembleDebug           # -> app/build/outputs/apk/debug/
```

Neither version is a preference. `io.rebble.pebblekit2:common:1.3.0` requires
consumers to compile against API 37 or later, and it ships Java 21 bytecode —
AGP desugars that for the APK, but JVM unit tests load the classes directly and
fail on JDK 17 with `UnsupportedClassVersionError`. That in turn rules out AGP 8.x,
which cannot address the minor-versioned `android-37.0` platform, so the project
is on **AGP 9.3.2 / Gradle 9.7.1**. AGP 9 supplies Kotlin itself, which is why
there is no `org.jetbrains.kotlin.android` plugin here — only the Compose one.

Notes are stored in plain SQLite rather than Room, to keep an annotation
processor (and its Kotlin/KSP version pinning) out of the build.

The debug build's application id is `dev.neonfire.qnote.debug`, which is already
listed in the watchapp's `companionApp` declaration, so a debug APK pairs with a
release PBW without edits.

## Testing on the emulator

Everything except real speech can be driven headlessly:

```sh
pebble transcribe "buy oat milk"                    # inject a transcription
pebble transcribe --error connectivity              # or an error path
pebble emu-button click select --emulator emery
pebble screenshot --no-open --emulator emery s.png

# Pretend to be the phone. send-app-message wants numeric keys, not names:
pebble send-app-message --emulator emery --app-uuid <uuid> --uint 10003=1  # ACK_ID
pebble send-app-message --emulator emery --app-uuid <uuid> --uint 10004=1  # DELETE_ID
pebble send-app-message --emulator emery --app-uuid <uuid> --uint 10006=1  # START_CAPTURE

# The category list, as the companion pushes it. Tab-separated slot and name,
# one per line; 10009 is the "ask for a category" switch.
pebble send-app-message --emulator emery --app-uuid <uuid> \
  --string 10008=$'1\tErrands\n2\tIdeas\n' --uint 10009=1

pebble data-logging --emulator emery list           # the spool session
```

Two things to know:

- `pebble transcribe` must be running *before* you press Select — it acts as the
  voice service the dictation session talks to. It holds the connection open and
  often never exits, so `pkill -f "pebble transcribe"` between runs.
- Watchapps idle out after roughly half a minute. Long scripted sequences need
  `pebble install` to relaunch between steps, not one long session.

## Pairing it with a real watch

The `companionApp` block in `qnote-watch/package.json` is what makes the Pebble
app bind to `QNoteListenerService`. It lists both `dev.neonfire.qnote` and
`dev.neonfire.qnote.debug`; change those if you change the application id, and
rebuild the PBW — the value is read from the PBW's `appinfo.json`, not the store.

Requires the Core Devices Pebble app **1.0.7.7 or newer** (the version that
added PebbleKit 2), or microPebble `1.0.0-alpha35+`.

On first launch the app asks which Pebble app may exchange notes with it.
Auto-select is deliberately off: notes are personal text, and without pinning,
any app claiming to be a Pebble app could receive them.

### Quick Launch

Assign qnote to a Quick Launch button in the Pebble app. Holding that button is
then the fastest path there is: one press, and you are speaking.

## Sharing into qnote

qnote is registered for `text/plain` shares, so "Share" on a link, a
selection, a snippet from any app offers it as a destination. A note made this
way has no watch behind it, so it gets its own watch id (`"phone"`, internal —
never a real `WatchIdentifier`) and the share's arrival time stands in for
`recordId`; the `id = "<watchId>:<recordId>"` scheme documented in
[`Note`](qnote-android/app/src/main/kotlin/dev/neonfire/qnote/data/Note.kt)
does not otherwise change.

It lands saved, not open for editing — sharing is already the deliberate act,
the same way choosing to speak into the watch is. Edit or categorise it
afterwards from the list, same as any other note. A page shared with both a
title and a link (`EXTRA_SUBJECT` and `EXTRA_TEXT`) keeps both, folded into one
note, so a shared URL is not saved bare with no indication of what it was.

## Categories

A category is just a string on a note, so there is no management screen: typing
a new name in the picker creates it, and deleting the last note that carries one
retires it. Filter with the chip row above the list, set one from the chip on
the detail screen, swipe a note right, or long-press to select several and
categorise them in one go. Markdown export groups by category.

Categories are created on the phone, where the keyboard is, but they can be
**applied on the watch**. Confirm a dictation and qnote offers the categories it
knows about; Back skips in one press and the note is saved either way. Turn the
prompt off under **Ask for a category on the watch** in the overflow menu.

### How a category survives the trip

The record has exactly one spare byte, so the watch sends a *number*, not a
name. A number is only meaningful against a table both ends agree on, and the
watch's copy can be arbitrarily old: a note captured out of range reaches the
phone whenever the spool next drains, which may be days and several category
changes later.

So the phone's slot table is **append-only**. A name is bound to a slot the
first time it is seen and keeps it forever; retiring a category frees nothing.
An old slot number therefore always resolves to the name it was tagged with,
which a positional index into the live list could not promise.

The phone pushes the table to the watch whenever the list changes and again
every time the watchapp opens — the latter arrives while you are still speaking.
It is capped at 192 bytes and filled most-recently-used first, because a watch
persist value is 256 bytes and the note ring already spends 3 KB of the app's
4 KB budget.

The note is written to the watch's persist store *before* the picker appears, so
backing out costs the tag and nothing else. Waiting for an answer does delay the
submission, though, and an app that idles out with the picker open would
otherwise leave a note the durable spool never saw — so `flags` bit 2 records
which notes reached datalogging, and `sync_init()` spools anything that did not.
A note whose picker was never answered keeps exactly the durability of any
other; it just arrives uncategorised.

## Notes on the design

**The Android app is dark, always.** Its accent is `#00AAFF`, which is
`GColorVividCerulean` — the exact colour the watchapp uses for its menu
highlight and timestamps.

**Why `START_CAPTURE` exists.** The watch cannot tell from `launch_reason()`
whether a phone-initiated launch meant "start dictating" or "just open the app",
and `pebble install` looks identical to both. The companion therefore launches
the app and then sends an explicit `START_CAPTURE` message. Since the watchapp
now dictates on every launch this is mostly belt-and-braces, but it still covers
the case where the app is already open on the watch. `capture.c` drops a second
request while a session is running, so the two paths cannot collide.

**qnote closes itself after 90 seconds.** A watchapp holds the screen until
something takes it away, and qnote is opened dozens of times a day for a few
seconds each — so one still sitting on the note list is nearly always one the
wearer walked away from. `idle.c` runs a timer that the list, the detail screen
and the category picker reset on every button press, and that dictation
suspends outright, because the system voice UI owns the screen and may sit there
as long as it likes. On expiry the last window is popped, which ends the app;
the firmware falls back to the watchface, and there is no API to ask for it
directly.

## Releasing

The Android app is sideloaded, so the release APK has to be signed with a key
that stays stable forever: Android refuses an update signed with a different
key, and a user would have to uninstall first — losing every note on the phone.

The key lives **outside this repo**, at `~/.qnote-keys/`:

```
~/.qnote-keys/qnote-release.jks        the key itself
~/.qnote-keys/signing.properties       path, alias and passwords
```

`app/build.gradle.kts` reads that file if it exists and ignores it if it does
not, so cloning this repo and running `assembleDebug` works with no setup —
only `assembleRelease` needs the key.

**Back up `~/.qnote-keys/` somewhere you will still have in five years.** It is
not in git, and it cannot be regenerated.

Cutting a release:

```sh
cd qnote-watch  && pebble build                     # -> build/qnote-watch.pbw
cd qnote-android && ./gradlew assembleRelease       # -> app/build/outputs/apk/release/
gh release create v1.0.0 qnote.pbw qnote.apk --title "..." --notes "..."
```

The watchapp's `companionApp.android.url` points at
`releases/latest`, so the Pebble app sends anyone missing the companion straight
to the newest APK. Keep that true — don't delete old releases without a newer
one in place.

## Store assets

Everything both stores need is in `store/`, regenerated by one command:

```sh
cd qnote-android && ./gradlew testDebugUnitTest   # re-renders the phone screenshots
python3 tools/make_store_assets.py                # icons, banner, collection
```

| | Play | Pebble |
|---|---|---|
| Icon | `store/play/icon-512.png` (512x512, no alpha) | `icon_144x144.png`, `icon_80x80.png` |
| Banner | `feature-graphic.png` (1024x500) | — |
| Screenshots | four at 1080x1920 | three at 200x228 |
| Preview | — | `preview_emery.gif` |
| Copy | `listing.md` | `STORE.md` |

Every icon is rendered from `tools/icon.svg`, so there is one piece of artwork
rather than five that drift apart. It reuses the glyph paths from the Android
adaptive icon, scaled up because a store icon is not mask-cropped.

**No screenshot here is a mockup.** The watch ones are QEMU captures. The phone
ones are rendered by `StoreScreenshotTest`, which drives the shipping
composables through the real `NotesViewModel` and a real SQLite store using
Roborazzi and Robolectric — layoutlib on the JVM, no emulator. That matters
because this machine has no `/dev/kvm`, so an Android emulator would fall back
to software translation, and a hand-drawn approximation would misrepresent the
app in a listing.

The qualifiers `w360dp-h640dp-xxhdpi` come out at exactly 1080x1920, Play's
standard phone screenshot size. One caveat: an `AlertDialog` opens a second
window that never reports idle under Robolectric, so the category picker cannot
be captured this way — the multi-select shot covers the same feature from the
main window instead.

## Licence

qnote is free software under the **GNU General Public License, version 3 or
later**. The full text is in [`LICENSE`](LICENSE); every source file carries an
SPDX header.

In short: you may use, study, modify and redistribute qnote, and if you
distribute a modified version you must give its recipients the same freedoms,
including the source. That is the point — a notepad you keep your own thoughts
in should be something you can read and change.

The GPL requires the licence to travel with a distributed binary, and qnote's
APK is handed to people directly rather than through a store that would surface
it. So the licence ships inside the app: **⋮ → About & licence**, readable
offline. `app/src/main/res/raw/gpl3.txt` is a copy of `LICENSE` for that purpose
— keep them identical.

### Dependencies

| | Licence | Compatible? |
|---|---|---|
| PebbleKit Android 2 | Apache 2.0 | Yes — Apache 2.0 code may be combined into a GPLv3 work |
| AndroidX / Compose | Apache 2.0 | Yes |
| Pebble SDK headers | Apache 2.0 | Yes |
| Roborazzi, Robolectric | Apache 2.0 | Yes (test only) |
| `gradle-wrapper.jar` (committed) | Apache 2.0 | Yes — keeps its own licence |

Apache 2.0 is one-way compatible with GPLv3: it can flow into this project, but
not the reverse. That is fine here — nothing in qnote is meant to be re-used
under weaker terms.
