#!/usr/bin/env bash
# Installs a built APK on the owner's TVs (TV_ADDRESSES, "host:port" separated by spaces).
# Usage: deploy-tvs.sh <apk> <label> <event>
#   event: push | schedule | workflow_dispatch
#
# Quiet hours only: a push outside 01:00 to 06:00 London time is queued for the next scheduled run. A scheduled run
# outside the window does nothing. A manual run installs now. A TV that is offline or playing is left for the next run.
# Each TV remembers the commit it last got, so a TV that already has this commit is skipped.
set -euo pipefail

apk=$1
label=$2
event=$3
adb=${ADB:-adb}
sha=${GITHUB_SHA:-local}
state="$HOME/.tv-deploy/$label"
mkdir -p "$state"

hour=$(TZ=Europe/London date +%H)
in_window=false
if [ "$hour" -ge 1 ] && [ "$hour" -lt 6 ]; then in_window=true; fi

if [ "$event" = "push" ] && [ "$in_window" = false ]; then
  echo "Queued: outside 01:00-06:00 London time. The next scheduled run installs it."
  exit 0
fi
if [ "$event" = "schedule" ] && [ "$in_window" = false ]; then
  echo "Scheduled run outside the window: nothing to do."
  exit 0
fi

failed=0
for tv in ${TV_ADDRESSES:-}; do
  if ! "$adb" connect "$tv" 2>&1 | grep -q "connected"; then
    echo "$tv: offline, will retry in the next window"
    continue
  fi
  if [ "$(cat "$state/$(echo "$tv" | tr ':' '_').sha" 2>/dev/null || true)" = "$sha" ]; then
    echo "$tv: already on $sha"
    continue
  fi
  playing=$("$adb" -s "$tv" shell dumpsys media_session | grep -c "state=PlaybackState {state=PLAYING" || true)
  if [ "$playing" -gt 0 ]; then
    echo "$tv: something is playing, will retry in the next window"
    continue
  fi
  if "$adb" -s "$tv" install -r "$apk"; then
    echo "$sha" > "$state/$(echo "$tv" | tr ':' '_').sha"
    echo "$tv: updated to $sha"
  else
    echo "$tv: install failed"
    failed=1
  fi
  "$adb" disconnect "$tv" >/dev/null 2>&1 || true
done

exit "$failed"
