pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Cinema"
include(":core")
// -Pcinema.desktopOnly=true leaves the Android app out, for machines without the Android SDK.
if (providers.gradleProperty("cinema.desktopOnly").orNull != "true") { include(":app"); include(":mobile") }
include(":desktop")
