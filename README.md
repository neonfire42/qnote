# qnote

Speak a note into a Pebble watch; manage the list on Android.

Two halves that talk over [PebbleKit Android 2](https://github.com/pebble-dev/PebbleKitAndroid2):

- **`qnote-watch/`** — a Pebble watchapp in C, targeting emery (Pebble Time 2).
  Press Select, speak, and the transcription is stored and sent to the phone.
- **`qnote-android/`** — a Kotlin/Compose companion that receives the notes,
  keeps them, and lets you search, edit, share and export them.

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

Needs JDK 17 and an Android SDK with platform 36. `local.properties` points at
the SDK; the Gradle wrapper handles the rest.

```sh
cd qnote-android
./gradlew testDebugUnitTest       # the C <-> Kotlin record contract
./gradlew assembleDebug           # -> app/build/outputs/apk/debug/
```

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

Assign qnote to a Quick Launch button in the Pebble app. Holding that button
opens qnote and starts dictating immediately — the fastest path from thought to
stored note. Every other launch route (the launcher, the phone opening the app)
shows the list instead.
