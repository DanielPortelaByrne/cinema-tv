import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/*
 * Cinema for Linux desktops and the Steam Deck.
 *
 * The network and parsing code is :core, shared with the Android TV app. This module is the desktop's side of it:
 * DesktopPlatform (folders, preferences, secrets), the screens (new, in Compose for Desktop) and the player (mpv).
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val linuxTarget = providers.gradleProperty("cinema.linux").orNull == "true"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    // -Pcinema.linux=true builds for Linux (the Flatpak) from any machine; otherwise for the machine building it.
    implementation(if (linuxTarget) compose.desktop.linux_x64 else compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    // Android ships org.json; the desktop needs the real thing.
    implementation("org.json:json:20231013")
    // Game controllers through SDL 2.30, which knows the Steam Deck's controls and Steam Input's virtual pad.
    implementation("com.badlogicgames.jamepad:jamepad:2.30.0.0")
    // JavaFX's WebView, only to pass Letterboxd's browser check (the TV uses a hidden Android WebView the same way).
    val fxPlatform = if (linuxTarget) "linux" else when {
        System.getProperty("os.name").startsWith("Windows") -> "win"
        System.getProperty("os.name").startsWith("Mac") -> "mac"
        else -> "linux"
    }
    listOf("base", "graphics", "controls", "web", "media", "swing").forEach { implementation("org.openjfx:javafx-$it:21.0.13:$fxPlatform") }

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

// The app's own typefaces, shared with the TV build.
tasks.processResources {
    from("../app/src/main/res/font") { into("fonts") }
    // ...and its icons and logo (Android vector drawables, which Compose for Desktop reads as they are).
    from("../app/src/main/res/drawable") { include("ic_cin_*.xml", "cinema_mark.xml", "ic_gear.xml"); into("icons") }
}

tasks.test { useJUnit() }

/**
 * The app as the Flatpak wants it: every jar in build/linux/lib (the launcher puts them all on the class path).
 * Run with -Pcinema.linux=true so the Linux build of Skia is the one packed.
 */
val linuxBundle by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Collects the app's jars for the Flatpak (use -Pcinema.linux=true)."
    doFirst { check(linuxTarget) { "Run with -Pcinema.linux=true" } }
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("linux/lib"))
}

/** Renders each screen as a PNG in build/screens from made-up data, to look at the layout without a server or a Deck. */
tasks.register<JavaExec>("deckScreens") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.danielportelabyrne.cinema.desktop.ScreenshotsKt")
    args(layout.buildDirectory.dir("screens").get().asFile.path)
    environment("CINEMA_HOME", layout.buildDirectory.dir("screens-home").get().asFile.path)
}

compose.desktop {
    application {
        mainClass = "com.danielportelabyrne.cinema.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m", "-Dsun.java2d.uiScale.enabled=true")
        nativeDistributions {
            // Only createDistributable is used: the Flatpak copies the app folder it makes (launcher + its own Java).
            targetFormats(TargetFormat.Deb)
            packageName = "cinema"
            packageVersion = "1.0.0"
            description = "Your Jellyfin library, made for the couch"
            vendor = "Daniel Portela Byrne"
            includeAllModules = true
        }
    }
}

/** Renders the player chrome's states as PNGs in build/chrome-preview, to lay over the TV's own screenshots. */
tasks.register<JavaExec>("chromePreview") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.danielportelabyrne.cinema.desktop.ChromePreviewKt")
    args(layout.buildDirectory.dir("chrome-preview").get().asFile.path)
}
