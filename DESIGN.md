# Design tokens (LockMusic lock-screen player)

Derived from the two user screenshots (iOS 27 Apple Music Lock Screen; iOS 26 third-party animated artwork mockup) and the Liquid Glass rules: glass only on the floating controls, content (artwork) stays unglazed, corner radii concentric with the screen, legibility measured. No Apple assets are used.

| token | value | note |
| --- | --- | --- |
| background | album artwork, centre crop, slow zoom 1.06 to 1.14 (16 s) | animated by us; Apple's motion artwork is not used |
| screen dim | 25% black (user slider 0 to 100%) | keeps clock readable |
| bottom scrim | gradient 0 to 55% black from 45% height | |
| panel radius | 32 dp | concentric with the phone's corner radius |
| panel fill | blurred copy of the artwork (40 dp) + 50% black + 8% white | measured below |
| panel border | 1 dp white at 22% | |
| text primary | white | |
| text secondary | white at 88% | raised from 65% after the contrast check |
| controls | star 26 dp, rewind/forward 36 dp, play/pause 48 dp, touch target 52 dp | |
| clock | 20 sp semibold + date 17 sp, under the status bar | |
| icons | Material Symbols (open licence) | not SF Symbols |
| font | system default | |

## Contrast check (worst case: pure white artwork under the panel)

| dim | panel black | panel white | background | white text | 88% white text |
| --- | --- | --- | --- | --- | --- |
| 25% | 30% | 10% | 146 | 3.11:1 (fail) | 2.2:1 (fail) |
| 25% | 45% | 8% | 117 | 4.60:1 | 2.9:1 |
| 25% | 50% | 8% | 108 | 5.22:1 (chosen) | about 4.6:1 (pass at 88%) |

The 88% figure is estimated from the same formula; recheck on the phone with a very bright cover.
