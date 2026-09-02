# qnote

Speak a note into a Pebble watch; manage the list on Android.

Two halves that talk over [PebbleKit Android 2](https://github.com/pebble-dev/PebbleKitAndroid2):

- **`qnote-watch/`** — a Pebble watchapp in C, targeting emery (Pebble Time 2).
  Opening it starts a new note: speak, confirm, done.
- **`qnote-android/`** — a Kotlin/Compose companion that receives the notes,
  keeps them, and lets you search, categorise, edit, share and export them.

Both entry points go straight to capture. Opening the watchapp starts dictation
immediately, and opening the phone app starts dictation *on the watch* — so
there is no menu between a thought and a recorded note. Back gets you to the
list on the watch; dismissing dictation is silent, so browsing costs one press.
Turn the phone-side behaviour off under **Dictate when app opens** in the
overflow menu if you mostly open the app to read.

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

### The wire format

`QNoteRecord`, defined in `qnote-watch/src/c/notes.h` and decoded by
`NoteRecordCodec.kt`. Datalogging needs a fixed item size, so the layout is a
contract:

| offset | size | field |
|---|---|---|
| 0 | 4 | `id` — uint32, monotonic per watch |
| 4 | 4 | `timestamp_utc` — uint32 seconds |
| 8 | 2 | `text_len` — uint16, **bytes** not characters |
| 10 | 1 | `flags` — bit 0 truncated, bit 1 synced |
| 11 | 1 | `reserved` |
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

## Categories

Categories live on the phone only. The watch record has no room for one — the
256-byte layout is fixed by datalogging — and sorting is a job for a screen with
a keyboard.

A category is just a string on a note, so there is no management screen: typing
a new name in the picker creates it, and deleting the last note that carries one
retires it. Filter with the chip row above the list, set a category from the
chip on the detail screen, or long-press notes to select several and categorise
them in one go. Markdown export groups by category.

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
