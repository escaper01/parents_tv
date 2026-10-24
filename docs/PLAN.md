# Parents TV — plan

An Android TV app that behaves like a plain cable box: it opens by itself when the TV turns on, plays the last channel, and is driven only with channel up/down, numbers and volume. Channels are managed from a phone or PC browser.

## Goals

1. **Zero-skill use.** The TV turns on, the last channel plays. CH+/CH− (or ▲/▼) changes channel, number keys jump to a channel, volume works as on any TV. Nothing else to learn.
2. **Opens automatically.** On boot and on wake from standby, using the accessibility-service method already proven on this TV (the boot-receiver method does not work on this firmware).
3. **Manageable channel list.** Add, edit, remove, reorder and import channels from a browser on the home network, with no typing on the TV.
4. **Robust playback.** Each channel can have several stream URLs; dead links fall through to the next and retry quietly.

## Non-goals (for now)

- EPG / programme guide, recording, catch-up, user accounts.
- Al Aoula: SNRT does not publish a stream iptv-org can list. It can be added later through the web page if you find a working URL.

## Remote control behaviour

| Key | Action |
|---|---|
| CH+ / ▲ | Next channel |
| CH− / ▼ | Previous channel |
| 0–9 | Type a channel number; switches after 2 s or on OK |
| OK | Show / hide the channel list overlay (▲/▼ to move, OK to pick) |
| Back | Hide overlay; never leaves the app while playing |
| Volume / Mute | Left to the TV (system volume, works unchanged) |
| Info / long-press OK | Shows the address of the management page and its PIN |

A banner with the channel number, name and logo appears for 3 s after each switch.

## Channel data

Stored as `channels.json` in the app's private storage, seeded from a bundled default list on first run.

```json
{
  "version": 1,
  "channels": [
    {
      "id": "2m-ma",
      "number": 1,
      "name": "2M",
      "group": "Morocco",
      "logo": "https://…/2m.png",
      "urls": ["https://…/index.m3u8", "https://…/backup.m3u8"],
      "enabled": true
    }
  ]
}
```

- **Duplicates.** `id` is unique. On import, a channel whose `id` already exists, or whose first URL matches an existing URL, is merged: new URLs are added and no second channel is created.
- **Import formats.** This JSON format, plus plain `.m3u` / `.m3u8` playlists, which are converted using the `tvg-id` attribute or the name as the `id`.
- **Export.** Download the current list as JSON for backup or to load onto another TV.

### Default channels (from iptv-org; every link is checked before shipping)

- **Morocco:** 2M, 2M Monde, Al Maghribia, Arryadia, Chada TV, Medi1TV Arabic / Maghreb / Afrique
- **News:** France 24 Arabic, France 24 French, France 24 English, BBC News, BBC Arabic, Fox News
- **MBC:** MBC 1, MBC 4, MBC 5, MBC Drama, MBC Masr, MBC Masr 2, MBC Iraq, and any other MBC channel with a live link

Channels marked geo-blocked in iptv-org are left out, because they will not play in Morocco.

## Management web page

- Runs inside the app on port `8080` of the TV (for example `http://192.168.1.1:8080`).
- Protected by a 4-digit PIN that the TV shows when you press Info.
- Features: list, add, edit (name, number, logo, URLs), delete, drag to reorder, enable or disable, import a file (JSON or M3U), export JSON, and "test link".
- Changes apply on the TV immediately; the current channel keeps playing unless you change or delete it.

## Auto-start

- An `AccessibilityService` built into the app. Android binds it at every boot, and it launches the player about 8 s later, once the stock launcher has drawn.
- The same service listens for screen-on (wake from standby) and brings the player back to the front.
- Enabled once over ADB during install; the TV's settings remember it.
- The old helper `com.osama.autostart` is removed from the TV when this app is installed.

## Technical choices

| Area | Choice | Why |
|---|---|---|
| Language | Kotlin | Standard for Android |
| Player | AndroidX Media3 ExoPlayer (HLS) | Handles `.m3u8` streams, retries, and the TV's MediaTek decoder |
| UI | Plain Android Views, full-screen, no Leanback library | Simple, fast on a weak TV CPU, full control of keys |
| Web server | NanoHTTPD, embedded | Tiny, no dependencies |
| Storage | JSON file and SharedPreferences (last channel, PIN) | No database needed |
| SDK | minSdk 24, target 35 | The TV runs Android 9 (API 28) |
| Build | Gradle (Kotlin DSL) on this PC | Android Studio is already installed |

## Project layout

```
parents_tv/
  PLAN.md
  app/src/main/
    AndroidManifest.xml
    java/…/MainActivity.kt          full-screen player and remote keys
    java/…/player/PlayerController.kt  ExoPlayer, URL fallback, retry
    java/…/data/ChannelRepository.kt   load, save, merge, import
    java/…/data/M3uParser.kt
    java/…/web/AdminServer.kt          NanoHTTPD and REST API
    java/…/autostart/AutoStartService.kt  accessibility service
    assets/default_channels.json
    assets/admin/index.html            management page
  app/src/test/…                       unit tests (parser, merge, dedupe)
  tools/build_channels.ps1             rebuilds default_channels.json from iptv-org and checks links
```

## Milestones

1. **Skeleton:** project builds; full-screen player plays one hard-coded stream on the emulator.
2. **Channels and remote:** JSON storage, default list, CH±/numbers/OK overlay, last channel remembered, link fallback.
3. **Web management:** NanoHTTPD, PIN, CRUD, import/export with de-duplication.
4. **Auto-start:** accessibility service; test with an emulator reboot.
5. **Emulator test pass:** you test everything on the Android TV emulator.
6. **TV install:** install over ADB, enable the service, remove the old helper, reboot test on the real TV.

## Test checklist (for you on the emulator)

- [ ] App opens on boot and plays the last channel
- [ ] CH+/CH−, ▲/▼, number keys, OK list all switch correctly
- [ ] Volume and mute work
- [ ] Back never drops you to the home screen while watching
- [ ] A dead channel shows "reconnecting…" and does not freeze
- [ ] Web page: add, edit, delete, reorder; changes appear on the TV
- [ ] Import the same JSON twice → no duplicate channels
- [ ] Import an M3U file → channels added correctly

## Risks

- **Free streams change or die.** Mitigated by several URLs per channel, the "test link" button, and `tools/build_channels.ps1` to refresh.
- **Weak TV hardware.** 1080p HLS should be fine for this MediaTek chip; if not, lower-quality variants are preferred.
- **Firmware quirks.** The accessibility method is already proven on this TV; screen-on behaviour after standby still needs a test.
