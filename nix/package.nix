{
  lib,
  stdenv,
  androidenv,
  gradle-packages,
  jdk17,
  writeText,
  # Optional: a JSON file, or an attrset turned into one. Baked into the APK.
  preset ? null,
}:
let
  # AGP 8.12 default.
  buildToolsVersion = "35.0.0";

  android = androidenv.composeAndroidPackages {
    platformVersions = [ "36" ];
    buildToolsVersions = [ buildToolsVersion ];
    includeEmulator = false;
    includeSystemImages = false;
    includeNDK = false;
  };
  sdk = "${android.androidsdk}/libexec/android-sdk";

  presetFile =
    if builtins.isAttrs preset && !lib.isDerivation preset then
      writeText "cinema-preset.json" (builtins.toJSON preset)
    else
      preset;

  # Matches the wrapper; AGP 8.12 breaks on Gradle 9.6+.
  gradle =
    (gradle-packages.mkGradle {
      version = "9.5.0";
      hash = "sha256-VTx49Q2vzVTWW5pERkkFeFdGnt+DZDE4lpVghTbWt0Y=";
      defaultJava = jdk17;
    }).wrapped;
in
stdenv.mkDerivation (finalAttrs: {
  pname = "cinema-tv";
  version = "1.0";

  src = lib.fileset.toSource {
    root = ./..;
    fileset = lib.fileset.unions [
      ../app
      ../core
      ../desktop
      ../build.gradle.kts
      ../settings.gradle.kts
      ../gradle.properties
    ];
  };

  nativeBuildInputs = [ gradle ];

  mitmCache = gradle.fetchDeps {
    pkg = finalAttrs.finalPackage;
    data = ./deps.json;
  };

  env = {
    ANDROID_HOME = sdk;
    ANDROID_SDK_ROOT = sdk;
    JAVA_HOME = jdk17.home;
  };

  preBuild = ''
    export ANDROID_USER_HOME=$(mktemp -d)
  '';

  gradleBuildTask = ":app:assembleRelease";
  # nixDownloadDeps fails on Android variant matching.
  gradleUpdateTask = finalAttrs.gradleBuildTask;
  gradleFlags = [
    "-Dorg.gradle.java.home=${jdk17.home}"
    # Maven's aapt2 is dynamically linked and fails on NixOS.
    "-Pandroid.aapt2FromMavenOverride=${sdk}/build-tools/${buildToolsVersion}/aapt2"
    # Release lint checks for newer library versions against Google's version lists, which change with every
    # release, so their hashes in deps.json would keep breaking. Lint still runs in normal Gradle builds.
    "--exclude-task=lintVitalRelease"
  ]
  ++ lib.optional (presetFile != null) "-Pcinema.preset=${presetFile}";

  installPhase = ''
    runHook preInstall
    install -Dm644 app/build/outputs/apk/release/app-release-unsigned.apk \
      $out/cinema-tv-unsigned.apk
    runHook postInstall
  '';

  meta = {
    description = "Jellyfin, Seerr and streaming catalogue app for Android TV";
    homepage = "https://github.com/DanielPortelaByrne/cinema-tv";
    license = lib.licenses.mit;
    platforms = [ "x86_64-linux" ];
  };
})
