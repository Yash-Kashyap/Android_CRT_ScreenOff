# CRT Off

The old-TV "electron beam" screen-off animation from Android 2.3 / 4.x, rebuilt frame-accurately for
Android 9 and newer. No root, no internet permission.

## The animation (350 ms, measured from 60 fps reference footage)

1. **Warm-up, 67 ms**: the picture dims slightly and a vertically mirrored "ghost" of the screen
   fades in.
2. **Collapse, 117 ms**: the picture squeezes toward the centre line at a near-even pace,
   glowing brighter and turning white.
3. **The line, 167 ms**: the bright full-width line thins, shortens slightly and fades out.

Turning the screen on is instant, as on the original phones.

## How to switch off with it

- **Floating power button**: drag it anywhere; it snaps to the edge
- **Quick Settings tile** "CRT off"
- **Home-screen shortcut**
- **Double-press Volume Down** (optional)
- **Double-press the camera key** (optional, phones with a camera button such as Sony Xperia; a single press still works as the shutter)
- **Samsung side key**: Settings → Advanced features → Side key → Double press → Open app → "CRT off now" (Samsung may show it as "CRT Off" afterwards; that's fine)

Android doesn't let apps animate the real power button. Apps are only told after the screen is
already dark, so these triggers play the animation and then switch the screen off themselves.

## Setup

1. Install the latest APK from [Releases](https://github.com/Yash-Kashyap/Android_CRT_ScreenOff/releases) and open **CRT Off**.
2. Turn on **CRT Off – screen-off service** in Accessibility settings. On Android 13+, if it's greyed
   out: App info → ⋮ → *Allow restricted settings* first.

The service draws the animation above everything and switches the screen off the same way as the
power button, so fingerprint and face unlock keep working. On Android 11+ it takes a snapshot of the
screen for the animation, which is never saved. It doesn't read screen content. On Android 9–10, and
on protected screens such as banking apps, black shutters close in around the live screen in the same
shape instead.

## Build

```
./gradlew assembleDebug
./gradlew testReleaseUnitTest   # renders the animation frame by frame + Android 9/11/14 checks
```
