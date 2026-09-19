// Top-level build file.
//
// TOOLCHAIN NOTE (important — this is what "path A" costs):
// The host compiles against the real CloudStream library artifact
// (com.github.recloudstream.cloudstream:library). That artifact is published with
// Kotlin 2.3.x, and Kotlin cannot read metadata newer than its own compiler, so the host
// toolchain has to be >= the version the library was built with.
// Versions below are aligned with CloudStream v4.8.0's gradle/libs.versions.toml.
//
// Single source of truth for the pinned extension API:
//   gradle.properties -> CLOUDSTREAM_VERSION
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0" apply false
    // kapt (not ksp) on purpose: it ships with the Kotlin plugin, so there is no extra
    // KSP<->Kotlin version pair to keep in sync while building the app on CI.
    id("org.jetbrains.kotlin.kapt") version "2.3.0" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
