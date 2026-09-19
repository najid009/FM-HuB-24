// Vendored community provider — see extensions/VENDORED.md for what was changed and why.
// GPL-3.0 (CloudStream-derived). Upstream: phisher98/cloudstream-extensions-phisher.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.lagradost.cloudstream3.gradle")
}

// The gradle plugin writes this into manifest.json / plugins.json as the extension version, and it has
// to be an int. Upstream's own number is kept so an update here is comparable with the community repo.
version = 5

android {
    namespace = "com.fmhub24.extensions.banglaplex"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            resources.setSrcDirs(emptyList<String>())
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// `name` is not set: the gradle plugin takes it from the module name, and the module is named after the
// provider on purpose. `status` follows CloudStream (0 ok, 1 down, 2 slow/error, 3 hidden).
cloudstream {
    description = "BanglaPlex — Bangla movies & series"
    authors = listOf("phisher98", "FMHub24")
    status = 0
    language = "bn"
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=banglaplex.click"
    requiresResources = false
}
