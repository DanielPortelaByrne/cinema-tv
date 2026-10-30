#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# AppStream's Glycin image loader needs a Flatpak system repository for its sandbox.
# On Nix/developer setups with only a per-user installation, use that repo as the system dir.
if [[ -z "${FLATPAK_SYSTEM_DIR:-}" && ! -d /var/lib/flatpak/repo && -d "$HOME/.local/share/flatpak/repo" ]]; then
  export FLATPAK_SYSTEM_DIR="$HOME/.local/share/flatpak"
fi

./gradlew :desktop:test :desktop:linuxBundle -Pcinema.linux=true -Pcinema.desktopOnly=true --console=plain
mkdir -p build/flatpak
flatpak run org.flatpak.Builder --user --force-clean \
  --repo=build/flatpak/repo --state-dir=build/flatpak/state \
  build/flatpak/app flatpak/io.github.danielportelabyrne.Cinema.yml
flatpak build-bundle --runtime-repo=https://flathub.org/repo/flathub.flatpakrepo \
  build/flatpak/repo build/flatpak/Cinema.flatpak io.github.danielportelabyrne.Cinema master
echo "Created build/flatpak/Cinema.flatpak"
