#!/bin/sh
# Installs Calm Tablet Home on a tablet (real or emulator) exactly like a user would, then checks it works.
# Usage: sh ci/smoke-test.sh <adb serial> [screenshot.png]
set -e
cd "$(dirname "$0")/.."
DEV="$1"; SHOT="${2:-smoke.png}"
ME=com.tabletdetox.home/.MainActivity
fail() {
  echo "FAIL: $*" >&2
  echo "-- on screen: $(focus)" >&2
  adb -s "$DEV" logcat -d -b crash 2>/dev/null | grep -A12 "FATAL EXCEPTION" | head -40 >&2
  adb -s "$DEV" exec-out screencap -p > "${SHOT%.png}-fail.png" 2>/dev/null || true
  exit 1
}
focus() { adb -s "$DEV" shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r'; }
wait_home() { for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do focus | grep -q com.tabletdetox.home && return 0; sleep 1; done; return 1; }
ui() { adb -s "$DEV" shell "uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1; adb -s "$DEV" shell "cat /sdcard/ui.xml"; }
home() { adb -s "$DEV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r'; }
# The centre of the first node with this text, from a uiautomator dump.
centre() { echo "$1" | tr '>' '\n' | grep -m1 "text=\"$2\"" | sed 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/' \
  | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'; }

echo "== Android $(adb -s "$DEV" shell getprop ro.build.version.release | tr -d '\r'), $(adb -s "$DEV" shell wm size | tail -1 | tr -d '\r')"

echo "== install"
sh install.sh "$DEV"
sleep 2
[ "$(home)" = "$ME" ] || fail "default home is $(home)"
adb -s "$DEV" shell input keyevent KEYCODE_HOME
wait_home || fail "home screen not in front"

echo "== Home shows a clock, screen time and the Add app tile"
x=$(ui)
echo "$x" | grep -qE 'text="[0-9]{1,2}:[0-9]{2}"' || fail "no clock"
echo "$x" | grep -q 'text="Screen time today' || fail "no screen time (usage access not granted?)"
echo "$x" | grep -q 'text="Add app"' || fail "no Add app tile"

echo "== the app panel opens, lists Settings, and search narrows it"
set -- $(centre "$x" "Add app"); adb -s "$DEV" shell input tap "$1" "$2"; sleep 2
x=$(ui)
echo "$x" | grep -q 'text="All apps"' || fail "the app panel didn't open"
set -- $(centre "$x" "Search"); adb -s "$DEV" shell input tap "$1" "$2"; sleep 1
adb -s "$DEV" shell input text "sett"; sleep 1
ui | grep -q 'text="Settings"' || fail "searching didn't find Settings"
echo "   search finds Settings"
adb -s "$DEV" shell input keyevent KEYCODE_HOME; sleep 1
wait_home || fail "Home didn't come back from the app panel"

echo "== custom app list: Settings on Home"
sh install.sh "$DEV" "com.android.settings"
adb -s "$DEV" shell input keyevent KEYCODE_HOME
wait_home || fail "home screen not in front after custom list"
ui | grep -q 'text="Settings"' || fail "Settings didn't show up on Home"

echo "== greyscale on, then undo puts it all back"
before=$(adb -s "$DEV" shell settings get secure accessibility_display_daltonizer_enabled | tr -d '\r')
GRAYSCALE=1 sh install.sh "$DEV" >/dev/null
[ "$(adb -s "$DEV" shell settings get secure accessibility_display_daltonizer_enabled | tr -d '\r')" = 1 ] || fail "greyscale didn't turn on"
adb -s "$DEV" exec-out screencap -p > "$SHOT"
sh check.sh "$DEV" || fail "check.sh says something's wrong"
sh undo.sh "$DEV" >/dev/null
sleep 2
case "$(home)" in *com.tabletdetox.home*) fail "after undo, home is still Calm Tablet Home" ;; esac
after=$(adb -s "$DEV" shell settings get secure accessibility_display_daltonizer_enabled | tr -d '\r')
[ "$after" = "$before" ] || fail "greyscale was $before before, $after after undo"
echo "   home is back to $(home)"
echo "PASS ($SHOT)"
