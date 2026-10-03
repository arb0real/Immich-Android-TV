#!/usr/bin/env bash
# Install Immich TV on an Android TV device (e.g. Nvidia Shield) over the network with adb.
#
# Usage:
#   scripts/sideload-shield.sh [--ci [branch] | --local | <path/to/app.apk>] [--dream]
#
#   --ci [branch]  (default) download the APK of the latest successful CI run of the
#                  current branch (or the given one). Needs the GitHub CLI (gh), logged in.
#   --local        build a debug APK locally with ./gradlew assembleDebug
#   <apk>          install the given APK file
#   --dream        start the screensaver after installing, to check it right away
#
# The device address is read from $SHIELD_IP, or from a .shield file in the repo root
# containing e.g. "192.168.1.50" (port 5555 is used when none is given).
#
# One time setup on the Shield: Settings > Device Preferences > About > click "Build" 7 times,
# then Settings > Device Preferences > Developer options > enable "Network debugging".
# The first connection shows an "Allow USB debugging?" prompt on the TV: tick "Always allow".
set -euo pipefail

PACKAGE="nl.giejay.android.tv.immich"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

die() { echo "Error: $*" >&2; exit 1; }

mode="ci"
branch=""
apk=""
dream=false
while [ $# -gt 0 ]; do
  case "$1" in
    --ci)
      mode="ci"
      if [ $# -gt 1 ] && [ "${2#--}" = "$2" ]; then branch="$2"; shift; fi
      ;;
    --local) mode="local" ;;
    --dream) dream=true ;;
    -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *.apk) mode="file"; apk="$1" ;;
    *) die "unknown argument: $1 (see --help)" ;;
  esac
  shift
done

command -v adb >/dev/null || die "adb not found. Install Android platform-tools: https://developer.android.com/tools/releases/platform-tools"

host="${SHIELD_IP:-}"
if [ -z "$host" ] && [ -f "$ROOT/.shield" ]; then
  host="$(tr -d '[:space:]' < "$ROOT/.shield")"
fi
[ -n "$host" ] || die "set SHIELD_IP or put the Shield's IP address in $ROOT/.shield"
case "$host" in *:*) ;; *) host="$host:5555" ;; esac

case "$mode" in
  ci)
    command -v gh >/dev/null || die "GitHub CLI (gh) not found: https://cli.github.com (or use --local / an APK path)"
    branch="${branch:-$(git rev-parse --abbrev-ref HEAD)}"
    echo "Looking for the latest successful CI build of '$branch'..."
    run_id="$(gh run list --workflow android.yml --branch "$branch" --status success --limit 1 \
      --json databaseId --jq '.[0].databaseId // empty')"
    [ -n "$run_id" ] || die "no successful CI run found for branch '$branch'"
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    gh run download "$run_id" --name immich-tv-beta.apk --dir "$tmp" \
      || die "run $run_id has no APK artifact. Is the signing key configured in the repository secrets?"
    apk="$(find "$tmp" -name '*.apk' | head -n 1)"
    ;;
  local)
    ./gradlew assembleDebug
    apk="$(ls -t app/build/outputs/apk/debug/*.apk | head -n 1)"
    ;;
esac
[ -n "$apk" ] && [ -f "$apk" ] || die "APK not found"

echo "Connecting to $host..."
adb connect "$host" >/dev/null || true
state="$(adb -s "$host" get-state 2>/dev/null || true)"
if [ "$state" != "device" ]; then
  die "cannot use $host (state: ${state:-not connected}). Is Network debugging enabled? If the TV shows an 'Allow debugging' prompt, accept it and run this again."
fi

echo "Installing $(basename "$apk")..."
if ! output="$(adb -s "$host" install -r "$apk" 2>&1)"; then
  echo "$output" >&2
  if echo "$output" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
    echo >&2
    echo "The installed app is signed with a different key (e.g. the Play Store version, or a" >&2
    echo "debug build vs. a release build). Uninstall it first (this removes its settings):" >&2
    echo "  adb -s $host uninstall $PACKAGE" >&2
  fi
  exit 1
fi

if [ "$dream" = true ]; then
  echo "Starting the screensaver..."
  adb -s "$host" shell am start -n com.android.systemui/.Somnambulator >/dev/null \
    || echo "Could not start the screensaver; is Immich TV selected as screensaver?" >&2
else
  adb -s "$host" shell monkey -p "$PACKAGE" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 || true
fi
echo "Done."
