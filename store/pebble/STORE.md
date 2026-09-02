# Pebble App Store Listing — qnote

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

Requires a Pebble with a microphone and the companion Android app.

## Category

Utilities > Productivity

## Keywords

notes, voice, dictation, memo, notepad, productivity, capture, voice notes,
speech, reminder

## Banner Text

Speak. It's saved.

## Companion App

qnote needs its Android companion to store the notes it captures:
https://github.com/neonfire42/qnote

The watchapp declares the companion in its `companionApp` block, so the Pebble
app links the two automatically once both are installed.

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
- The qnote Android companion app

## Support

https://github.com/neonfire42/qnote/issues

## Screenshots

| File | Shows |
|---|---|
| `screenshot_emery.png` | The note list with sync markers |
| `screenshot_emery_dictating.png` | Dictation, reached with no button press |
| `screenshot_emery_detail.png` | Full text of one note |
| `preview_emery.gif` | Rollover animation of the above |

All captured from the emery QEMU emulator at native 200x228.

## App Information

- **Author**: neonfire42
- **Category**: Utilities
- **Platforms**: Emery (Pebble Time 2)
- **Size**: ~12 KB
- **UUID**: 1ca95d44-145f-40d4-8a80-c34ccf7f0119

## Publishing

```sh
cd qnote-watch
pebble login
pebble publish
```

`pebble publish` prompts for the icons and screenshots; point it at the files in
this folder.
