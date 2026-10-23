# Cleaning your tablet safely

Tablet Detox does three jobs. The calm home screen and greyscale are automatic. Turning off the junk
is a judgement call for every app, so it's left to whoever is driving the setup. This page is that
judgement, plus the rules that keep it reversible.

Start with `sh candidates.sh`. It lists every app on the tablet with a verdict, and changes nothing:

| verdict | meaning |
| --- | --- |
| `never` | the tablet needs it to start, call, text, unlock or draw its screen, or Android keeps it running whatever you do. Leave it on. |
| `keep` | it carries a feature you may use: camera, texts, Find My Device, screen time. Off only if unused. |
| `safe` | commonly preinstalled junk: ads, promos, partner installs, unused vendor and Google apps. |
| `review` | nothing known about it. Leave it on unless you know what it is. |

Work down the list from the top. Turn off, wait, check the tablet still works.

## The five rules

### 1. Turn apps off, never uninstall them

`pm disable-user --user 0 <package>` hides an app and `pm enable <package>` brings it back.
`pm uninstall --user 0` on a system app can be undone too (`cmd package install-existing <package>`),
but people forget which ones they removed. Disable, and keep every package you touch in a list.

### 2. Never turn off your tablet's own home screen

On a TV the stock launcher is just a launcher. On a tablet it is also the gesture bar, the recent
apps screen and, on Samsung and Xiaomi, part of the lock screen. That's why `install.sh` only hands
the Home role to Calm Home and leaves the stock one installed and on. `candidates.sh` marks every
launcher `never`.

### 3. Never turn off what calls, texts or unlocks

The dialer, the in-call screen, telephony, carrier config, emergency alerts, the keyboard, the
fingerprint and face unlock parts, the setup wizard and the permission screens are load bearing. A
tablet with cellular that can't call 911/112, or can't show its keyboard to type the PIN, is a brick.
`candidates.sh` marks these `never` by name on every brand.

### 4. Apps Android marks PERSISTENT can't be turned off

```
adb shell dumpsys package com.example | grep -m1 pkgFlags
# pkgFlags=[ SYSTEM HAS_CODE PERSISTENT ALLOW_CLEAR_USER_DATA ... ]
```

If you see `PERSISTENT`, leave it alone. `candidates.sh` marks these `never` too.

### 5. Detox the habit, not just the tablet

Junk apps slow a tablet down. Apps you *chose* are what take your time. The kind tools for those are
not "turn off", they are:

- **Off Home.** Calm Home shows only the apps you put there. Everything else is behind Add app or a swipe up.
- **No notifications.** `adb shell appops set <package> POST_NOTIFICATION ignore` (Android 13+), or
  Settings > Notifications. Add the line `appops <package> POST_NOTIFICATION ignore` to your list so
  `check.sh` can keep it that way.
- **Greyscale.** `GRAYSCALE=1 sh install.sh`, or Settings > Accessibility > Colour correction >
  Greyscale. Colour is what makes feeds and games feel rewarding.
- **App timers and Focus mode** live in Digital Wellbeing. Tap the screen time line on Home.

## Do it in batches

Turn off five apps, then check the tablet: unlock it, play a video, use the camera, connect a keyboard
or pen if you use one, and cast if you cast. Restart, and check again. When a batch is clean,
do the next one. If the tablet misbehaves you then know which batch to undo.

## If something goes wrong

- Bring one app back: `adb shell pm enable <package>`
- Bring everything back that's turned off:
  `for p in $(adb shell pm list packages -d | tr -d '\r' | sed 's/package://'); do adb shell pm enable "$p"; done`
- Put the old home screen back and colour too: `sh undo.sh`
- Tablet stuck restarting: it usually still answers adb for a few seconds each boot. Plug it in and
  run the loop above. If it never does, use the tablet's recovery mode ("wipe cache" first, factory
  reset last).

## Reference

### Never turn off

| package | what it is |
| --- | --- |
| `com.google.android.gms`, `com.google.android.gsf` | Google Play services and framework |
| `com.android.vending` | Google Play Store |
| `com.android.settings`, `com.android.systemui` | Settings, the status bar and notifications |
| `com.android.phone`, `*telecom*`, `*telephony*`, `*incallui*`, `*dialer*` | calls, on tablets with cellular |
| `*cellbroadcast*`, `*emergency*` | emergency alerts and emergency calls |
| `*launcher*`, `com.miui.home` | your tablet's home screen, gestures and recent apps |
| `*inputmethod*`, `*keyboard*`, `com.samsung.android.honeyboard` | keyboards |
| `*biometric*`, `*fingerprint*`, `*keyguard*` | unlocking |
| `*setupwizard*`, `*provision*`, `*permissioncontroller*`, `*packageinstaller*` | setup, permissions, installing |
| `*provider*` | contacts, calendar, media, downloads and settings storage |
| `com.android.chrome`, `com.google.android.webview` | web pages inside apps on most tablets |
| anything flagged `PERSISTENT` | it will not stay off |

### Commonly safe to turn off

| package | what it is |
| --- | --- |
| `com.facebook.appmanager`, `com.facebook.services`, `com.facebook.system` | Facebook's preinstalled updater, even if you never installed Facebook |
| `com.netflix.partner.activation`, `com.microsoft.skydrive`, `com.linkedin.android` | partner installs |
| `com.samsung.android.bixby.*`, `com.samsung.android.app.spage`, `com.samsung.android.visionintelligence` | Bixby and Samsung Free |
| `com.samsung.android.game.gamehome`, `com.samsung.android.game.gametools` | Game Launcher |
| `com.samsung.android.app.tips`, `com.samsung.android.tvplus`, `com.samsung.android.ar*` | Samsung extras |
| `com.miui.msa.global`, `com.miui.analytics` | the ads and analytics behind MIUI's pop-ups |
| `com.xiaomi.mipicks`, `com.xiaomi.glgm`, `com.miui.videoplayer`, `com.miui.player` | Xiaomi store, games, media |
| `com.google.android.videos`, `com.google.android.apps.books`, `com.google.android.apps.magazines` | Google apps few people use |
| `com.google.android.feedback`, `com.google.android.apps.subscriptions.red` | Google feedback, Google One |

### Depends on you

| package | feature |
| --- | --- |
| `com.google.android.googlequicksearchbox` | the Google app: search, Assistant, Discover feed |
| `com.google.android.apps.wellbeing`, `com.samsung.android.forest` | screen time, app timers, Focus mode, bedtime |
| `com.google.android.apps.adm`, `com.samsung.android.fmm` | finding a lost tablet. Keep it. |
| `com.google.android.apps.messaging`, `com.samsung.android.messaging` | texts. Only off if another texting app is your default |
| `com.google.android.tts`, `com.google.android.marvin.talkback` | text to speech and the screen reader |

Vendors rename and add to these lists, so treat them as strong hints, not gospel. When in doubt,
leave it on: a slow tablet is better than a tablet you can't call from.
