#!/bin/sh
# Checks your tablet is still detoxed, and with --fix puts back whatever an update undid.
#
#   sh check.sh [serial]                     check Home and screen time
#   sh check.sh [serial] --fix               and fix them
#   sh check.sh [serial] --fix my-tablet.txt  also check your own list of changes
#
# The list has one change per line (# starts a comment):
#   off com.example.app                        the app stays turned off
#   secure accessibility_display_daltonizer_enabled 1     a setting (global, secure or system)
#   appops com.example.app POST_NOTIFICATION ignore       an app permission (here: no notifications)
set -e

TARGET=; FIX=; LIST=
for a in "$@"; do case "$a" in --fix) FIX=1 ;; *.txt) LIST=$a ;; *) TARGET=$a ;; esac; done
[ -z "$LIST" ] || { [ -f "$LIST" ] && [ -r "$LIST" ]; } || { echo "Can't read $LIST." >&2; exit 2; }
if [ -z "$TARGET" ]; then
  n=$(adb devices | tr -d '\r' | grep -c '	device$' || true)
  [ "$n" = 1 ] || { echo "Found $n tablets. Plug in just one, or name it: sh check.sh <serial>" >&2; exit 2; }
  TAB=$(adb devices | tr -d '\r' | grep '	device$' | cut -f1)
else
  case "$TARGET" in
    *.*.*.*:*) TAB=$TARGET ;;
    *.*.*.*)   TAB=$TARGET:5555 ;;
    *)         TAB=$TARGET ;;
  esac
  case "$TAB" in *:*) adb connect "$TAB" >/dev/null || true ;; esac
fi
[ "$(adb -s "$TAB" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TAB." >&2; exit 2; }

sh_tab() { adb -s "$TAB" shell "$@" </dev/null | tr -d '\r'; } # adb would eat the list below
bad=0
# Prints a result line. With --fix, runs the fix and checks again.
check() { # name, test command, fix command
  if eval "$2"; then echo "  ok    $1"; return; fi
  if [ -n "$FIX" ] && eval "$3" >/dev/null 2>&1 && eval "$2"; then echo "  fixed $1"; return; fi
  echo "  WRONG $1"; bad=$((bad + 1))
}
is_off() { sh_tab pm list packages -d "$1" | grep -qx "package:$1"; }
installed() { sh_tab pm list packages "$1" | grep -qx "package:$1"; }

# shellcheck disable=SC2034 # ME is read inside the eval'd check strings below
ME_PKG=com.tabletdetox.home; ME=$ME_PKG/.MainActivity
home() { sh_tab cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; }
echo "Calm Tablet Home"
installed $ME_PKG || { echo "  WRONG Calm Tablet Home isn't installed. Run install.sh."; exit 1; }
check "Home opens Calm Tablet Home" '[ "$(home)" = "$ME" ]' \
  'sh_tab cmd role add-role-holder --user 0 android.app.role.HOME $ME_PKG; sh_tab cmd package set-home-activity "$ME"'
check "screen time is on" 'sh_tab appops get $ME_PKG GET_USAGE_STATS | grep -q allow' \
  'sh_tab appops set $ME_PKG GET_USAGE_STATS allow'

if [ -n "$LIST" ]; then
  echo "Your changes ($LIST)"
  grep -v '^[[:space:]]*#' "$LIST" | grep -v '^[[:space:]]*$' | tr -d '\r' | while read -r kind a b c; do
    case "$kind" in
      off)
        if installed "$a"; then check "$a stays off" "is_off $a" "sh_tab pm disable-user --user 0 $a"
        else echo "  gone  $a (not installed)"; fi ;;
      global|secure|system)
        check "$kind $a = $b" "[ \"\$(sh_tab settings get $kind $a)\" = \"$b\" ]" "sh_tab settings put $kind $a $b" ;;
      appops)
        check "$a $b = $c" "sh_tab appops get $a $b | grep -q \"$c\"" "sh_tab appops set $a $b $c" ;;
      *) echo "  ?     can't read: $kind $a $b $c" ;;
    esac
  done > "${TMPDIR:-/tmp}/tabletcheck.$$" || true
  cat "${TMPDIR:-/tmp}/tabletcheck.$$"
  n=$(grep -c "WRONG" "${TMPDIR:-/tmp}/tabletcheck.$$" || true); bad=$((bad + n))
  rm -f "${TMPDIR:-/tmp}/tabletcheck.$$"
fi

if [ "$bad" -gt 0 ]; then
  [ -n "$FIX" ] && echo "$bad thing(s) could not be fixed." || echo "$bad thing(s) changed back. Run again with --fix to put them right."
  exit 1
fi
echo "All good."
