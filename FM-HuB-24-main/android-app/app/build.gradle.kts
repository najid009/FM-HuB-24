import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
}

// ---------------------------------------------------------------------------
// Plugin/extension API pin — kept identical to :plugin-api (single source of truth:
// android-app/gradle.properties).
// ---------------------------------------------------------------------------
val rootProperties = Properties().apply {
    val f = rootProject.file("gradle.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String, fallback: String): String =
    (project.findProperty(name) as String?) ?: System.getenv(name) ?: rootProperties.getProperty(name) ?: fallback

val cloudstreamVersion = prop("CLOUDSTREAM_VERSION", "v4.8.0")
val pluginApiVersion = prop("PLUGIN_API_VERSION", "1")

android {
    namespace = "com.fmhub24.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fmhub24.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Supabase config - MUST come from local.properties or env, never hardcoded.
        val supabaseUrl: String = (project.findProperty("SUPABASE_URL") as String?)
            ?: System.getenv("SUPABASE_URL")
            ?: ""
        val supabaseKey: String = (project.findProperty("SUPABASE_ANON_KEY") as String?)
            ?: System.getenv("SUPABASE_ANON_KEY")
            ?: ""

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
        // Shown in Settings and used to explain "extension was built for another API".
        buildConfigField("String", "CLOUDSTREAM_VERSION", "\"$cloudstreamVersion\"")
        buildConfigField("int", "PLUGIN_API_VERSION", pluginApiVersion)
    }

    buildTypes {
        release {
            // Keep shrinking OFF until the plugin matrix is verified: R8 renaming host
            // classes silently breaks every .cs3 (see proguard-rules.pro for the keeps
            // that make it safe to re-enable).
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Everything CloudStream's own app module does to stay loadable on old devices, copied from
        // its build file (v4.8 sets `isCoreLibraryDesugaringEnabled = true` and pulls the `_nio`
        // flavor of the desugaring library, "NIO Flavor Needed for NewPipeExtractor").
        //
        // This is not optional for a plugin host: jackson, kotlinx-datetime, ktor and NiceHttp all
        // reach for `java.time` / `java.nio.file`, which do not exist before API 26. Those references
        // compile happily against compileSdk 36 and then throw `NoClassDefFoundError` the first time a
        // provider is touched — a crash on Android 7/8 that no build step and no code review can see.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    lint {
        // Only NewApi runs, and it is fatal. With compileSdk 36 on minSdk 24, a call to an API the
        // device does not have compiles perfectly and then throws NoSuchMethodError the moment a
        // screen opens — i.e. "the app closes again", with nothing in the UI to explain it. Lint's
        // API-level check is the only thing that sees that at build time, so it fails the build
        // (`:app:lintDebug` runs it in CI, and `lintVitalRelease` covers assembleRelease).
        // Everything else is deliberately off: library-owned resource warnings are not ours to fix
        // and would only make this step ignorable.
        checkOnly += "NewApi"
        fatal += "NewApi"
        textReport = true
        abortOnError = true
    }
}

// Kotlin 2.2+ turned the old `android { kotlinOptions { … } }` DSL into a hard error, so the
// compiler options live on the `kotlin` extension now. Keep this in sync with :plugin-api.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
        )
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    // ------------------------------------------------------------------ plugin system
    // The contract + the real CloudStream library it re-exports (MainAPI, APIHolder,
    // BasePlugin, LoadResponse/SearchResponse/ExtractorApi/ExtractorLink, `app`, …).
    // Everything a .cs3 dex resolves from the host comes from here.
    implementation(project(":plugin-api"))

    // Direct deps that plugin bytecode touches; versions MUST match the library, since the
    // classes are shared through the host classloader (a second copy inside the dex is not
    // how extensions are built).
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("com.github.Blatzar:NiceHttp:0.4.18")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("io.ktor:ktor-http:3.5.0")
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")

    // ------------------------------------------------------------------ core
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.9.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-compiler:2.57.2")
    // Both Hilt's and Room's kapt processor read the Kotlin metadata out of every annotated class
    // through kotlin-metadata-jvm, and the copy they pull in transitively only understands up to
    // 2.2 — which aborts :app:kaptDebugKotlin with "[Hilt] Provided Metadata instance has version
    // 2.3.0, while maximum supported version is 2.2.0". Gradle picks the newest version on the
    // classpath, so pinning the reader to this project's Kotlin version is the whole fix (it is a
    // processor-only dependency: nothing here ends up in the APK).
    kapt("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil:2.7.0")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")

    // Media3 version is aligned with the CloudStream library so ExtractorLink handling and
    // the player agree on one media3 build.
    // Same version CloudStream v4.8 pins; the `_nio` variant is what backs `java.nio.file`
    // (Path/Files) as well as `java.time` on API 24-25.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.3")
    implementation("androidx.media3:media3-exoplayer-dash:1.9.3")
    implementation("androidx.media3:media3-ui:1.9.3")
    implementation("androidx.media3:media3-common:1.9.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.preference:preference-ktx:1.2.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
