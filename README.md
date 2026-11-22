# Slate

Website: https://thrishankkuntimaddi.github.io/slatelauncher/ · [Privacy](https://thrishankkuntimaddi.github.io/slatelauncher/privacy.html) · [Terms](https://thrishankkuntimaddi.github.io/slatelauncher/terms.html)

An iPadOS-style home screen for Android tablets, built for the Xiaomi Pad 5 (HyperOS).

- Home pages with folders, a dock (favourites, then recently used apps that are still open), and an App Library
- Widgets: clock, calendar, up next, to-do, notes, sticky notes, focus timer, habits, weather, world clock, countdown, screen time, photo frame, battery, and widgets from your apps — in a stack that snaps and moves on by itself
- Smart organize: category folders for groups of more than three apps
- Full-screen gestures on HyperOS with a third-party launcher (Accessibility → Slate gestures):
  swipe up for Home, swipe up and pause for Recents, swipe in from a side for Back

## Build

```sh
export JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release signing reads `keystore.properties` from outside the repo
(`$NOLAUNCHER_SIGNING`, default `~/.android/nolauncher-signing/keystore.properties`).
The application id stays `app.nolauncher.pad` so updates install over existing installs.

## HyperOS notes

Setting a third-party launcher as default switches HyperOS to 3-button navigation. Restore the
gesture bar with `adb shell settings put global force_fsg_nav_bar 1`, then turn on Slate gestures.
Allow Autostart for Slate so HyperOS doesn't stop the gesture service.

The gesture service only performs Home / Back / Recents and reads the app names on the system
Recents screen (so the dock can drop apps you've cleared). It reads nothing else.

## Google Play

See [PLAY_STORE.md](PLAY_STORE.md). Build the bundle with `./gradlew :app:bundleRelease`.
