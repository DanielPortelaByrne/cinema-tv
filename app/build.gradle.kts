import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from keystore.properties (never committed). Without it, release builds are unsigned.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Optional setup baked into the APK. See README, "Building with a preset".
val presetPath = providers.gradleProperty("cinema.preset").orElse(providers.environmentVariable("CINEMA_PRESET"))

abstract class PresetAsset : DefaultTask() {
    @get:InputFile @get:Optional abstract val preset: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun copy() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val file = preset.orNull?.asFile ?: return
        val parsed = try { JsonSlurper().parse(file) } catch (e: Exception) { throw GradleException("Preset $file is not valid JSON: ${e.message}") }
        val keys = (parsed as? Map<*, *>)?.keys ?: throw GradleException("Preset $file must be a JSON object")
        val unknown = keys.map { it.toString() } - KNOWN_KEYS
        if (unknown.isNotEmpty()) throw GradleException("Preset $file has unknown keys: $unknown. Known: $KNOWN_KEYS")
        file.copyTo(File(out, "cinema-preset.json"))
    }

    private companion object {
        val KNOWN_KEYS = setOf("jellyfin", "seerr", "sonarr", "radarr", "qbittorrent", "transmission", "deluge",
            "region", "requestHelperUrl", "streamServiceUrl", "music")
    }
}

val presetAsset = tasks.register<PresetAsset>("presetAsset") {
    preset.set(presetPath.map { rootProject.layout.projectDirectory.file(it) })
    outputDir.set(layout.buildDirectory.dir("generated/preset"))
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(presetAsset, PresetAsset::outputDir) }
}

android {
    namespace = "com.danielportelabyrne.cinema"
    compileSdk = 36

    androidResources { noCompress += "ttf" }

    defaultConfig {
        applicationId = "com.danielportelabyrne.cinema"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
        debug {
            // CI installs its build on a real TV next to the owner's own Cinema: -Pcinema.appIdSuffix=.ci
            providers.gradleProperty("cinema.appIdSuffix").orNull?.let { applicationIdSuffix = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.3")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Live channels and Jellyfin's converted films are HLS streams; without this module the player crashes on them.
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")   // some free channels are DASH (.mpd) streams
    // FFmpeg audio decoding (Dolby Digital Plus, DTS, TrueHD...), which the TV cannot decode itself. Jellyfin publishes the
    // Media3 extension prebuilt; its 1.5.0 build works with the 1.5.1 player. With it, films keep their original file, so
    // streams stay seekable instead of being converted on the fly.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    implementation("androidx.media3:media3-decoder:1.5.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json so addon-response parsing can be unit tested (the Android stubs return empty values).
    testImplementation("org.json:json:20231013")
}
