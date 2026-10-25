{
  description = "Cinema for Android TV";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f (pkgsFor system));
      pkgsFor =
        system:
        import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfreePredicate =
              pkg:
              builtins.elem (nixpkgs.lib.removePrefix "android-sdk-" (nixpkgs.lib.getName pkg)) [
                "build-tools"
                "cmake"
                "cmdline-tools"
                "platform-tools"
                "platforms"
                "tools"
              ];
          };
        };
    in
    {
      packages = forAllSystems (pkgs: rec {
        cinema-tv = pkgs.callPackage ./nix/package.nix { };
        default = cinema-tv;
      });

      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell {
          inputsFrom = [ self.packages.${pkgs.stdenv.hostPlatform.system}.cinema-tv ];
          packages = [ pkgs.android-tools pkgs.gh pkgs.libglvnd ];
          shellHook = ''
            export LD_LIBRARY_PATH="${pkgs.libglvnd}/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
          '';
        };

        flatpak = pkgs.mkShell {
          packages = with pkgs; [
            jdk17
            flatpak
            appstream
            fuse
            fuse3
            openssh
            gradle
            rsync
            patch
            coreutils
            sshpass
            gh
            lua
            libglvnd
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk17}"
            export LD_LIBRARY_PATH="${pkgs.libglvnd}/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            export FUSERMOUNT_DIR="${pkgs.fuse}/bin"
            export XDG_DATA_DIRS="/var/lib/flatpak/exports/share:/usr/share''${XDG_DATA_DIRS:+:$XDG_DATA_DIRS}"
            export STEAM_DECK_HOST="''${STEAM_DECK_HOST:-deck@steamdeck.local}"
            export STEAM_DECK_PATH="''${STEAM_DECK_PATH:-/home/deck/flatpaks}"
            echo "Cinema Flatpak build environment loaded"
          '';
        };
      });

      formatter = forAllSystems (pkgs: pkgs.nixfmt);
    };
}
