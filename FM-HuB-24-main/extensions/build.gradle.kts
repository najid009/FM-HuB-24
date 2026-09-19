// Root build for FMHub24 extensions.
//
// Every extension module is an Android *library* that CloudStream's gradle plugin turns into a
// `.cs3` (classes.dex + manifest.json in a zip). Using that plugin is what guarantees the
// package matches what the host loader expects — do not hand-roll the zip.
//
// Shared dependency/compiler config lives here; per-module identity (description, authors,
// tvTypes, …) lives in the module's own `cloudstream { }` block.
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // Aligned with android-app/build.gradle.kts on purpose: the host's Kotlin runtime must be
        // at least as new as the stdlib this dex was compiled against, otherwise the provider links
        // and then dies with NoSuchMethodError on a stdlib call.
        classpath("com.android.tools.build:gradle:8.13.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
        // Provides `make`, `compileDex`, `generateManifest`, `makePluginsJson`, `deployWithAdb`.
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
    }
}

// Mirrors android-app/gradle.properties — keep in sync.
val cloudstreamVersion: String = providers.gradleProperty("CLOUDSTREAM_VERSION").getOrElse("v4.8.0")
val pluginApiVersion: String = providers.gradleProperty("PLUGIN_API_VERSION").getOrElse("1")

// The gradle plugin writes `project.version` into manifest.json and plugins.json, and it must be
// an int ("Project version must be an int"). This is the *extension's* version — bump it when the
// provider changes, not when the API does (pluginApiVersion above is a different number).
val extensionVersion: String = providers.gradleProperty("EXTENSION_VERSION").getOrElse("1")

subprojects {
    version = extensionVersion

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            // Null assertions bloat the dex and hide real scrape errors behind NPEs.
            freeCompilerArgs.addAll(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions",
            )
        }
    }

    // These dependencies have to be registered *after* the module applied its plugins: a
    // `subprojects {}` block runs before the subproject's own build script, and until
    // com.android.library + org.jetbrains.kotlin.android are on the project there is no
    // `compileOnly`/`implementation` configuration to add anything to (Gradle fails the whole
    // build with "Configuration with name 'compileOnly' not found").
    plugins.withId("org.jetbrains.kotlin.android") {
        val deps = dependencies

        // THE contract. `compileOnly` on purpose: these classes come from the host at runtime.
        // Packaging them instead would put a second, incompatible MainAPI inside the dex —
        // exactly the bug that produced "none produced a MainAPI provider".
        deps.add("compileOnly", "com.fmhub24.pluginapi:plugin-api:$pluginApiVersion-$cloudstreamVersion")
        deps.add("compileOnly", "com.github.recloudstream.cloudstream:library:$cloudstreamVersion")

        // Also host-provided, needed only to compile against.
        deps.add("compileOnly", "org.jsoup:jsoup:1.22.1")
        deps.add("compileOnly", "com.github.Blatzar:NiceHttp:0.4.18")
        deps.add("compileOnly", "com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

        // Community providers (HDhub4u, …) annotate their DTOs with `@SerializedName`. Gson is not a
        // CloudStream dependency — our *app* pulls it in for Retrofit — so it is only needed to
        // compile against, and must never be packed into the dex.
        deps.add("compileOnly", "com.google.code.gson:gson:2.11.0")
        deps.add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

        // Kotlin's own stdlib: the host ships it, and its version is pinned to this project's
        // (see the buildscript block above), so the dex can rely on the parent loader for it.
        deps.add("compileOnly", "org.jetbrains.kotlin:kotlin-stdlib")
    }
}
