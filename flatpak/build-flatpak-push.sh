#!/usr/bin/env bash
set -euo pipefail

# Build locally, deploy to the Deck's user installation, verify that deployment,
# then launch that exact user-scoped app. This intentionally does not touch the
# system installation (which would require root on the Deck).
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

# AppStream's Glycin image loader needs a Flatpak system repository for its sandbox.
# On Nix/developer setups with only a per-user installation, use that repo as the system dir.
if [[ -z "${FLATPAK_SYSTEM_DIR:-}" && ! -d /var/lib/flatpak/repo && -d "$HOME/.local/share/flatpak/repo" ]]; then
  export FLATPAK_SYSTEM_DIR="$HOME/.local/share/flatpak"
fi

STEAM_DECK_HOST="${STEAM_DECK_HOST:-deck@steamdeck.local}"
if [[ "$STEAM_DECK_HOST" != *@* ]]; then STEAM_DECK_HOST="deck@$STEAM_DECK_HOST"; fi
STEAM_DECK_PATH="${STEAM_DECK_PATH:-/home/deck/flatpaks}"
SKIP_BUNDLE="${SKIP_BUNDLE:-true}"
APP_ID="io.github.danielportelabyrne.Cinema"
REMOTE_NAME="cinema-local"
SSH_OPTS=(-o ConnectTimeout=10)

ssh_deck() {
  if [[ -n "${SSHPASS:-}" ]]; then
    sshpass -e ssh "${SSH_OPTS[@]}" "$@"
  else
    ssh "${SSH_OPTS[@]}" "$@"
  fi
}
scp_deck() {
  if [[ -n "${SSHPASS:-}" ]]; then
    sshpass -e scp "$@"
  else
    scp "$@"
  fi
}

echo "Building Linux application bundle..."
./gradlew :desktop:linuxBundle -Pcinema.linux=true -Pcinema.desktopOnly=true --console=plain
[[ -d desktop/build/linux ]] || { echo "Gradle did not create desktop/build/linux" >&2; exit 1; }

echo "Building Flatpak repository..."
mkdir -p build/flatpak
flatpak run org.flatpak.Builder --user --force-clean \
  --repo=build/flatpak/repo --state-dir=build/flatpak/state \
  build/flatpak/app flatpak/io.github.danielportelabyrne.Cinema.yml
[[ -d build/flatpak/repo/objects ]] || { echo "Flatpak Builder did not produce a repository" >&2; exit 1; }

echo "Connecting to $STEAM_DECK_HOST..."
ssh_deck "$STEAM_DECK_HOST" "mkdir -p '$STEAM_DECK_PATH'"

if [[ "$SKIP_BUNDLE" == "true" ]]; then
  echo "Syncing repository to $STEAM_DECK_PATH/repo..."
  rsync -avz --delete build/flatpak/repo/ "$STEAM_DECK_HOST:$STEAM_DECK_PATH/repo/"

  echo "Installing/updating user-scoped app on the Deck..."
  ssh_deck "$STEAM_DECK_HOST" bash -s -- "$STEAM_DECK_PATH" "$APP_ID" "$REMOTE_NAME" <<'EOSSH'
set -euo pipefail
repo_path="$1/repo"
app_id="$2"
remote_name="$3"
flatpak remote-add --user --no-gpg-verify --if-not-exists "$remote_name" "file://$repo_path"
flatpak remote-modify --user --no-gpg-verify --url="file://$repo_path" "$remote_name"
flatpak install --user --or-update --assumeyes "$remote_name" "$app_id"
installed_commit="$(flatpak info --user --show-commit "$app_id")"
echo "Installed user commit: $installed_commit"
# Stop a previously running system-scoped copy so it cannot be mistaken for the updated user build.
flatpak kill --system "$app_id" >/dev/null 2>&1 || true
nohup flatpak run --user "$app_id" >"$HOME/cinema-flatpak.log" 2>&1 </dev/null &
echo "Launched user installation; app log: $HOME/cinema-flatpak.log"
EOSSH
else
  echo "Creating and transferring Flatpak bundle..."
  flatpak build-bundle --runtime-repo=https://flathub.org/repo/flathub.flatpakrepo \
    build/flatpak/repo build/flatpak/Cinema.flatpak "$APP_ID" master
  scp_deck build/flatpak/Cinema.flatpak "$STEAM_DECK_HOST:$STEAM_DECK_PATH/Cinema.flatpak"

  echo "Installing/updating user-scoped app on the Deck..."
  ssh_deck "$STEAM_DECK_HOST" bash -s -- "$STEAM_DECK_PATH" "$APP_ID" <<'EOSSH'
set -euo pipefail
app_id="$2"
flatpak install --user --or-update --assumeyes "$1/Cinema.flatpak"
installed_commit="$(flatpak info --user --show-commit "$app_id")"
echo "Installed user commit: $installed_commit"
flatpak kill --system "$app_id" >/dev/null 2>&1 || true
nohup flatpak run --user "$app_id" >"$HOME/cinema-flatpak.log" 2>&1 </dev/null &
echo "Launched user installation; app log: $HOME/cinema-flatpak.log"
EOSSH
fi

echo "Deployment complete. Verify with: flatpak info --user --show-commit $APP_ID"
