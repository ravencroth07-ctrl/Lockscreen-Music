# Test plan (run on the Realme 14 Pro)

Bugs use S1 (crash or blocks the main flow) to S4 (cosmetic).

| # | check | expected |
| --- | --- | --- |
| 1 | Grant notification access, play a song in any music app | wallpaper shows the artwork within 2 s of the track starting |
| 2 | Skip to a track with no embedded artwork | iTunes artwork appears; if none found, previous art or gradient, no black screen |
| 3 | Track titles ending in "- Single" or with "(feat. X)" | artwork still found |
| 4 | Lock the phone while music plays, wake the screen | player opens over the lock screen in under 1 s |
| 5 | Same, with full-screen notifications switched off | no player, normal media controls, no crash |
| 6 | Play/pause, next, previous, scrub, volume | each works; progress moves smoothly |
| 7 | Swipe left on the panel, tap Clear | player closes, music keeps playing, no widget until next track or resume |
| 8 | Pause then resume after Clear | player returns on the next screen wake |
| 9 | Swipe up | keyguard prompts for unlock; player closes |
| 10 | Flashlight and camera buttons | torch toggles; secure camera opens |
| 11 | Stop music | wallpaper switches to the idle photo, or the last artwork if none set (S2 if a black screen appears) |
| 12 | Switch artwork to idle photo and back several times | no "Invalid argument -22" in logcat; wallpaper keeps drawing (the pixel-format nudge) |
| 13 | Leave for 30 minutes, battery saver on and off | listener still alive; if not, recheck step 5 and 6 |
| 14 | Very bright album cover | panel text readable (contrast target 4.5:1) |
| 15 | Artwork animation | smooth, no visible battery drain over 10 minutes with the screen on |

Logcat filter: `adb logcat | grep -E "lockmusic|SurfaceHolder|WallpaperService"`.

When it runs, tell me what failed and I'll fix it and score parity with the original screenshots.
