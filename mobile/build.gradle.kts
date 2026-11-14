import java.util.Properties

/*
 * Cinema for phones and tablets.
 *
 * The network and parsing code is :core, shared with the TV and the desktop. This module is the phone's side of it:
 * MobilePlatform (files, preferences, Keystore secrets), touch screens in Jetpack Compose, the Media3 player with
 * Google Cast, offline copies, and a remote for Cinema on the TVs.
 *
 * It has its own application id, so it sits beside a TV build on the same account, and its own release asset
 * (cinema-mobile.apk), so the TV and phone never install each other's updates.
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/** The TV app's typefaces and icons, copied in at build time so they are not kept twice in git. */
abstract class SharedRes : DefaultTask() {
    @get:InputDirectory abstract val tvRes: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun copy() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val res = tvRes.get().asFile
        File(res, "font").listFiles()?.forEach { it.copyTo(File(out, "font/${it.name}")) }
        File(res, "drawable").listFiles { f -> f.name.startsWith("ic_cin_") || f.name in setOf("cinema_mark.xml", "ic_gear.xml", "ic_launcher.xml") }
            ?.forEach { it.copyTo(File(out, "drawable/${it.name}")) }
    }
}

val sharedRes = tasks.register<SharedRes>("sharedRes") {
    tvRes.set(rootProject.layout.projectDirectory.dir("app/src/main/res"))
    outputDir.set(layout.buildDirectory.dir("generated/sharedRes"))
}

androidComponents {
    onVariants { variant -> variant.sources.res?.addGeneratedSourceDirectory(sharedRes, SharedRes::outputDir) }
}

android {
    namespace = "com.danielportelabyrne.cinema.mobile"
    compileSdk = 36

    androidResources { noCompress += "ttf" }

    defaultConfig {
        applicationId = "com.danielportelabyrne.cinema.mobile"
        minSdk = 26
        targetSdk = 36
        // Same build number as the TV release it ships with (CI passes -Pcinema.versionCode=<run number>).
        val build = providers.gradleProperty("cinema.versionCode").orNull?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
        // Only a test build for an emulator exposes the player to adb (-Pcinema.testExported=true); never a release.
        manifestPlaceholders["playerExported"] = providers.gradleProperty("cinema.testExported").getOrElse("false")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-decoder:1.5.1")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    // Google Cast: the phone hands playback to a Chromecast or Google TV.
    implementation("androidx.media3:media3-cast:1.5.1")
    implementation("com.google.android.gms:play-services-cast-framework:22.0.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
