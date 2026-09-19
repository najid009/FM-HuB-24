pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // CloudStream's own gradle plugin (`com.lagradost.cloudstream3.gradle`) +
        // the published `library` artifact live on JitPack.
        maven("https://jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        // Lets `:app` compile against a locally published plugin-api
        // (`./gradlew :plugin-api:publishToMavenLocal`) — used by the extensions project.
        mavenLocal()
    }
}

rootProject.name = "FMHuB24"

// :plugin-api  -> the plugin contract (re-exports CloudStream's real API + FMHub helpers)
// :app         -> the host app that loads .cs3 dex files
include(":plugin-api")
include(":app")
