# MATRIX CYBER LAUNCHER

A real Android home launcher (Kotlin, Jetpack Compose, Material 3, DataStore, MVVM). Min SDK 29, target/compile SDK 34.

## Build with GitHub Actions (no Android Studio needed)
1. Create a GitHub repo and upload ALL files from the zip (keep the `.github` folder).
2. Open the **Actions** tab -> **Build Debug APK** -> latest run. It runs unit tests, then builds.
3. When green, scroll to **Artifacts**, download `matrix-launcher-debug-apk`, unzip it, get `app-debug.apk`.
4. If it is red, open the failed step and copy the error text to get a fix.

The workflow calls Gradle 8.7 directly, so no `gradle-wrapper.jar` is needed.
Local build (Android Studio / any PC with JDK 17 + Android SDK): `gradle assembleDebug`.
Termux compilation is possible only if you manage to install an aarch64 Android SDK; GitHub Actions is the easier path.

## Install and set as default
1. Copy the APK to the phone, allow "install unknown apps" for your file manager, install.
2. Press Home and choose MATRIX CYBER LAUNCHER, or Settings -> Apps -> Default apps -> Home app.
3. To go back: the same screen, choose your old launcher.

## Features and honest status
Implemented (written, not yet run on a device by the author):
- HOME intent filter; real app discovery via PackageManager (`<queries>`, no QUERY_ALL_PACKAGES); real launch intents; removed apps handled.
- Matrix home: header with live clock/date, link indicator (reads real connectivity), CORE reactor (tap to run a visual scan that refreshes telemetry), local telemetry (battery, RAM, network, uptime), favorites row, dock (phone, messages, camera, browser, WhatsApp resolved from installed apps) and APPS button.
- Gestures: swipe up = app drawer, swipe down = search. Back closes overlays.
- App drawer: search, A-Z, favorites, recents, grid/list, empty/error states, long-press menu (open, pin, app info, uninstall via system dialog), refresh on package changes.
- Terminal: HELP APPS SEARCH OPEN TIME DATE BATTERY STATUS RADAR SETTINGS CLEAR, history (up/down), autocomplete (Tab / chips). No shell access.
- Radar (clearly marked simulated) and a separate diagnostics screen (real data only).
- Keyboard: Esc closes overlays, Ctrl+K opens search, Enter opens first result / submits, arrow keys move focus.
- Voice: opt-in MIC button, permission explained first, one-shot recognition, ambiguity message.
- Settings (DataStore): intensity, rain, scanlines, radar, sound, haptics, 12/24h, grid, icon size, terminal size, brightness, high contrast, reduce motion, boot sequence, device label, restore defaults.
- Animations pause when the launcher is not visible; reduce-motion (app setting or system animation scale 0) shows static frames.

Added in v1.1: real Android widgets (WDG button, AppWidgetHost; Android asks you to approve each bind), app shortcuts in the long-press menu (shown only while this is the default launcher), reorderable favorites (< > in Settings), button roles for screen readers.

Not implemented: notification-listener feature (optional in the spec), accepting pinned shortcuts requested by other apps, free drag-and-drop icon placement, profiling results.
The telemetry blocks on the home screen are launcher panels; the WDG screen holds the real widgets.

## Tests
- Unit (run in CI): `gradle testDebugUnitTest` - command parser, app matcher, voice interpreter.
- Instrumented smoke tests (need a phone/emulator): `gradle connectedDebugAndroidTest` - HOME registration and activity start.
- Manual checklist: drawer shows apps, search opens an app, favorites survive restart, deny mic and verify the rest still works, toggle reduce motion, uninstall an app while launcher is open.

## Signed release build
1. Make a keystore once: `keytool -genkeypair -v -keystore release.jks -alias matrix -keyalg RSA -keysize 2048 -validity 10000`
2. Base64 it: `base64 -w0 release.jks` (Termux: same command).
3. GitHub repo -> Settings -> Secrets and variables -> Actions -> add: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
4. Actions -> **Build Signed Release APK** -> Run workflow. Download the artifact.
Never commit the keystore or passwords (`.gitignore` already blocks *.jks).

## Privacy
No analytics, ads, accounts or tracking. Only permissions: ACCESS_NETWORK_STATE (read-only) and RECORD_AUDIO (asked only when you tap MIC). Speech recognition is done by your phone's speech service, which may use the internet.
