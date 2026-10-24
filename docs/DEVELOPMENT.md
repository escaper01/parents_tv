# Development guide

## Requirements

- Windows with **Android Studio** (provides the SDK, ADB and the JDK at
  `C:\Program Files\Android\Android Studio\jbr`). Gradle 8.14.3 is downloaded by the wrapper.
- For the emulator: SDK command-line tools and the image `system-images;android-30;android-tv;x86`.

## Build

```
.\build.ps1                                   # unit tests + debug and release APKs -> apk\ParentsTV.apk
.\build.ps1 -Install                          # ... and install on every connected device
.\build.ps1 -Install -Device 192.168.1.13:5555
```

Or by hand: `.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease`.

- The release build is signed with the PC's **debug key** (`~/.android/debug.keystore`), so it can
  be sideloaded without managing a keystore. An APK built on another PC cannot be installed over
  this one without uninstalling first (see NEW_TV_SETUP.md, "Updates").
- `apk/ParentsTV.apk` is committed on purpose so a TV can be set up without building. Rebuild and
  commit it whenever the app changes, and bump `versionCode` / `versionName` in
  `app/build.gradle.kts`.

## Run on the Android TV emulator

```
sdkmanager "system-images;android-30;android-tv;x86"
avdmanager create avd -n Parents_TV -k "system-images;android-30;android-tv;x86" -d tv_1080p
emulator -avd Parents_TV -port 5678 -no-snapshot-save -no-boot-anim
adb connect 127.0.0.1:5679          # only needed when the port is not auto-detected
adb -s emulator-5678 install -r apk\ParentsTV.apk
adb -s emulator-5678 shell settings put secure enabled_accessibility_services com.osama.parentstv/com.osama.parentstv.autostart.AutoStartService
adb -s emulator-5678 shell settings put secure accessibility_enabled 1
adb -s emulator-5678 forward tcp:18080 tcp:8080     # web page at http://localhost:18080
```

`-port 5678` avoids the ports Hyper-V reserves on Windows (see TV_ADB_AND_DEBLOAT.md).
The keyboard arrows, Enter and Esc act as the remote. The emulator has no MPEG-2 / MP2 decoders,
so some Moroccan satellite restreams show no picture or no sound there but play on a real TV.

## How the code is organised

```
app/src/main/java/com/osama/parentstv/
  ParentsTvApp.kt              Application: channel repository, settings, starts the web server
  MainActivity.kt              full-screen player, remote keys, channel list + search, overlays
  player/PlayerController.kt   ExoPlayer setup, URL fallback, stall watchdog, retry, diagnostics
  data/Channel.kt              channel model (id, name, group, logo, urls, enabled)
  data/ChannelJson.kt          channels.json format (read / write)
  data/M3uParser.kt            M3U / M3U8 playlist import
  data/ChannelMerger.kt        duplicate-free merging (same id or any shared URL)
  data/ChannelRepository.kt    files/channels.json on the TV, change listeners
  data/Settings.kt             last channel, favorites, PIN (SharedPreferences)
  web/AdminServer.kt           small HTTP server on port 8080 + JSON API
  web/QrCode.kt                QR bitmap (ZXing)
  web/NetworkInfo.kt           the TV's LAN address
  autostart/AutoStartService.kt  accessibility service: opens the player at boot and on wake
  autostart/AutoStart.kt       the on/off switch + "is the service enabled on this TV" + setup helpers
  autostart/BootReceiver.kt    fallback for devices that deliver BOOT_COMPLETED
app/src/main/assets/
  default_channels.json        built-in channel list (generated, see below)
  admin/index.html             the web page (Channels + Manage tabs), plain HTML/JS
app/src/test/                  unit tests for the parser and the merging rules
```

Design notes:

- **Channel numbers are positions** in the list of enabled channels, so reordering never leaves gaps.
- **Playback** forces the highest bitrate the TV supports, starts after 1 s of buffer, and treats
  20 s without progress as a failure: it then tries the channel's next URL, and after all of them,
  retries with a growing pause (5 s to 30 s).
- **HLS extractor flags** `FLAG_ALLOW_NON_IDR_KEYFRAMES | FLAG_DETECT_ACCESS_UNITS` are needed for
  satellite restreams without access-unit delimiters.
- **Auto-start** uses an accessibility service because this TV's firmware never delivers
  `BOOT_COMPLETED` to third-party apps. At boot it opens the player at once; if the stock launcher
  then draws on top within 60 s, it brings the player back **once**, so a later Home press is
  respected.
- **The auto-start switch** (`Settings.autoStart`) is checked in `AutoStartService.openPlayer`,
  the single place that opens the player automatically. `AutoStart.state()` combines it with
  whether the service is enabled: `ACTIVE`, `OFF` or `NEEDS_SETUP`. An app cannot enable its own
  accessibility service unless `WRITE_SECURE_SETTINGS` was granted over ADB (`pm grant`); then the
  switch enables it directly, otherwise it opens the TV's Accessibility screen.

## Channel tools

```
powershell -ExecutionPolicy Bypass -File tools\build_channels.ps1
powershell -ExecutionPolicy Bypass -File tools\check_channels.ps1 -Device 192.168.1.13:5555 [-Group Morocco]
```

- `tools/channel_list.ps1` decides **which channels ship, their category and their order**
  (Morocco first). Each entry matches names in the public playlists; optional `first` (preferred
  URLs), `exclude` (URL regex to drop) and `allowGeo` (keep entries the list marks geo-blocked).
  Keep this file pure ASCII; Windows PowerShell 5 reads BOM-less files as ANSI.
- `tools/build_channels.ps1` downloads iptv-org and Free-TV, keeps the wanted channels, tests every
  URL (HTTP 2xx + `#EXTM3U`), drops dead ones and writes `default_channels.json`.
- `tools/check_channels.ps1` plays each channel on a device through the web API and reports
  `OK / NO SOUND / NO PICTURE / NOT PLAYING` from the player's frame counters.

After rebuilding the list, build the app, install it, and either **import** the new file (keeps
the TV's own edits and order, adds new channels at the end) or **reset** (replaces the TV's list
with the new one; export a backup first).

## Web API

All `/api/*` calls except `/api/ping` need the PIN in an `X-Pin` header (or `?pin=` for the export
link). Bodies and responses are JSON.

| Method & path | Does |
|---|---|
| `GET /` | The web page |
| `GET /api/ping` | `{"ok":true}`, no PIN |
| `GET /api/channels` | `{channels:[...], current, favorites:[ids]}` |
| `POST /api/channels` | Create or update one channel (`id` to update; optional `position`, 0-based) |
| `DELETE /api/channels/{id}` | Delete a channel |
| `POST /api/reorder` | `{ids:[...]}` new order |
| `POST /api/import` | Raw JSON (this format) or M3U text; merges, returns `{added, merged, unchanged}` |
| `POST /api/import-url` | `{url}`: download and import a playlist |
| `GET /api/export` | Download `channels.json` |
| `POST /api/reset` | Replace the list with the built-in one |
| `POST /api/play/{id}` | Switch the TV to a channel (the player must be on screen) |
| `GET /api/status` | What is playing: URL, video/audio format, frames rendered, bitrate |
| `POST /api/test` | `{url}`: quick reachability check of a stream link |
| `GET /api/autostart` | `{enabled, serviceEnabled, canEnableItself, state: ACTIVE/OFF/NEEDS_SETUP, adbCommands}` |
| `POST /api/autostart` | `{enabled: true/false}`: the auto-start switch; returns the same as GET |
| `POST /api/autostart/open-settings` | Opens the TV's Accessibility screen; `{opened}` is `ACCESSIBILITY`, `SETTINGS` (main settings, as a fallback) or `NONE` |
| `POST /api/favorites/{id}` | Add a favorite |
| `DELETE /api/favorites/{id}` | Remove a favorite |

## Channel file format

```json
{
  "version": 1,
  "channels": [
    { "id": "2m.ma@sd", "number": 1, "name": "2M", "group": "Morocco",
      "logo": "https://...", "urls": ["http://.../index.m3u8", "http://backup/..."], "enabled": true }
  ]
}
```

A bare array, or a single `"url"` instead of `"urls"`, is also accepted. On import, a channel
matching an existing one by `id` or by any URL is merged (new URLs become backups); everything
else is appended.
