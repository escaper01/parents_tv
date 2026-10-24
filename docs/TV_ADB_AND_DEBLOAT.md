# TV over ADB: connecting, debugging and removing bloatware

Everything here was done from a Windows PC with PowerShell on the original TV:
**MediaTek "SMART_TV" (board m5621), Cvte firmware, Android 9**, stock launcher
`com.cvte.tv.launcher.fall` ("LauncherFall4").
Commands use `adb -s <device>`; replace `192.168.1.13:5555` with your TV.

- [Finding the TV on the network](#finding-the-tv-on-the-network)
- [Connecting](#connecting)
- [Looking around](#looking-around)
- [Debugging the app](#debugging-the-app)
- [Removing bloatware](#removing-bloatware)
- [Home screen (launcher) lessons](#home-screen-launcher-lessons)
- [Recovering a TV stuck at boot](#recovering-a-tv-stuck-at-boot)
- [Audio, volume and codecs](#audio-volume-and-codecs)
- [PC-side gotchas](#pc-side-gotchas)

---

## Finding the TV on the network

The PC and the TV must be on the **same network** (same router). The TV's IP changes when it
switches between Wi-Fi and cable, and can change after a router restart. The original TV was
seen at `192.168.1.1`, `.11` (Wi-Fi) and `.13` (Ethernet) at different times.

**1. Scan for the ADB port (5555) on the whole subnet** (PowerShell; adjust `192.168.1` to your
network, which is the first three numbers of the PC's own IP from `ipconfig`):

```powershell
# wake up the ARP table so every device answers
1..254 | % { (New-Object Net.NetworkInformation.Ping).SendPingAsync("192.168.1.$_", 800) } | Out-Null; Start-Sleep 3
# try port 5555 everywhere at once
$jobs = 1..254 | % { $ip = "192.168.1.$_"; $tc = New-Object Net.Sockets.TcpClient
  [pscustomobject]@{ ip = $ip; tc = $tc; ar = $tc.BeginConnect($ip, 5555, $null, $null) } }
Start-Sleep 6
foreach ($j in $jobs) { if ($j.ar.IsCompleted -and $j.tc.Connected) { "ADB OPEN: $($j.ip)" }; $j.tc.Close() }
```

Ignore a hit on the PC's own IP (an emulator or WSL can listen on 5555).

**2. Recognise the TV by its network card (MAC address)** in the ARP table:

```powershell
arp -a
```

Original TV: Wi-Fi `84-7a-b6-83-a6-aa`, Ethernet `bc-fa-b8-8a-22-80`.
If the TV's MAC is missing from `arp -a`, the TV is **not on your network**. That happened
once because a neighbour's Wi-Fi had almost the same name; plugging in the cable fixed it.

**3. Or read it on the TV:** Settings → Network → status, or the Parents TV channel list (OK),
whose header shows `http://<tv-ip>:8080`.

> The PC can only list Wi-Fi names (`netsh wlan show networks`) when Windows Location access is on.

## Connecting

```
adb connect 192.168.1.13:5555        # "connected to ..." means it worked
adb devices -l                       # must say "device", not "unauthorized" or "offline"
adb disconnect 192.168.1.1:5555      # forget an old address that now shows "offline"
```

- `unauthorized`: accept the "Allow USB debugging?" prompt on the TV.
- `offline` after the TV rebooted or changed network: `adb disconnect`, scan again, reconnect.
- "Connection timed out": wrong IP, TV asleep, or ADB not enabled (see NEW_TV_SETUP.md step 2).

## Looking around

```
adb shell getprop ro.product.manufacturer           # MediaTek
adb shell getprop ro.product.model                  # SMART_TV
adb shell getprop ro.build.version.release          # 9
adb shell getprop sys.boot_completed                # 1 when fully booted
adb shell uptime

adb shell pm list packages -3                       # apps you installed
adb shell pm list packages -s                       # system apps
adb shell pm path com.cvte.tv.launcher.fall         # where an app's APK lives

adb shell dumpsys window | findstr mCurrentFocus    # which app is on screen
adb shell dumpsys power | findstr "mWakefulness Display"   # awake / asleep
adb shell "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"   # home-screen apps
adb shell settings list secure                      # also: system, global
```

**Readable app names.** `pm list` gives package ids only. To see the names shown on the TV, pull
the APKs and read their labels with `aapt` from the Android SDK build-tools:

```powershell
$aapt = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\aapt.exe"
$p = 'com.mediatek.androidbox'
$path = ((adb shell pm path $p) -replace 'package:','').Trim()
adb pull $path "$env:TEMP\$p.apk" | Out-Null
& $aapt dump badging "$env:TEMP\$p.apk" | Select-String "application-label:"    # -> Kindlink
```

**Screenshots** (the TV's screen, saved on the PC):

```
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png C:\Users\you\Desktop\tv.png
adb shell rm /sdcard/s.png
```

Video from the player often appears black in screenshots; overlays (channel list, banners) show.

**Remote control from the PC:**

```
adb shell input keyevent KEYCODE_HOME          # also: DPAD_UP/DOWN/LEFT/RIGHT, DPAD_CENTER, BACK,
adb shell input keyevent KEYCODE_CHANNEL_UP    #       VOLUME_UP/DOWN/MUTE, 0..9, SEARCH, INFO
adb shell input text jazeera                   # types into the focused text box
adb shell am start -n com.osama.parentstv/.MainActivity
adb reboot
```

Do not send keys while someone is watching: they mix with the real remote's presses.

## Debugging the app

```
adb logcat -c                                                     # clear the log
adb logcat -s ParentsTv.Player:* ParentsTv.AutoStart:* ParentsTv.Admin:* AndroidRuntime:E
adb logcat -d | findstr /i "parentstv FATAL"                      # dump what happened (no follow)
```

- `ParentsTv.Player`: every stream opened, the tracks found (with `supported=YES/NO_...`), decoder
  names, errors and stalls. Lines like `NO_EXCEEDS_CAPABILITIES` or `NO_UNSUPPORTED_TYPE` explain
  missing picture or sound.
- `ParentsTv.AutoStart`: "Service connected" at boot, "Screen on" after standby.

**Web API from the PC** (works through ADB, even when the PC and TV are on different networks):

```
adb forward tcp:18081 tcp:8080
curl -H "X-Pin: 5048" http://127.0.0.1:18081/api/status       # what is playing: formats, frames rendered
curl -H "X-Pin: 5048" http://127.0.0.1:18081/api/channels
curl -H "X-Pin: 5048" -o backup.json http://127.0.0.1:18081/api/export
curl -X POST -H "X-Pin: 5048" -d "{}" http://127.0.0.1:18081/api/play/2m.ma@sd
curl -X POST -H "X-Pin: 5048" --data-binary @app\src\main\assets\default_channels.json http://127.0.0.1:18081/api/import
curl -X POST -H "X-Pin: 5048" -d "{}" http://127.0.0.1:18081/api/reset        # replace with the built-in list
```

The full list is in [DEVELOPMENT.md](DEVELOPMENT.md#web-api). To check every channel:
`tools\check_channels.ps1` (see NEW_TV_SETUP.md step 6).

**Auto-start state:**

```
adb shell settings get secure enabled_accessibility_services
adb shell settings get secure accessibility_enabled
curl -H "X-Pin: 5048" http://127.0.0.1:18081/api/autostart     # switch + setup state
adb shell pm grant com.osama.parentstv android.permission.WRITE_SECURE_SETTINGS   # lets the switch enable the service itself
```

How the one-time setup and the on/off switch work: [NEW_TV_SETUP.md, step 5](NEW_TV_SETUP.md#5-turn-on-auto-start-one-time-setup).

## Removing bloatware

`pm uninstall -k --user 0 <package>` hides an app for the TV's user and keeps its files, so it is
**fully reversible** and survives reboots, but a factory reset brings everything back. It does not
need root.

```
adb shell pm uninstall -k --user 0 <package>      # remove
adb shell cmd package install-existing <package>  # bring back
```

### What was removed from the original TV

| Package | Shown as | Why |
|---|---|---|
| `com.facebook.katana` | Facebook | Preinstalled, unused |
| `fusion.android.tv.demo` | Demo app | Store demo |
| `com.stark.store` | App store tile | Manufacturer store with promos |
| `com.netflix.mediaclient` | Netflix | Unused |
| `com.google.android.feedback` | Google feedback | Telemetry |
| `com.google.android.partnersetup` | Partner setup | First-setup leftover |
| `com.google.android.apps.restore` | Restore | First-setup leftover |
| `com.mediatek.androidbox` | Kindlink | Phone-to-TV link app |
| `com.cvte.tv.pictorial` | Invisible Art | Artwork screensaver |
| `com.cvte.tv.remotedebug` | RemoteDebug | Manufacturer remote debugging |
| `com.osama.autostart` | AutoStart SNRT | Our first auto-start helper, replaced by Parents TV |
| `news.androidtv.launchonboot` | Launch on Boot | Tried for auto-start; had no app picker on this firmware |

Kept on purpose: Google Play (store and updates), YouTube, Files, the TV Setting app, every
`com.cvte.*` / `com.mediatek.*` service, the keyboard, WebView, **the stock launcher**.

### Safe-removal checklist

1. Write down what you remove (this table).
2. Remove **one or two packages at a time**, then reboot and check the TV still starts.
3. Never remove: the launcher (`*launcher*`), `com.android.*` core services, TV input / tuner
   services (`com.mediatek.tvinput`, `com.cvte.tvservice`, `com.cvte.tv.api.impl`), settings apps,
   the keyboard, WebView, Google Play services.
4. Keep the Ethernet cable plugged in while experimenting (see recovery below).

## Home screen (launcher) lessons

- We installed **FLauncher** (`me.efesser.flauncher`, from the Play Store / APKPure) as a cleaner
  home screen without YouTube suggestions, and set it as default:
  `adb shell cmd package set-home-activity me.efesser.flauncher/.MainActivity`.
- **This Cvte firmware starts its own launcher by name at every boot**
  (`START ... cmp=com.cvte.tv.launcher.fall/.MainActivity from uid 0` in the log), whatever the
  default home is. When that launcher was removed, **the TV hung at the boot screen**, and its
  Wi-Fi never came up.
- Final setup: stock launcher kept and set as default
  (`adb shell cmd package set-home-activity com.cvte.tv.launcher.fall/.MainActivity`), FLauncher
  left installed as an optional app, and Parents TV opens on top of the launcher at every boot.

## Recovering a TV stuck at boot

What worked when the TV stuck at the boot screen after the launcher was removed:

1. Unplug the TV for 30 s and power it on. If still stuck:
2. **Connect the TV to the router with an Ethernet cable.** Even with the UI stuck, ADB came up on
   the wired network.
3. Scan for port 5555 (above), then:
   ```
   adb connect <tv-ip>:5555
   adb shell cmd package install-existing com.cvte.tv.launcher.fall
   adb shell cmd package install-existing com.cvte.tv.launcher.fall.skin
   ```
4. The TV finished booting by itself. If a "choose home app" dialog appears, set the stock
   launcher as default (command above).

If ADB never comes up, the last resort is the TV's recovery / factory reset, which restores every
removed app and erases all settings and installed apps.

## Audio, volume and codecs

- **Volume:** Android's music stream on this TV is 0-100 and is the real TV volume.
  ```
  adb shell media volume --stream 3 --get
  adb shell media volume --stream 3 --set 11
  ```
  The TV draws no bar for these changes, so the app draws its own for ◀/▶.
- **Mute** is handled by the firmware (red speaker icon); `dumpsys audio` shows the state.
- **Which formats the TV can decode:**
  ```
  adb shell "cat /vendor/etc/media_codecs*.xml /system/etc/media_codecs*.xml | grep -i '<MediaCodec '"
  ```
  The original TV has hardware decoders for H.264, HEVC, MPEG-2 video, MP2/MP3/AAC/AC3/DTS audio
  (`OMX.MS.*`). Several Moroccan streams are MPEG-2 + MP2 satellite restreams that play on the TV
  but **not on the Android emulator**, which lacks those decoders.

## PC-side gotchas

- **Android emulator "too many emulator instances"** on Windows: Hyper-V reserves the emulator's
  usual ports. Check with `netsh int ipv4 show excludedportrange protocol=tcp`, then start the
  emulator on a free port and connect manually:
  ```
  emulator -avd Parents_TV -port 5678 -no-snapshot-save
  adb connect 127.0.0.1:5679
  ```
- **APKs downloaded from apkpure.com with curl** return a Cloudflare page, not an APK: download
  them in a browser instead, then `adb install` the file. Check what you got with
  `aapt dump badging file.apk` before installing.
- **Gradle "Unable to establish loopback connection"** happened only inside a sandboxed shell; the
  same build works in a normal PowerShell window (`.\build.ps1`).
