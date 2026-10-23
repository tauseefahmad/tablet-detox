#!/bin/sh
# Installs Calm Tablet Home on your tablet and makes it the home screen. Only needs adb.
#
#   sh install.sh                              the one tablet plugged in by USB
#   sh install.sh <serial or IP:port>          a tablet picked by name, or over Wireless debugging
#   sh install.sh "" "com.whatsapp,com.spotify.music"    choose the apps on Home (8 at most)
#   GRAYSCALE=1 sh install.sh                  also turn the screen grey (undo.sh turns colour back on)
#
# Nothing is turned off or uninstalled: your tablet's own home screen stays installed, just not in
# charge, so gestures and recent apps keep working. undo.sh puts it back in charge.
# Uses out/calmtablet.apk if you built it, otherwise downloads the latest release.
set -e
cd "$(dirname "$0")"

TARGET="$1"; APPS="$2"
ME_PKG=com.tabletdetox.home; ME=$ME_PKG/.MainActivity
STATE=/data/local/tmp/calmtablet-state.txt
RELEASE_URL=${RELEASE_URL:-https://github.com/tauseefahmad/tablet-detox/releases/latest/download/calmtablet.apk}

command -v adb >/dev/null 2>&1 || {
  echo "adb is missing. Install it first:" >&2
  echo "  Mac:     brew install android-platform-tools" >&2
  echo "  Windows: https://developer.android.com/tools/releases/platform-tools" >&2
  exit 1; }

# No name: the one tablet adb can see. An IP without a port gets :5555.
if [ -z "$TARGET" ]; then
  n=$(adb devices | tr -d '\r' | grep -c '	device$' || true)
  [ "$n" = 1 ] || { echo "Found $n tablets. Plug in just one, or name it: sh install.sh <serial>  (adb devices lists them)" >&2; exit 1; }
  TAB=$(adb devices | tr -d '\r' | grep '	device$' | cut -f1)
else
  case "$TARGET" in
    *.*.*.*:*) TAB=$TARGET ;;
    *.*.*.*)   TAB=$TARGET:5555 ;;
    *)         TAB=$TARGET ;;
  esac
  case "$TAB" in *:*) adb connect "$TAB" >/dev/null || true ;; esac
fi

state=$(adb -s "$TAB" get-state 2>&1 || true)
case "$state" in
  device) ;;
  *unauthorized*) echo "Your tablet is asking \"Allow USB debugging?\". Tap Allow (tick \"Always allow\"), then run this again." >&2; exit 1 ;;
  *) echo "Can't reach $TAB. Check the cable (or Wireless debugging) and that USB debugging is on." >&2; exit 1 ;;
esac

# Keeps adb's exit status, so a failed command on the tablet counts as failed here.
sh_tab() { _rc=0; _out=$(adb -s "$TAB" shell "$@" </dev/null) || _rc=$?; [ -z "$_out" ] || printf '%s\n' "$_out" | tr -d '\r'; return $_rc; }
current_home() { sh_tab cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; }
sdk=$(sh_tab getprop ro.build.version.sdk)

APK=out/calmtablet.apk
if [ ! -f "$APK" ]; then
  APK=$(mktemp -d)/calmtablet.apk
  echo "Downloading Calm Tablet Home…"
  curl -fsSL -o "$APK" "$RELEASE_URL"
fi

# Remember what we're about to change, once, so undo.sh can put it back exactly. Running install.sh
# again keeps the first record: by then Home is already ours.
# In the same argument as the command: Git Bash on Windows turns an argument that starts with / into a Windows path.
if ! sh_tab "[ -s $STATE ]"; then
  prev=$(current_home | sed 's|/.*||')
  case "$prev" in ""|android|$ME_PKG) prev=- ;; esac
  sh_tab "echo home $prev > $STATE" || { echo "Couldn't save what to undo on the tablet, so nothing was changed." >&2; exit 1; }
  for k in accessibility_display_daltonizer_enabled accessibility_display_daltonizer; do
    sh_tab "echo secure $k \$(settings get secure $k) >> $STATE"
  done
fi

echo "Installing… If your tablet asks to allow the install (Play Protect, or Xiaomi's install prompt), allow it on the tablet."
if ! out=$(adb -s "$TAB" install -r "$APK" 2>&1); then
  case "$out" in
    *UPDATE_INCOMPATIBLE*) echo "Replacing an older copy signed with a different key…"
                           adb -s "$TAB" uninstall $ME_PKG >/dev/null; adb -s "$TAB" install "$APK" ;;
    *) echo "$out" >&2; exit 1 ;;
  esac
fi

# Screen time on Home needs usage access. It's the tablet's own usage log; nothing leaves the tablet.
# Right after an install Android can still reset it, so check it stuck and try again.
usage_ok() { sh_tab appops get $ME_PKG GET_USAGE_STATS 2>/dev/null | grep -q allow; }
for _ in 1 2 3; do
  sh_tab appops set $ME_PKG GET_USAGE_STATS allow >/dev/null 2>&1 || true
  sleep 2
  usage_ok && break
done
usage_ok || echo "Couldn't turn on screen time. Tap the line on Home to allow it."

if [ -n "$APPS" ]; then
  # The stamp makes the app apply this list once, even if Android later replays this start.
  adb -s "$TAB" shell am start -n $ME --es stamp "$(date +%s)" --es apps "\"$APPS\"" >/dev/null
fi

if [ "$GRAYSCALE" = 1 ]; then
  # Android's own colour correction set to greyscale: the same switch as Settings > Accessibility.
  sh_tab settings put secure accessibility_display_daltonizer 0
  sh_tab settings put secure accessibility_display_daltonizer_enabled 1
  echo "Screen is now grey. undo.sh turns colour back on, or Settings > Accessibility > Colour correction."
fi

# Make it the home screen: the role (Android 10+), and the classic way.
if [ "$sdk" -ge 29 ]; then
  sh_tab cmd role add-role-holder --user 0 android.app.role.HOME $ME_PKG >/dev/null 2>&1 || true
fi
sh_tab cmd package set-home-activity "$ME" >/dev/null 2>&1 || true

adb -s "$TAB" shell input keyevent KEYCODE_HOME
if [ "$(current_home)" = "$ME" ]; then
  echo "Done. Press Home to see it. Swipe up for all apps; hold anywhere for settings."
else
  echo "Installed, but your tablet still opens its own home screen."
  echo "Opening the home app setting: choose Calm Tablet Home there."
  adb -s "$TAB" shell am start -a android.settings.HOME_SETTINGS >/dev/null 2>&1 || true
fi
