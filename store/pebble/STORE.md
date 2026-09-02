# Pebble App Store Listing — qnote

**Live at https://apps.rePebble.com/2d83367c2e4a408a807bd2e5** (app id `2d83367c2e4a408a807bd2e5`).

Edit the listing, changelog and visibility at
https://appstore-api.repebble.com/dashboard.

## App Name

qnote

## Tagline

Open it and start talking. The note is on your phone before you look up.

## Description

qnote is a voice notepad for your wrist, built around one idea: the app should
never stand between you and the thought you are trying to keep.

Open qnote and it is already listening. No menu, no button to find first. Speak,
confirm the transcription, and it is saved. Put qnote on a Quick Launch button
and a single long press takes you from idea to recorded note.

**Never loses a note.** Every note goes out over two paths at once. When your
phone is nearby it arrives instantly. When it isn't, the watch spools the note in
its own storage and delivers it as soon as you are back in range — even if you
closed the app hours earlier. Nothing depends on you remembering to sync.

**Honest about its limits.** The watch keeps the last 12 notes, because Pebble
gives an app about 4 KB of persistent storage and a note is 256 bytes of it. Your
phone is the archive. If all 12 are still waiting to reach the phone, qnote tells
you rather than quietly overwriting something you said.

**Features:**
- Starts dictating the moment you open it
- Scrolling list of recent notes with a per-note sync marker: filled once the
  phone has it, hollow while it is still only on the watch
- Full note text on a scrolling detail screen
- Delete from the watch, and the phone hears about it
- Companion Android app for search, categories, editing, sharing and export
- Store-and-forward delivery for notes captured out of Bluetooth range

**You need the companion app.** qnote stores its notes on your phone, so the
watchapp alone will capture nothing. The companion is a free download from
GitHub — it is not on the Play Store, so Android will ask you to allow
installing it from your browser or file manager. Full instructions are on the
project page.

qnote is free software under the GPLv3 — the source for both halves is on
GitHub, and you are free to read it, change it and pass it on.

Requires a Pebble with a microphone.

## Licence

Free software under the GNU General Public License v3 or later. Source:
https://github.com/neonfire42/qnote

## Category

Utilities > Productivity

## Keywords

notes, voice, dictation, memo, notepad, productivity, capture, voice notes,
speech, reminder

## Banner Text

Speak. It's saved.

## Companion App

qnote needs its Android companion to store the notes it captures. It is **not
on the Google Play Store** — download the APK directly:

https://github.com/neonfire42/qnote/releases/latest/download/qnote.apk

Installing it:

1. Open that link on your Android phone. It downloads `qnote.apk` directly.
2. Android will warn that the file came from an unknown source. Allow your
   browser (or the Files app) to install apps, then confirm.
3. Open qnote on the phone once and grant it access to your Pebble app when
   asked.

The watchapp declares the companion package in its `companionApp` block, so the
Pebble app pairs the two automatically once both are installed. That declaration
also carries the download link above, so the Pebble app can point users at it.

## Version History

### v1.0.0
- Initial release
- Dictation capture via the Pebble Dictation API
- 12-note on-watch cache with sync state per note
- AppMessage plus datalogging delivery to the phone
- Delete and acknowledge from the phone
- Opens straight into a new note on every launch

## Requirements

- A Pebble with a microphone (every model except Pebble Classic and Pebble Steel)
- Pebble mobile app 1.0.7.7 or newer, or microPebble 1.0.0-alpha35+
- The qnote Android companion app, sideloaded from GitHub releases
- Android only. There is no iOS companion, so qnote cannot store notes on an
  iPhone.

## Support

https://github.com/neonfire42/qnote/issues

## Screenshots

| File | Shows |
|---|---|
| `screenshot_emery.png` | The note list with sync markers |
| `screenshot_emery_dictating.png` | Dictation, reached with no button press |
| `screenshot_emery_category.png` | Filing a note straight after confirming it |
| `screenshot_emery_detail.png` | Full text of one note, with its category |
| `preview_emery.gif` | Rollover animation of the above |

All captured from the emery QEMU emulator at native 200x228.

## App Information

- **Author**: neonfire42
- **Category**: Utilities
- **Platforms**: Emery (Pebble Time 2)
- **Size**: ~12 KB
- **UUID**: 1ca95d44-145f-40d4-8a80-c34ccf7f0119

## Publishing

The listing was created with this, which is repeatable for later versions —
`pebble publish` matches on the PBW's UUID, so a rerun updates the existing app
rather than making a second one:

```sh
cd qnote-watch
pebble login
pebble publish --non-interactive \
  --name "qnote" \
  --description "<the description above>" \
  --category tools \
  --source "https://github.com/neonfire42/qnote" \
  --icon-small ../store/pebble/icon_80x80.png \
  --icon-large ../store/pebble/icon_144x144.png \
  --screenshots emery_1_listening.png emery_2_list.png emery_3_detail.png \
  --release-notes "..." \
  --no-gif-all-platforms
```

### What the CLI can and cannot change

`pebble publish` sets `--name`, `--description`, `--category`, `--source` and the
icons **only when creating a new app**. On an existing app it uploads a new
release and ignores those flags — the listing text stays whatever it was at
creation. To change the description, paste
[`description.txt`](description.txt) into the dashboard:
https://appstore-api.repebble.com/dashboard

**Screenshots cannot be changed on their own.** `pebble publish` always tries to
upload a release first, so running it against a version the store already has
fails with `Release upload failed (400): Version X already exists for this app`
and never reaches the `--screenshots` / `--replace-screenshots` step. The
listing is left untouched — it is a clean abort, not a half-applied change — but
it does mean new screenshots only go up either alongside a version bump or by
hand in the dashboard:
https://appstore-api.repebble.com/dashboard

`--is-published` behaves differently in the two cases too. Creating a new app
makes the app and its first release visible straight away. A later release stays
**unpublished** until you pass `--is-published` or flip it in the dashboard, so
the store keeps serving the previous version until you do.

Other things that bite:

- Screenshot filenames must **start with the platform name** (`emery_...`), not
  end with it. The files in this folder are named `screenshot_emery*.png` for
  readability, so copy them to `emery_*.png` before publishing.
- `--no-gif-all-platforms` matters. Without it the tool tries to drive the
  emulator to capture rollover GIFs, which is slow and needs a running QEMU.
- `--is-published` is documented as defaulting to false, but the listing came out
  publicly visible anyway. Check the dashboard after publishing if you expected a
  draft.
