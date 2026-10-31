# LockMusic

An iOS-27-style Now Playing lock screen for Android (tested on Realme 14 Pro, Android 16).

- Live wallpaper: album artwork full bleed, slow zoom and drift, dim slider, idle photo when nothing plays.
- Lock-screen player: frosted panel with title, artist, scrubber, previous / play-pause / next, favourite star, volume, flashlight and camera buttons, compact clock. Swipe left on the panel for Clear (music keeps playing; the player returns on a new track or when playback resumes). Swipe up to unlock.
- Artwork: from the music app, otherwise the public iTunes Search API with cleaned metadata and three retries.

Status: written but not compiled or run (the sandbox could not reach Android's build servers). Expect to fix a compile error or two on first build.

## Build the APK

Option A, Android Studio: open this folder, let Gradle sync, Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.

Option B, GitHub: push this folder to a new repo. The workflow in `.github/workflows/build.yml` builds the debug APK. Download it from the run's Artifacts.

Install the APK on the phone (allow installs from your browser or file manager), then open LockMusic and follow the six steps.

## How the lock-screen player appears

A notification-listener service watches playback. When the screen turns on while music plays and the phone is locked, it posts a full-screen-intent notification that opens the player over the lock screen. This needs step 1 (notification access), step 2 (notifications) and step 3 (full-screen notifications). Without step 3 it does nothing and you keep the normal Android media controls.

## Not included, on purpose

- Spotify Canvas and Apple motion-artwork videos (licensed content). The artwork is animated by us instead.
- Apple's UI kit images, SF Symbols and fonts.
- Synced lyrics (licensed).
- The output / headphones picker.

See DESIGN.md for tokens and the contrast check, TESTING.md for the checks to run on the phone.
