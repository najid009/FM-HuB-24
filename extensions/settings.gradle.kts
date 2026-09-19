// FMHub24 extensions — the ".cs3" side of the plugin system
//
// This is a *standalone* Gradle project (not included in android-app/settings.gradle.kts):
// an extension is compiled against the host API but must never bundle a second copy of it,
// so it is built outside the app build to keep the two classpaths separate.
//
// Build:   ./gradlew :fmhub-archive:make            -> fmhub-archive/build/fmhub-archive.cs3
// Publish: python3 tools/make_repo.py --help
rootProject.name = "fmhub24-extensions"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        // plugin-api comes from: cd android-app && ./gradlew :plugin-api:publishToMavenLocal
        mavenLocal()
    }
}

include(":fmhub-archive")

// Vendored community providers (see VENDORED.md). Each is a plain CloudStream extension module, so
// `./gradlew make` builds them all and tools/make_repo.py picks the .cs3 files up automatically.
include(":BanglaPlex")
include(":Cinefreak")
include(":HDhub4u")
include(":MovieBoxProvider")
include(":AllMovieLandProvider")
