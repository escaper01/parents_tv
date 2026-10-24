# Setting up Parents TV on a new TV (any brand)

This works on any TV or box running **Android TV / Android 7.0 or newer**: Sony, TCL, Hisense,
Philips, Xiaomi, generic MediaTek/Cvte TVs, Android boxes. Google TV models work the same way.
It does **not** work on Samsung (Tizen), LG (webOS) or Roku TVs, which do not run Android apps.

You need: a Windows PC on the same home network as the TV, and about 15 minutes.

---

## 1. Install ADB on the PC

ADB (Android Debug Bridge) is how the PC talks to the TV.

- If Android Studio is installed, ADB is already at
  `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- Otherwise download **SDK Platform-Tools for Windows** from
  <https://developer.android.com/tools/releases/platform-tools>, unzip it, and run the commands
  below from that folder (or add it to `PATH`).

Check: `adb version`

## 2. Turn on network debugging on the TV

Many cheap Android TVs (like the original Cvte/MediaTek one) **already have ADB open on port 5555**.
Try step 3 first; come back here only if it cannot connect.

**Standard Android TV / Google TV:**

1. Settings → Device Preferences (or System) → About.
2. Select **Build** (or "Android TV OS build") and press OK **7 times** until it says
   "You are now a developer".
3. Back → **Developer options** → turn on **USB debugging**. If there is a separate
   **Network debugging / ADB over network** switch, turn it on too.

**Custom manufacturer menus** (no "Build" entry):

- Try pressing OK 7 times on *Firmware version*, *Software version* or *Android version* under
  System Info.
- Some TVs hide ADB in the factory/service menu (often `Menu` then `1147`, or `Source` then
  `2580`, varies by brand). Look for *ADB*, *USB debug* or *Remote debug* and enable it.
  Change nothing else in the factory menu.

## 3. Find the TV on the network and connect

The TV's IP is in its Settings → Network → (Wi-Fi or Ethernet) → status. If you cannot find it,
scan the network for the ADB port from PowerShell on the PC; see
[TV_ADB_AND_DEBLOAT.md](TV_ADB_AND_DEBLOAT.md#finding-the-tv-on-the-network) for the scan script.

```
adb connect 192.168.1.13:5555
adb devices
```

The first time, the TV may show **"Allow USB debugging?"**: tick *Always allow from this
computer* and press OK. `adb devices` must then list the TV as `device` (not `unauthorized`).

> Tip: plug the TV into the router with an **Ethernet cable** while setting up. It is more
> reliable than Wi-Fi, and if the TV ever gets stuck at boot, the cable often comes up before
> Wi-Fi and lets you repair it (this saved the original TV once).

## 4. Install the app

From the repository folder:

```
adb -s 192.168.1.13:5555 install -r apk\ParentsTV.apk
```

Open it once to check: `adb -s 192.168.1.13:5555 shell am start -n com.osama.parentstv/.MainActivity`

## 5. Turn on auto-start

The app opens itself at power-on through an accessibility service. Android starts enabled
accessibility services at every boot, which works even on firmware that ignores the normal
"boot completed" signal (the original TV did).

```
adb -s 192.168.1.13:5555 shell settings put secure enabled_accessibility_services com.osama.parentstv/com.osama.parentstv.autostart.AutoStartService
adb -s 192.168.1.13:5555 shell settings put secure accessibility_enabled 1
```

If the TV already uses other accessibility services, read the current value first and append
ours with a `:` separator instead of replacing it:

```
adb shell settings get secure enabled_accessibility_services
```

Some TVs show a warning the first time a service is enabled; accept it on the TV.
Alternatively enable it by hand: Settings → Accessibility → *Parents TV auto-start* → On.

## 6. Test

1. Reboot: `adb -s 192.168.1.13:5555 reboot`. The app should open by itself and play the last
   channel. Check from the PC:
   ```
   adb -s 192.168.1.13:5555 shell dumpsys window | findstr mCurrentFocus
   ```
   It should name `com.osama.parentstv/...MainActivity`.
2. Put the TV in standby with the remote and wake it: the app should come back.
3. On the TV press **OK**: the channel list opens with a QR code. Scan it with a phone, enter the
   PIN `5048`, and check that tapping a channel on the phone switches the TV.
4. Check every channel's picture and sound:
   ```
   powershell -ExecutionPolicy Bypass -File tools\check_channels.ps1 -Device 192.168.1.13:5555 -Group Morocco
   ```
   (leave out `-Group` to test all channels; it takes about 20 s per channel and switches the TV,
   so do it when nobody is watching).

## 7. Optional: clean up the TV

Remove the manufacturer's bloatware and ads **carefully**: see
[TV_ADB_AND_DEBLOAT.md](TV_ADB_AND_DEBLOAT.md#removing-bloatware). Never remove the stock
launcher; on some firmware the TV will not finish booting without it.

## 8. Moving channels from another TV

On the old TV's web page: Manage → **Download backup (.json)**. On the new TV's web page:
Manage → **Import file**. Duplicates are merged, so importing twice is harmless.

## Things that differ between TVs

| Topic | What to check |
|---|---|
| Codecs | Some free streams are satellite restreams in MPEG-2 video / MP2 audio. Most TVs with a tuner decode them in hardware; cheap boxes and the emulator may not. `tools\check_channels.ps1` shows which channels lack picture or sound. |
| Volume | ◀/▶ change Android's music volume. On most TVs that is the real TV volume; if not, the remote's own volume keys still work. |
| Mute | Handled by the TV itself; the app only shows its own sign when Android reports the mute. |
| Home button | Leaves the app (on purpose). The app comes back at the next power-on. |
| Standby | Some TVs fully power off in standby; then the app starts like at boot. |
| Updates | The APK is signed with the build PC's debug key. Installing an APK built on a different PC over an existing install fails with a signature error: export the channels backup, `adb uninstall com.osama.parentstv`, install, re-enable auto-start, import the backup. |
