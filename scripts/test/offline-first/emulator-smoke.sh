#!/bin/sh
# emulator-smoke.sh — AI-free adb-driven smoke of FoodRats' offline-first flows.
# Derived from the verified 2026-07-15 emulator walk (docs/session/2026-07-14-code-cleaning/
# REPORT-offline-first.md). Asserts via uiautomator dumps + logcat markers only.
#
# Scenarios:
#   S1  offline enqueue (crew tagline edit) -> pending pill -> reconnect drain
#   S3  process-death persistence of the pending queue (force-stop + relaunch offline)
#   (S2 offline meal publish is deliberately NOT scripted — the composer path
#    needs camera capture + multi-step scroll typing that is too brittle for
#    unattended sh; run it manually per the README when needed.)
#
# Preconditions (script checks and bails with a message if unmet):
#   - a booted emulator/device visible to adb (first one is used, or set SERIAL)
#   - FoodRats debug build installed and ALREADY SIGNED IN (owner@a.com / 123456,
#     "walk crew") with the Feed reachable. One-time manual sign-in required.
#
# Leaves the device online and reverts the tagline character it appends.
# Exit 0 = all scenario assertions passed.

set -u
cd "$(git rev-parse --show-toplevel)" || exit 2

SERIAL="${SERIAL:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
[ -z "$SERIAL" ] && { echo "FAIL[setup]: no adb device"; exit 1; }
A() { adb -s "$SERIAL" "$@"; }
APP=es.schsebastian.foodrats
DUMP=/tmp/frdump.xml

PASS=0; FAILED=0
ok()   { echo "PASS $1"; }
fail() { echo "FAIL $1"; FAILED=1; }

restore_network() {
  A shell svc wifi enable >/dev/null 2>&1
  A shell svc data enable >/dev/null 2>&1
  A shell cmd connectivity airplane-mode disable >/dev/null 2>&1
}
trap restore_network EXIT INT TERM

dump() { A exec-out uiautomator dump /dev/tty 2>/dev/null | grep '<?xml' > "$DUMP" || A shell "uiautomator dump /sdcard/frdump.xml >/dev/null 2>&1 && cat /sdcard/frdump.xml" > "$DUMP"; }

has_text() { dump; grep -q "$1" "$DUMP"; }

wait_text() { # wait_text <needle> <seconds>
  i=0
  while [ "$i" -lt "$2" ]; do
    if has_text "$1"; then return 0; fi
    sleep 2; i=$((i+2))
  done
  return 1
}

wait_text_gone() {
  i=0
  while [ "$i" -lt "$2" ]; do
    if ! has_text "$1"; then return 0; fi
    sleep 2; i=$((i+2))
  done
  return 1
}

# tap the center of the first node whose serialized form contains $1 (match on
# content-desc or text attribute value). Re-dumps every time — bounds shift when
# the offline banner appears.
tap_marker() {
  dump
  node=$(grep -oE "<node[^>]*(content-desc|text)=\"[^\"]*$1[^\"]*\"[^>]*>" "$DUMP" | head -1)
  [ -z "$node" ] && return 1
  bounds=$(echo "$node" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  [ -z "$bounds" ] && return 1
  set -- $(echo "$bounds" | tr -c '0-9' ' ')
  x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
  A shell input tap "$x" "$y"
  sleep 2
}

# tap the center of the first node matching $2 that sits *below* the first
# node matching $1 (i.e. its top edge is past $1's bottom edge). Crew Settings
# renders one "Save" button per editable field, so a bare tap_marker "Save"
# always hits the first (Crew Name's) Save — this scopes the match to the
# field's own Save button, e.g. tap_after "TAGLINE" "Save".
tap_after() {
  after_pat=$1
  target_pat=$2
  dump
  ref=$(grep -oE "<node[^>]*(content-desc|text)=\"[^\"]*$after_pat[^\"]*\"[^>]*>" "$DUMP" | head -1)
  [ -z "$ref" ] && return 1
  refb=$(echo "$ref" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  [ -z "$refb" ] && return 1
  refy2=$(echo "$refb" | tr -c '0-9' ' ' | awk '{print $4}')
  candidates=$(grep -oE "<node[^>]*(content-desc|text)=\"[^\"]*$target_pat[^\"]*\"[^>]*>" "$DUMP")
  target=""
  oldIFS=$IFS
  IFS='
'
  for cand in $candidates; do
    b=$(echo "$cand" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
    [ -z "$b" ] && continue
    cy1=$(echo "$b" | tr -c '0-9' ' ' | awk '{print $2}')
    if [ "$cy1" -gt "$refy2" ]; then target="$cand"; break; fi
  done
  IFS=$oldIFS
  [ -z "$target" ] && return 1
  tbounds=$(echo "$target" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  set -- $(echo "$tbounds" | tr -c '0-9' ' ')
  x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
  A shell input tap "$x" "$y"
  sleep 2
}

# dismiss the on-screen keyboard via BACK (first BACK press closes the IME
# without navigating away — confirmed: mInputShown flips true->false and the
# screen stays put). Required before any tap in the lower half of Crew
# Settings: the IME overlays that region and swallows taps meant for the
# app's Save button, which uiautomator still reports at its (occluded)
# app-layout coordinates.
dismiss_ime() {
  A shell input keyevent 4
  sleep 1
}

go_offline() {
  A shell cmd connectivity airplane-mode enable
  A shell svc wifi disable
  A shell svc data disable
  sleep 3
}

go_online() {
  restore_network
  sleep 5
}

launch() { A shell am start -n "$APP/.MainActivity" >/dev/null 2>&1; sleep 5; }

OFFLINE_BANNER="offline"                # substring of "You're offline — changes sync…"
SYNC_PILL="waiting to sync"

echo "== offline-first emulator smoke on $SERIAL =="

# -- preconditions ------------------------------------------------------------
A shell pm path "$APP" >/dev/null 2>&1 || { echo "FAIL[setup]: $APP not installed — run ./gradlew :androidApp:installDebug"; exit 1; }
launch
if has_text "Continue with Google\|Sign in"; then
  echo "FAIL[setup]: app is on the sign-in screen — sign in once manually (owner@a.com / 123456, crew 'walk crew'), then re-run."
  exit 1
fi

# -- S1: offline enqueue -> pill -> reconnect drain ----------------------------
A logcat -c
go_offline
if wait_text "$OFFLINE_BANNER" 10; then ok "S1.banner: offline banner shown"; else fail "S1.banner: offline banner not found"; fi

# enqueue: crew settings -> tagline append one char -> save -> back to feed
tap_marker "Crew settings" || fail "S1.nav: crew-settings gear not found"
A shell input swipe 540 1800 540 900 300; sleep 1   # scroll to the tagline field
if tap_marker "TAGLINE"; then
  A shell input keyevent 123                        # MOVE_END
  A shell input text "x"                            # safe char — "~" gets mksh tilde-expanded by the device shell
  sleep 1
  dismiss_ime                                       # IME occludes the Save button below; a tap while it's open never reaches the app
  tap_after "TAGLINE" "Save" || fail "S1.save: Save button not found"
else
  fail "S1.field: tagline field not found"
fi
A shell input keyevent 4; sleep 2                    # BACK to feed
if wait_text "$SYNC_PILL" 15; then ok "S1.pill: pending pill visible offline"; else fail "S1.pill: pending pill not found"; fi

# -- S3: process death persistence (still offline) -----------------------------
A shell am force-stop "$APP"; sleep 2
launch
if wait_text "$SYNC_PILL" 20; then ok "S3.restore: pending pill restored after force-stop"; else fail "S3.restore: pill not restored"; fi

# -- reconnect drain ------------------------------------------------------------
go_online
if wait_text_gone "$SYNC_PILL" 60; then ok "S1/S3.drain: queue drained after reconnect"; else fail "S1/S3.drain: pill still present 60s after reconnect"; fi
if A logcat -d 2>/dev/null | grep -qE "FR/Outbox.*replayed .*Success"; then
  ok "S1.logcat: outbox replay Success marker present"
else
  fail "S1.logcat: no 'replayed … Success' marker"
fi

# -- cleanup: revert the appended tagline char (online, syncs immediately) -----
tap_marker "Crew settings"
A shell input swipe 540 1800 540 900 300; sleep 1
if tap_marker "TAGLINE"; then
  A shell input keyevent 123
  A shell input keyevent 67                          # DEL the appended x
  dismiss_ime
  tap_after "TAGLINE" "Save"
  A shell input keyevent 4
  echo "cleanup: tagline reverted"
else
  echo "cleanup WARNING: could not revert tagline — remove the trailing 'x' manually"
fi

echo "== done =="
[ "$FAILED" -eq 0 ] && { echo "RESULT: PASS"; exit 0; } || { echo "RESULT: FAIL"; exit 1; }
