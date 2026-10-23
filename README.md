# Tablet Detox

### Make an old, slow Android tablet fast and calm again.

Claude Code or Codex does the work. You just tap Allow on the tablet.

Quiet glass tiles and gradient wallpapers, made for touch and for both orientations, plus a cleanup
that knows which tablet parts are load bearing. Good for the old family tablet, a kitchen tablet, or
a kid's tablet that should only open a few apps.

| 🧹 Removes the junk | 🪟 A calm home screen | ⚫ Greyscale, if you want it | ↩️ Undo anytime |
|---|---|---|---|
| Preinstalled Facebook, Bixby, MIUI ads, game launchers, partner apps: turned off. | A clock, today's screen time and only the apps you chose. No widgets, no feed, no badges. | One switch makes games and feeds a lot less pull. | Apps are turned off, never deleted. `undo.sh` puts your old home screen back. |

## What Calm Home does

- **Tap** a tile to open it. **Hold** it to move it, replace it with another app, take it off Home, or
  uninstall it (apps that came with the tablet get App info instead, where Android can turn them off).
- **Move**: the tile gets an outline. Tap the spot it should go to, then tap it again to finish.
- **Add app** (the last tile), or **swipe up from the bottom**, opens every app with search. Tap to
  open, hold to add it to Home or take it off.
- **The settings button** (top right), or **hold any empty spot**: Wallpaper (fourteen gradients, dark
  and bright, or your own wallpaper under a dark veil), Screen time, Tablet settings, Choose home app.
- **Tap the clock** for alarms, **the date** for your calendar, **the screen time line** for Digital
  Wellbeing.
- Tiles reflow to the screen: about four across in portrait, six to eight in landscape.

One screen of plain Java, no libraries, no Gradle. Colours only, no images, so it stays light on old
hardware.

## Get started

**1. Turn on developer mode on the tablet.**
Settings → About tablet → tap **Build number** 7 times. Then Settings → System → Developer options →
turn on **USB debugging**. Read the [warnings](#notes-and-warnings) first.

**2. Plug the tablet into your laptop** and open [Claude Code](https://claude.com/claude-code) or
[Codex](https://openai.com/codex) there. Tap **Allow** on the tablet when it asks.

**3. Paste this in.**

```text
Help me clean up my Android tablet and make it faster and calmer.
My tablet is a <brand and model>. It's used for <who / what>.

1. Before you change anything, tell me in plain words: this turns
   off system apps, and that has some risk, and you're an AI, so
   you can make mistakes. undo.sh and your undo list turn
   everything back on. Ask me to confirm I want to go ahead.
2. Connect to my tablet over USB with adb.
3. Download https://github.com/tauseefahmad/tablet-detox and run
   its candidates.sh. Show me every app in plain English: safe to
   turn off, needed by the tablet, and apps I use. Check free
   memory and storage.
4. Ask me before every change. Turn apps off instead of deleting
   them. Keep a list of what you changed in my-tablet.txt, in the
   format check.sh reads, and how to undo it.
5. Never turn off: Google Play services, Google Play, Settings,
   System UI, my tablet's own home screen/launcher, the keyboard,
   anything for unlocking, setup, Find My Device, or (if it has
   cellular) calls, texts and emergency alerts. If you're not
   sure, leave it on.
6. Install the calm home screen: run install.sh with these apps:
   <your apps>. Ask me if I want greyscale (GRAYSCALE=1).
7. Turn off animations if the tablet is slow.
8. Restart the tablet once and check it starts and unlocks
   normally before you say you're done. Show me free memory
   before and after.
9. Once everything is checked, remind me to turn off USB debugging.
```

**4. Turn USB debugging off when it's done.** Settings → System → Developer options.

### Notes and warnings

> [!WARNING]
> **Do it at your own risk.** This turns off system apps. The AI can make mistakes. It asks you before
> every change, and `undo.sh` plus its undo list turn everything back on. You're responsible for your
> own tablet.

> [!WARNING]
> **Your tablet's own home screen stays on.** Nothing here turns off the stock
> launcher: on tablets it also runs the taskbar, gestures and recent apps. Calm Home just takes over
> the Home button.

> [!NOTE]
> **A kid's tablet:** Calm Home makes the other apps harder to find, not impossible. For real limits
> use Google Family Link or Samsung Kids alongside it.

## Without AI

You only need `adb` (Mac: `brew install android-platform-tools`; Windows: Google's
[SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools), run the scripts
from Git Bash).

```sh
sh install.sh                                          # the tablet plugged in by USB
sh install.sh "" "com.android.chrome,com.netflix.mediaclient,com.amazon.kindle"   # choose the apps
GRAYSCALE=1 sh install.sh                              # and turn the screen grey
sh candidates.sh                                       # every app with a verdict, changes nothing
sh check.sh --fix my-tablet.txt                        # after an update: put your changes back
sh undo.sh                                             # old home screen, colour, and Calm Home removed
```

Over Wi-Fi instead of a cable: turn on Wireless debugging, pair once with `adb pair <IP>:<pairing port>`,
then pass the `IP:port` it shows to every script.

`check.sh` reads one change per line:

```text
off com.samsung.android.game.gamehome
global window_animation_scale 0
appops com.example.game POST_NOTIFICATION ignore
```

`docs/debloat.md` has the rules behind the verdicts and the never-touch and commonly-safe lists.

## Help

<details>
<summary><b>Which tablets does it work on?</b></summary>
<br>

Android 6 and newer: Samsung Galaxy Tab, Lenovo Tab, Xiaomi Pad, Pixel Tablet and others. Home is
switched with Android's Home role on Android 10+, and the classic home setting before that. If the
tablet keeps its own home screen, `install.sh` opens the "Home app" setting for you to pick Calm Home.

</details>

<details>
<summary><b>Amazon Fire tablets?</b></summary>
<br>

Partly. The cleanup works (Fire OS is Android underneath), but current Fire tablets don't let another
app replace Amazon's home screen, so you keep theirs.

</details>

<details>
<summary><b>iPad?</b></summary>
<br>

No. iPadOS doesn't let anything replace the home screen or turn off built-in apps. Screen Time and
Guided Access do the detox half by hand.

</details>

<details>
<summary><b>Screen time says "Tap to see your screen time"</b></summary>
<br>

Calm Home needs usage access to read Android's own usage log. `install.sh` grants it. If it didn't
stick, tap the line and turn on Calm Home in the list. Nothing leaves the tablet.

</details>

<details>
<summary><b>How do I undo it?</b></summary>
<br>

`sh undo.sh` hands Home back to your old home screen, puts colour back as it was, and uninstalls Calm
Home. Or without a laptop: Settings → Apps → Default apps → Home app, then uninstall Calm Home like any
app. To undo the cleanup too, ask Claude or Codex to reverse everything in `my-tablet.txt`.

</details>

<details>
<summary><b>For developers: build it yourself</b></summary>
<br>

1. Put your default apps in `apps.txt`.
2. Install Java 17+ and the Android SDK (any platform and build-tools 34 or newer; Android Studio's
   SDK works).
3. `sh build.sh`, then `sh install.sh` installs your build instead of the release.
4. `sh ci/smoke-test.sh <serial>` checks it all works on a tablet or emulator.

Keep `key.jks`, made on your first build: updates must be signed with the same key.

</details>

---

MIT License.
