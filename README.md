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

## Network toolkit (v1.5) - NET button / terminal
Written WITHOUT a compiler or device (the author's sandbox had no Gradle, Android SDK or internet). Nothing below has been built, unit-tested or run on a phone yet: push to GitHub and read the **Build Debug APK** run. Expect to fix a few compile errors on the first run.

Open it with the **NET** button on the home screen. Three tabs, all real (no simulated results):
- **DISCOVER** - shows this phone's connection (transport, IPv4/prefix, gateway, DNS servers from Android). You pick a private LAN range in CIDR (/22 to /30, RFC1918 + link-local only; the phone's own subnet is pre-filled). Each address gets one echo probe (`InetAddress.isReachable`: ICMP where Android allows it, else TCP echo), then, only if silent, quick TCP connects to 443/80/53. At most 24 probes run at once. Shows IP, reverse-DNS name (else "Unknown"), how it was detected, response time and discovery time; MAC is always "Unknown" (Android 10+ blocks it). Progress, cancel, partial results kept.
- **DIAGNOSE** - connectivity/transport, Android's own internet-validation flag, DNS lookup time, TCP connect latency (3 samples, lookup excluded), echo reachability, and a basic internet check (TCP connect to 1.1.1.1:443). A value is shown only when that test succeeded; failures give a specific message (DNS failure, timeout, refused, unreachable). Session history with timestamps. Each test can run alone, or run the full sequence.
- **PORTS** - TCP connect check of up to 64 ports on a host name/IPv4 you enter (editable list plus common-port chips). States: REACHABLE / REFUSED / TIMED OUT / UNREACHABLE / ERROR. 6 parallel connects, 2.5 s timeout each, cancel any time, summary + elapsed + finish time. Nothing is sent after connecting, so no service/version/vulnerability is ever claimed.

The radar sweep angle equals real progress (done/total); dots appear only for results that actually came back. There is no looping decorative animation in this screen, so it costs no battery when idle; reduce-motion removes the easing too.

Safety rails: a confirmation dialog ("I am authorized") before every active test; one operation at a time (no overlapping/duplicate scans); scans stop when the launcher leaves the foreground; discovery refuses public ranges; no stealth/evasion/exploit/credential features; nothing is uploaded. **SAVE REPORT** writes a .txt under the app's own external files folder (`Android/data/com.matrix.launcher/files/reports/`); **SHARE** opens Android's share sheet only when you tap it. History is in memory only.

Terminal commands: `NETWORK`, `NETWORK STATUS`, `NETWORK DISCOVER [CIDR]`, `NETWORK DIAGNOSTICS`, `DNS <host>`, `PORTCHECK <host> <ports>`, `SCAN STATUS`, `SCAN CANCEL`, `REPORT`. Discover / diagnostics / portcheck print an authorization notice and run only if you type `YES`. `DNS` is a plain name lookup and starts directly. Plain `SCAN` is still the old visual effect. If a required target or port is missing, the command prints what is required and its usage.

Limits you should know: results are best-effort (firewalls, Wi-Fi client isolation, sleeping devices, Android restrictions). `isReachable` and reverse DNS cannot be interrupted by Android, so cancel can take a second or two. Wi-Fi name (SSID) is not shown (needs location permission, deliberately not requested). Only unit tests and loopback integration tests exist; no instrumented test for the NET screen.

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

Added in v1.4 (stages 2-9, written WITHOUT a compiler: expect to fix a few build errors on the first CI run):
- Home panels: swipe left/right (or tap CORE / GLOBE / RADAR) between reactor, interactive cyber globe (drag to rotate, pinch to zoom; ALL data simulated and labelled) and radar. Reactor tap = pulse + ripple + real telemetry refresh. Long-press empty space = Settings.
- SHOW MODE (button SHOW or terminal `SHOW`): ~25 s visual demo (boot lines, rain, grid sweep, reactor, radar, globe, HUD, SYSTEM ONLINE). Animation only; EXIT button + Back leave instantly.
- VAULT: launcher-only PIN-protected private shortcut list (salted, iterated SHA-256; 5 wrong tries = 30 s lock; auto-locks when you leave). NOT a replacement for Android's lock screen.
- AI assistant (button AI): offline rule-based assistant (open/find apps, battery, storage, network, time, date, status, theme, explain commands). Optional remote AI: OFF by default, you supply your own HTTPS OpenAI-compatible endpoint, model and key in Settings; only the typed question is sent. INTERNET permission exists only for this.
- Terminal: added GLOBE and SHOW. Drawer: category filter (from Android app-category metadata). Settings: rain speed, battery-friendly mode, HUD panel toggle, tilt parallax (accelerometer), default home panel.

Added in v1.3 (stage 1, NOT compiled or run by the author): 5 themes (MATRIX GREEN, STEALTH BLACK, CYAN CYBER, RED ALERT, MINIMAL HUD) persisted in DataStore with a Settings picker; terminal commands STORAGE, NETWORK, SCAN (labelled visual + real telemetry refresh), THEME [name].

Added in v1.2: iPhone-style liquid-glass look (glass panels, pill buttons with bouncy press, glossy squircle icon tiles) and Matrix green icon tint (Settings > MATRIX GREEN ICON TINT turns it off).

Added in v1.1: real Android widgets (WDG button, AppWidgetHost; Android asks you to approve each bind), app shortcuts in the long-press menu (shown only while this is the default launcher), reorderable favorites (< > in Settings), button roles for screen readers.

Not implemented: notification-listener feature (optional in the spec), accepting pinned shortcuts requested by other apps, free drag-and-drop icon placement, profiling results.
The telemetry blocks on the home screen are launcher panels; the WDG screen holds the real widgets.

## Tests
- Unit (run in CI): `gradle testDebugUnitTest` - command parser (incl. network commands), app matcher, voice interpreter, network input validation, port-state classification, report formatting, plus loopback-socket integration tests for the port checker and discovery engine.
- Instrumented smoke tests (need a phone/emulator): `gradle connectedDebugAndroidTest` - HOME registration and activity start.
- Manual checklist: drawer shows apps, search opens an app, favorites survive restart, deny mic and verify the rest still works, toggle reduce motion, uninstall an app while launcher is open.

## Signed release build
1. Make a keystore once: `keytool -genkeypair -v -keystore release.jks -alias matrix -keyalg RSA -keysize 2048 -validity 10000`
2. Base64 it: `base64 -w0 release.jks` (Termux: same command).
3. GitHub repo -> Settings -> Secrets and variables -> Actions -> add: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
4. Actions -> **Build Signed Release APK** -> Run workflow. Download the artifact.
Never commit the keystore or passwords (`.gitignore` already blocks *.jks).

## Privacy
No analytics, ads, accounts or tracking. Only permissions: ACCESS_NETWORK_STATE (read-only), INTERNET (optional remote AI + the NET toolkit, which acts only when you start a test) and RECORD_AUDIO (asked only when you tap MIC). Speech recognition is done by your phone's speech service, which may use the internet.
