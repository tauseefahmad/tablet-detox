#!/bin/sh
# Puts your tablet's own home screen back in charge, turns colour back on if install.sh made it grey,
# and removes Calm Tablet Home.
# Usage: sh undo.sh [serial or IP:port]
set -e
TARGET="$1"
ME_PKG=com.tabletdetox.home
STATE=/data/local/tmp/calmtablet-state.txt
if [ -z "$TARGET" ]; then
  n=$(adb devices | tr -d '\r' | grep -c '	device$' || true)
  [ "$n" = 1 ] || { echo "Found $n tablets. Plug in just one, or name it: sh undo.sh <serial>" >&2; exit 1; }
  TAB=$(adb devices | tr -d '\r' | grep '	device$' | cut -f1)
else
  case "$TARGET" in
    *.*.*.*:*) TAB=$TARGET ;;
    *.*.*.*)   TAB=$TARGET:5555 ;;
    *)         TAB=$TARGET ;;
  esac
  case "$TAB" in *:*) adb connect "$TAB" >/dev/null || true ;; esac
fi
[ "$(adb -s "$TAB" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TAB." >&2; exit 1; }
sh_tab() { adb -s "$TAB" shell "$@" </dev/null | tr -d '\r'; }

# END marks a full read; no record at all is fine (then there's nothing to put back but Home).
saved=$(sh_tab "if [ -e $STATE ]; then cat $STATE; fi; echo END")
case "$saved" in *END) ;; *) echo "Couldn't read what install.sh changed. Nothing was undone." >&2; exit 1 ;; esac

prev=$(printf '%s\n' "$saved" | awk '$1=="home"{print $2; exit}')
printf '%s\n' "$saved" | awk '$1=="secure"{print $2, $3}' | while read -r k v; do
  if [ -z "$v" ] || [ "$v" = null ]; then sh_tab settings delete secure "$k" >/dev/null
  else sh_tab settings put secure "$k" "$v"; fi
done

# Hand Home back before Calm Tablet Home goes, so the tablet is never without one.
if [ -n "$prev" ] && [ "$prev" != - ]; then
  sh_tab cmd role add-role-holder --user 0 android.app.role.HOME "$prev" >/dev/null 2>&1 || true
  act=$(sh_tab cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
    | grep "^ *$prev/" | head -1 | sed 's/^ *//')
  [ -z "$act" ] || sh_tab cmd package set-home-activity "$act" >/dev/null 2>&1 || true
  echo "Home is $prev again."
fi
adb -s "$TAB" uninstall $ME_PKG >/dev/null 2>&1 || true
sh_tab "rm -f $STATE"
adb -s "$TAB" shell input keyevent KEYCODE_HOME
echo "Removed. If your tablet asks which home screen to use, pick your old one and choose Always."
