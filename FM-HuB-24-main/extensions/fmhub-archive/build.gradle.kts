// Example FMHub extension: Internet Archive (archive.org) — public-domain media, so it is a
// safe reference implementation to copy when wiring a real site.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.lagradost.cloudstream3.gradle")
}

android {
    namespace = "com.fmhub24.extensions.archive"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Extensions have no resources unless `requiresResources` is set — a resource-free package
    // is what keeps a .cs3 small and loadable on every device.
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

cloudstream {
    // Consumed by the gradle plugin when writing repo metadata, and by tools/make_repo.py.
    description = "Public domain movies and shows from the Internet Archive"
    authors = listOf("FMHub24")
    status = 0 // 0 = ok, 1 = down, 2 = error, 3 = hidden
    language = "en"
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://archive.org/favicon.ico"
    requiresResources = false
}
