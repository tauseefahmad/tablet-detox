#!/bin/sh
# Lists every app on your tablet with the facts you need to clean it up safely. Changes nothing.
#
#   sh candidates.sh [serial or IP:port]
#
# One line per app, grouped by verdict:
#
#   VERDICT  STATE  PACKAGE                            WHAT IT IS
#
#   never    the tablet needs it to start, call, text, unlock or show its screen, or Android keeps it
#            running whatever you do. Leave it on.
#   keep     it carries a feature you may use (camera, messages, Find My Device). Off only if unused.
#   safe     commonly preinstalled junk: ads, promos, partner apps, unused vendor and Google apps.
#   review   nothing known about it. If you're not sure, leave it on.
#
# "never" also covers apps Android marks PERSISTENT (pm disable-user does not stick on those) and
# anything whose name says it's a launcher, keyboard, telephony, setup, provider or lock-screen part.
# Read-only, safe to run any time. docs/debloat.md says what to do with the list.
set -e
cd "$(dirname "$0")" 2>/dev/null || true

TARGET="$1"
command -v adb >/dev/null 2>&1 || {
  echo "adb is missing. Install platform-tools first:" >&2
  echo "  Mac:     brew install android-platform-tools" >&2
  echo "  Windows: https://developer.android.com/tools/releases/platform-tools" >&2
  exit 1; }
if [ -z "$TARGET" ]; then
  n=$(adb devices | tr -d '\r' | grep -c '	device$' || true)
  [ "$n" = 1 ] || { echo "Found $n devices. Plug in just one, or name it: sh candidates.sh <serial>" >&2; exit 1; }
  DEV=$(adb devices | tr -d '\r' | grep '	device$' | cut -f1)
else
  case "$TARGET" in
    *.*.*.*:*) DEV=$TARGET ;;
    *.*.*.*)   DEV=$TARGET:5555 ;;
    *)         DEV=$TARGET ;;
  esac
  case "$DEV" in *:*) adb connect "$DEV" >/dev/null 2>&1 || true ;; esac
fi
[ "$(adb -s "$DEV" get-state 2>/dev/null)" = device ] || {
  echo "Can't reach $DEV. Is it plugged in, with USB debugging allowed?" >&2; exit 1; }

sh_d() { _rc=0; _out=$(adb -s "$DEV" shell "$@" </dev/null 2>/dev/null) || _rc=$?; printf '%s\n' "$_out" | tr -d '\r'; return $_rc; }
# If the device drops off halfway, stop: a half-read list would give wrong verdicts.
lost() { echo "Lost the connection to $DEV while reading the app list. Nothing was changed. Run it again." >&2; exit 1; }

# Name patterns that are load bearing on every brand: Home and gestures, keyboards, calls and texts,
# setup, storage providers, the lock screen and the permission screens.
NEVER_RE='launcher|[.]home$|inputmethod|keyboard|honeyboard|telephony|telecom|incallui|[.]phone$|dialer|cellbroadcast|carrierconfig|[.]stk$|[.]providers[.]|provider|setupwizard|provision|permissioncontroller|packageinstaller|systemui|biometric|fingerprint|keyguard|[.]knox[.]|networkstack|wifi[.]resources|[.]ext[.]services|[.]ext[.]shared|documentsui|[.]nfc$|[.]se$|bluetooth|emergency|safetyhub|shell$|overlay'

# The tablet needs these to start, or to keep working once it has (beyond the patterns above).
NEVER="android com.google.android.gms com.google.android.gsf com.android.vending
com.android.settings com.android.settings.intelligence com.google.android.settings.intelligence
com.android.location.fused com.android.mms.service com.android.certinstaller com.android.keychain
com.android.externalstorage com.android.companiondevicemanager com.android.statementservice
com.android.vpndialogs com.android.wallpaperbackup com.android.backupconfirm com.android.sharedstoragebackup
com.android.localtransport com.android.proxyhandler com.android.pacprocessor com.android.captiveportallogin
com.android.inputdevices com.android.dynsystem com.google.android.apps.restore
com.google.android.configupdater com.google.android.partnersetup com.google.android.onetimeinitializer
com.samsung.android.authfw com.samsung.android.samsungpass com.sec.android.app.SecSetupWizard
com.samsung.android.app.telephonyui com.samsung.android.dialer com.samsung.android.incallui
com.miui.securitycenter com.miui.securitycore com.lbe.security.miui com.miui.core com.miui.system
com.android.chrome com.google.android.webview com.android.webview com.google.android.trichromelibrary"

# Turn off only if you don't use the feature they carry.
KEEP="com.google.android.googlequicksearchbox com.google.android.apps.messaging com.samsung.android.messaging
com.google.android.contacts com.samsung.android.app.contacts com.google.android.calendar com.samsung.android.calendar
com.google.android.GoogleCamera com.sec.android.app.camera com.android.camera com.android.camera2
com.google.android.apps.photos com.sec.android.gallery3d com.miui.gallery com.google.android.gm
com.google.android.apps.wellbeing com.samsung.android.forest com.google.android.apps.adm com.samsung.android.fmm
com.google.android.tts com.samsung.SMT com.google.android.marvin.talkback com.samsung.android.accessibility.talkback
com.google.android.apps.maps com.google.android.youtube com.google.android.deskclock com.sec.android.app.clockpackage
com.google.android.apps.nbu.files com.sec.android.app.myfiles com.samsung.android.lool com.samsung.android.scloud
com.samsung.android.app.smartcapture com.google.android.apps.safetyhub com.google.android.apps.turbo
com.google.android.as com.google.android.as.oss com.sec.android.app.sbrowser com.mi.globalbrowser
com.android.printspooler com.google.android.printservice.recommendation com.tabletdetox.home com.tabletdetox.home"

# Commonly preinstalled junk: ads, promos, partner installs, unused vendor and Google apps.
SAFE="com.facebook.appmanager com.facebook.services com.facebook.system com.facebook.katana
com.netflix.partner.activation com.microsoft.skydrive com.linkedin.android com.microsoft.office.officehubrow
com.samsung.android.bixby.agent com.samsung.android.bixby.wakeup com.samsung.android.app.spage
com.samsung.android.visionintelligence com.samsung.android.bixbyvision.framework
com.samsung.android.game.gamehome com.samsung.android.game.gametools com.samsung.android.ardrawing
com.samsung.android.aremoji com.samsung.android.arzone com.samsung.android.app.tips com.samsung.android.mateagent
com.samsung.android.stickercenter com.samsung.android.kidsinstaller com.samsung.android.app.watchmanagerstub
com.samsung.android.tvplus com.samsung.android.app.samsungapps.promo
com.miui.msa.global com.miui.analytics com.xiaomi.mipicks com.miui.videoplayer com.miui.player
com.xiaomi.glgm com.miui.yellowpage com.miui.hybrid com.facemoji.lite.xiaomi com.miui.miservice
com.heytap.market com.oppo.market com.heytap.browser com.heytap.music com.coloros.gamespace
com.google.android.videos com.google.android.music com.google.android.apps.books com.google.android.apps.magazines
com.google.android.apps.podcasts com.google.android.feedback com.google.android.apps.subscriptions.red
com.google.android.apps.tachyon com.google.android.play.games com.google.android.apps.youtube.kids
com.android.htmlviewer com.android.bips com.android.egg com.android.wallpaper.livepicker"

dump=$(sh_d dumpsys package) || lost
all=$(sh_d pm list packages) || lost
off=$(sh_d pm list packages -d) || lost
printf '%s\n' "$dump" | grep -q 'Package \[' || lost
printf '%s\n' "$all" | grep -q '^package:' || lost
all=$(printf '%s\n' "$all" | sed -n 's/^package://p')
off=$(printf '%s\n' "$off" | sed -n 's/^package://p')

tmp=$(mktemp -d 2>/dev/null || mktemp -d -t tabletcand)
trap 'rm -rf "$tmp"' EXIT

# One call reads every package's flags, so this stays quick on a slow tablet.
printf '%s\n' "$dump" | awk '
  /^[[:space:]]*Package \[/ { n=$0; sub(/.*\[/,"",n); sub(/\].*/,"",n); cur=n; seen=0; next }
  cur!="" && !seen && /pkgFlags=\[/ { l=$0; sub(/.*pkgFlags=\[/,"",l); sub(/\].*/,"",l); print cur "\t" l; seen=1 }
' | sort -u > "$tmp/flags"
printf '%s\n' "$all" > "$tmp/all"
printf '%s\n' "$off" > "$tmp/off"

# Plain-English names, so the list reads like a list of apps and not package names.
cat > "$tmp/names" <<'EOF'
com.google.android.gms	Google Play services
com.google.android.gsf	Google services framework
com.android.vending	Google Play Store
com.android.settings	Settings
com.android.systemui	System UI (status bar, notifications)
com.android.chrome	Chrome, also shows web pages inside apps
com.google.android.webview	Web pages inside apps
com.google.android.googlequicksearchbox	Google app: search bar, Assistant, Discover
com.google.android.apps.messaging	Google Messages (texts)
com.samsung.android.messaging	Samsung Messages (texts)
com.google.android.apps.wellbeing	Digital Wellbeing (screen time, Focus mode, bedtime)
com.samsung.android.forest	Samsung Digital Wellbeing
com.google.android.apps.adm	Find My Device
com.samsung.android.fmm	Samsung Find My Mobile
com.google.android.apps.nexuslauncher	Pixel home screen (also gestures and recent apps)
com.sec.android.app.launcher	Samsung One UI Home (also gestures and recent apps)
com.miui.home	Xiaomi home screen (also gestures and recent apps)
com.tabletdetox.home	Calm Tablet Home (this project)
com.tabletdetox.home	Calm Tablet Home
com.facebook.appmanager	Facebook app manager (preinstalled, updates itself)
com.facebook.services	Facebook services (preinstalled)
com.facebook.system	Facebook installer (preinstalled)
com.facebook.katana	Facebook (preinstalled copy)
com.netflix.partner.activation	Netflix partner activation
com.microsoft.skydrive	OneDrive (preinstalled)
com.linkedin.android	LinkedIn (preinstalled)
com.samsung.android.bixby.agent	Bixby
com.samsung.android.bixby.wakeup	Bixby voice wake-up
com.samsung.android.app.spage	Samsung Free (news feed left of Home)
com.samsung.android.visionintelligence	Bixby Vision
com.samsung.android.game.gamehome	Samsung Game Launcher
com.samsung.android.game.gametools	Samsung Game Tools
com.samsung.android.app.tips	Samsung Tips
com.samsung.android.tvplus	Samsung TV Plus
com.samsung.android.ardrawing	AR Doodle
com.samsung.android.aremoji	AR Emoji
com.miui.msa.global	MIUI ads service
com.miui.analytics	MIUI analytics
com.xiaomi.mipicks	Xiaomi GetApps store
com.miui.videoplayer	Mi Video
com.miui.player	Mi Music
com.xiaomi.glgm	Xiaomi Games
com.miui.yellowpage	Xiaomi Yellow Pages
com.google.android.videos	Google TV (Play Movies)
com.google.android.apps.books	Play Books
com.google.android.apps.magazines	Google News
com.google.android.apps.podcasts	Google Podcasts
com.google.android.feedback	Google feedback
com.google.android.apps.subscriptions.red	Google One
com.google.android.apps.tachyon	Google Meet (Duo)
com.google.android.play.games	Play Games
com.google.android.apps.youtube.kids	YouTube Kids
com.google.android.tts	Text to speech
com.google.android.marvin.talkback	TalkBack screen reader
com.android.htmlviewer	HTML file viewer
com.android.egg	Android version easter egg
com.android.printspooler	Printing
EOF

NEVER=$(printf '%s' "$NEVER" | tr '\n' ' '); KEEP=$(printf '%s' "$KEEP" | tr '\n' ' '); SAFE=$(printf '%s' "$SAFE" | tr '\n' ' ')
awk -F'\t' -v never="$NEVER" -v keep="$KEEP" -v safe="$SAFE" -v re="$NEVER_RE" -v tmp="$tmp" '
  BEGIN {
    n=split(never,x," "); for (i=1;i<=n;i++) if (x[i]!="") NV[x[i]]=1
    n=split(keep,x," ");  for (i=1;i<=n;i++) if (x[i]!="") KP[x[i]]=1
    n=split(safe,x," ");  for (i=1;i<=n;i++) if (x[i]!="") SF[x[i]]=1
    while ((getline l < (tmp "/flags")) > 0) { split(l,y,"\t"); FL[y[1]]=y[2] }
    while ((getline l < (tmp "/off")) > 0) OF[l]=1
    while ((getline l < (tmp "/names")) > 0) { split(l,y,"\t"); NM[y[1]]=y[2] }
    ORD["never"]=0; ORD["keep"]=1; ORD["safe"]=2; ORD["review"]=3
    while ((getline l < (tmp "/all")) > 0) {
      p=l; st=(p in OF) ? "off" : "on"
      # A known list wins over the name patterns: Facebook "services" is junk, not a system service.
      if (FL[p] ~ /PERSISTENT/) v="never"
      else if (p in NV) v="never"
      else if (p in KP) v="keep"
      else if (p in SF) v="safe"
      else if (p ~ re) v="never"
      else v="review"
      name=(p in NM) ? NM[p] : ""
      if (FL[p] ~ /PERSISTENT/) name=(name=="" ? "" : name " ") "(Android keeps it running)"
      printf "%d\t%s\t%s\t%s\t%s\n", ORD[v], v, st, p, name
      C[v]++
    }
    printf "SUMMARY\t%s\t%s\t%s\n", C["never"]+0, C["keep"]+0, C["safe"]+0 > (tmp "/summary")
    printf "%d\n", C["review"]+0 >> (tmp "/summary")
    close(tmp "/summary")
    exit
  }
' | sort -k1,1n -k4,4 | cut -f2-

[ -s "$tmp/summary" ] || { echo "Couldn't sort the app list. Nothing was changed." >&2; exit 1; }
n() { awk -v k="$1" 'NR==1{print $k}' "$tmp/summary" 2>/dev/null; }
echo
echo "never $(n 2) | keep $(n 3) | safe $(n 4) | review $(awk 'NR==2{print $1}' "$tmp/summary" 2>/dev/null)"
echo
echo "safe = commonly preinstalled junk. review = unknown, so leave it on unless you know it."
echo "Before turning anything off, read docs/debloat.md and ask someone before every change."
