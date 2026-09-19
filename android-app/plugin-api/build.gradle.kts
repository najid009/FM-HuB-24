plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    `maven-publish`
}

// ---------------------------------------------------------------------------
// FMHub24 plugin API — the single contract shared by
//   1. the host app (:app, `implementation` -> classes are inside the APK/classloader)
//   2. FMHub's own extensions (../extensions, `compileOnly` -> resolved from the host)
//
// It deliberately does NOT redefine MainAPI/LoadResponse/... : those classes come from
// the real CloudStream `library` artifact and are re-exported here via `api(...)`.
// Redefining them would produce duplicate classes and, worse, a class the .cs3 dex
// cannot link against.
// ---------------------------------------------------------------------------

val cloudstreamVersion: String = (project.findProperty("CLOUDSTREAM_VERSION") as String?)
    ?: "v4.8.0"
val pluginApiVersion: String = (project.findProperty("PLUGIN_API_VERSION") as String?) ?: "1"
val pluginApiGroup: String = (project.findProperty("PLUGIN_API_GROUP") as String?)
    ?: "com.fmhub24.pluginapi"
val pluginApiArtifact: String = (project.findProperty("PLUGIN_API_ARTIFACT") as String?)
    ?: "plugin-api"

android {
    namespace = "com.fmhub.plugin.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        // Exposed so the host can print "app built for plugin API n / CloudStream vX".
        buildConfigField("int", "PLUGIN_API_VERSION", pluginApiVersion)
        buildConfigField("String", "CLOUDSTREAM_VERSION", "\"$cloudstreamVersion\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    lint {
        abortOnError = false
    }
}

// Kotlin 2.2+ removed `android { kotlinOptions }` (it is an error, not a warning): extensions and
// the host must agree on 17, which is also what the CloudStream library artifact is built with.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // THE plugin API — CloudStream's host-facing library (MainAPI, APIHolder, BasePlugin,
    // LoadResponse/SearchResponse/ExtractorApi/ExtractorLink, app/Requests, WebViewResolver).
    api("com.github.recloudstream.cloudstream:library:$cloudstreamVersion")

    // Things the library declares as `implementation` but that extension bytecode resolves
    // from the host classloader at runtime. They must be on the host classpath with the same
    // versions, otherwise every provider dies linking with NoClassDefFoundError.
    api("org.jsoup:jsoup:1.22.1")
    api("com.github.Blatzar:NiceHttp:0.4.18")
    // NiceHttp exposes these as `api`, but being explicit keeps the version the host and the
    // library agree on — a mismatch shows up as NoSuchMethodError inside a provider, not at build.
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // Used by com.lagradost.cloudstream3.utils.AtomicList / unixTime helpers in the library.
    api("org.jetbrains.kotlinx:atomicfu:0.33.0")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    api("io.ktor:ktor-http:3.5.0")
    api("org.mozilla:rhino:1.8.1")
    api("androidx.preference:preference-ktx:1.2.1")
    api("androidx.core:core-ktx:1.17.0")

    // Host-only classes that CloudStream keeps in its *app* module but that extensions
    // still reference (com.lagradost.cloudstream3.utils.DataStore). Implemented in this
    // module's sources, so both sides agree on them.
}

publishing {
    publications {
        register<MavenPublication>("pluginApi") {
            groupId = pluginApiGroup
            artifactId = pluginApiArtifact
            version = "$pluginApiVersion-$cloudstreamVersion"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
