# Google Play listing — qnote

> **Not published.** There is no Play developer account for qnote yet, so the
> companion app is distributed as an APK on GitHub releases instead. This file
> is ready to paste into the Play Console if that changes; nothing else in the
> project depends on it.
>
> While qnote is sideloaded, the download link users are pointed at is
> `https://github.com/neonfire42/qnote/releases/latest`, which is also the URL
> baked into the watchapp's `companionApp` block.

Copy for the Play Console. Character limits are Play's, and each field below is
within them.

## App name (30 max)

```
qnote — Pebble voice notes
```

## Short description (80 max)

```
Speak a note into your Pebble. It's on your phone before you lower your wrist.
```

## Full description (4000 max)

```
qnote turns your Pebble into the fastest notepad you own.

Open qnote on the watch and it starts listening straight away — no menu, no
tapping through screens. Speak, confirm, done. The note is on your phone before
you have lowered your wrist.

FAST BY DESIGN

Opening the watchapp starts a new note immediately. Assign qnote to a Quick
Launch button and one long press is all it takes. Opening this phone app starts
dictation on your watch too, so whichever device is in your hand, you are one
action from talking.

NEVER LOSES A NOTE

Notes travel over two channels at once. When your phone is nearby they arrive
instantly. When it isn't, the watch keeps them in its own storage and delivers
them the moment you are back in range — even if you closed the app hours ago.
Nothing waits on you to remember to sync.

ORGANISE ON THE PHONE

Sort notes into categories you invent as you go: type a name and it exists.
Filter by category, search the full text, edit anything the transcription got
wrong, and export the lot as Markdown grouped by category. Share a single note
straight into any other app.

BUILT FOR PEBBLE

• Works with the current Pebble app (1.0.7.7+) and microPebble
• Uses PebbleKit Android 2, so it keeps working in the background
• Requires a Pebble with a microphone — every model except the original Pebble
  and Pebble Steel
• Dark, always, in the same blue the watchapp uses

qnote asks once which Pebble app may exchange notes with it, and talks to that
one only. Your notes stay on your phone and your watch. There is no account, no
server, and no analytics.

FREE SOFTWARE

qnote is licensed under the GNU General Public License v3. The source for both
the watchapp and this app is at https://github.com/neonfire42/qnote — read it,
change it, pass it on.

The watchapp is a separate install from the Pebble app store.
```

## Category

Productivity

## Tags

notes, voice, dictation, pebble, smartwatch, productivity, voice notes

## Contact

- Website: https://github.com/neonfire42/qnote
- Email: neoakb@gmail.com

## Data safety declaration

Answer these as follows — they reflect what the code actually does:

| Question | Answer |
|---|---|
| Does your app collect or share any user data? | No |
| Is all user data encrypted in transit? | N/A — no data leaves the device |
| Do you provide a way to delete data? | Yes — delete any note in the app |

qnote has no network permission, no analytics SDK, and no backend. Notes move
only between the watch and the phone, over the local Bluetooth link the Pebble
app owns.

## Permissions to justify

None requested beyond the PebbleKit service binding, which needs no runtime
permission.

## Assets in this folder

| File | Play requirement |
|---|---|
| `icon-512.png` | Hi-res icon, 512x512, 32-bit PNG, no alpha |
| `feature-graphic.png` | Feature graphic, 1024x500 |
| `01-note-list.png` | Phone screenshot |
| `02-category-filter.png` | Phone screenshot |
| `03-note-detail.png` | Phone screenshot |
| `04-category-picker.png` | Phone screenshot |

Play needs at least 2 phone screenshots; there are 4. All are real renders of
the shipping composables, produced by `StoreScreenshotTest` — not mockups.

Regenerate everything with:

```sh
cd qnote-android && ./gradlew testDebugUnitTest   # re-renders the screenshots
python3 tools/make_store_assets.py                # icons, banner, collection
```
