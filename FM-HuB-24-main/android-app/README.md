# FMHuB24 — Android Streaming App

FMHuB24 User App - Kotlin + Jetpack Compose streaming app that loads real content from Cloudstream .cs3 extensions via Supabase.

## Identity
- App Name: FMHuB24
- Package: `com.fmhub24.app`
- Min SDK 24, Target SDK 35, compileSdk 36 (AGP 8.13, Kotlin 2.3.0 — see the toolchain note below)
- Kotlin only, Jetpack Compose Material 3, Dark theme default
- Icon: Letter-mark "FM" + "24" subscript, orange `#FF6B35` / cyan `#00D4FF` gradient

## Backend: Supabase Read-Only
- Same project as Admin Panel
- Reads `extensions` table where `status='active'` (RLS)
- Uses `local.properties` or BuildConfig (never hardcoded):
  ```
  SUPABASE_URL=https://your-project.supabase.co
  SUPABASE_ANON_KEY=your-anon-key-here
  ```
- No write operations - only read

## Modules
| module | what it is |
| --- | --- |
| `:app` | the host: UI, Supabase sync, downloader, plugin loader |
| `:plugin-api` | the extension contract — `api()`-re-exports the real CloudStream library and adds `FMHubBasePlugin`, `FMHubProvider`, `FMHubApi.API_VERSION`, plus the host-side `com.lagradost.cloudstream3.utils.DataStore`. Published to mavenLocal as `com.fmhub24.pluginapi:plugin-api:<api>-<cloudstream>` for `../extensions` to compile against |

`:app` depends on `:plugin-api` with `implementation`, so the classes a plugin resolves from
`context.classLoader` are the exact ones the extension was compiled against.

## Tech Stack
- MVVM + Repository
- Hilt DI
- OkHttp + Retrofit (Supabase REST + .cs3 download)
- Coil (images)
- Room (favorites, watch progress, cached extensions)
- Media3 ExoPlayer 1.9.3 (HLS + DASH + MP4 — same version the CloudStream library pins)
- Coroutines + Flow
- Navigation Compose
- `PathClassLoader` for `.cs3` runtime loading, against `com.github.recloudstream.cloudstream:library:v4.8.0`

## Extension Loading - Core Functionality
1. Splash fetches active extensions from Supabase (`name`, `file_url`, `version`, `file_hash`, `plugin_class_name`, `api_version`, `requires_resources`, `source_repo_url`)
2. Downloads each `.cs3` to `files/extensions/`, **verifying SHA-256** against `file_hash` when the row has one; a cached file is reused only when its version/size/hash agree (`hardenCachedFiles()` re-checks files written by older builds)
3. `setReadOnly()` on the file (Android 14 refuses writable dex files), then one `PathClassLoader(file, context.classLoader)` per package
4. `manifest.json` is read through that loader; `fmhubApiVersion`, when present, is compared with `FMHubApi.API_VERSION` — a mismatch is reported as *"built for plugin API v2 but this host is v1"* instead of a silent empty load
5. The `pluginClassName` class is instantiated (no-arg) and `BasePlugin.load()` runs; providers = the diff of `APIHolder.allProviders`, extractors = the diff of `utils.extractorApis`, both attributed to this file
6. `getMainPage()` fills Home rows, `search()` fans out over providers with `Semaphore(8)`, `load()`/`loadLinks()` produce real links — extractor sub-links (`extractorData`) are resolved by the matching `ExtractorApi` before playback
7. Every failure above is kept per file and shown on Splash and in Settings → Extensions, with a copyable reason

**Why there are no local CloudStream classes any more:** plugin bytecode references
`com.lagradost.cloudstream3.MainAPI` **by name**. A host-side re-declaration creates a second,
incompatible `MainAPI`, and the plugin then fails to link — that was the whole "none produced a
MainAPI provider" bug. `app/src/main/java/com/fmhub24/app/plugins/cloudstream/Aliases.kt` therefore
contains only `typealias`es to the library (kept so existing view models did not need rewriting), and
the only real class we add in that package is the `DataStore` shim inside `:plugin-api` (CloudStream
keeps `DataStore` in its app module, extensions still reference it).

**Empty state:** If no extensions in Supabase, Home shows "No content available" - no dummy posters.

**Empty state:** If no extensions in Supabase, Home shows "No content available" - no dummy posters.

## Screens
- Splash: logo + fetch extensions
- Home: real sections from `getMainPage()`
- Search: real-time search across providers
- Details: real `load()` response, Play + Favorite
- Player: Media3, real `loadLinks()` streams, resume via Room
- Favorites: Room saved list
- Settings: theme, playback prefs, version

## Data Layer
```kotlin
@Entity data class CachedExtension(
  @PrimaryKey val id: String,
  val name: String,
  val version: Int,
  val localFilePath: String,
  val loadedAt: Long
)
```
Plus `Favorite` and `WatchProgress`.

## Build

```bash
cd android-app
./gradlew :app:assembleDebug                      # host
./gradlew :plugin-api:publishToMavenLocal          # only needed when building ../extensions
```

The CloudStream library + jsoup + NiceHttp come from **JitPack**, so `settings.gradle.kts` needs
`maven("https://jitpack.io")` (it has it). `isMinifyEnabled = false` for both build types until the
keep-rule set in `app/proguard-rules.pro` has been verified on a device — R8 shrinking the library
types a plugin links against breaks loading in ways that look identical to a bad extension.

1. Copy `local.properties.example` to `local.properties` (same folder).
2. Fill in **both**:
   - `sdk.dir=/path/to/Android/Sdk` (your Android SDK location)
   - `SUPABASE_URL` and `SUPABASE_ANON_KEY` — **the exact same Supabase project the Admin Panel uploads to** (Supabase Dashboard → Project Settings → API).
3. Open in Android Studio (Hedgehog+), Sync Gradle, Run.

> **The Supabase values are baked into the APK at build time (BuildConfig).**
> If you change `local.properties`, you MUST rebuild — an old APK still points at the old/placeholder URL.

### Toolchain notes (before you bump Kotlin, AGP or Hilt)

Each of these cost a red CI run, and every one of them fails *silently-looking* in the build file
rather than in the sources:

- **Kotlin ≥ the library's Kotlin.** `:app` and `:plugin-api` must compile with a Kotlin at least as
  new as the one `com.github.recloudstream.cloudstream:library` was published with, or the compiler
  refuses to read its metadata. That is why this project sits on 2.3.x.
- **`kotlin { compilerOptions { … } }`, not `android { kotlinOptions { … } }`.** Since Kotlin 2.2 the
  old block is an error in a `.gradle.kts` script, and it takes down `:app` *and*
  `:plugin-api:publishToMavenLocal` (i.e. every extension build) at once.
- **`kapt("org.jetbrains.kotlin:kotlin-metadata-jvm:<kotlin>")`** in `app/build.gradle.kts`. Hilt's and
  Room's kapt processors read Kotlin metadata through that library with their own pinned copy; if it
  is older than this project's compiler, `:app:kaptDebugKotlin` dies with
  `[Hilt] Provided Metadata instance has version X, while maximum supported version is Y`. Bumping
  the pin alongside `kotlinVersion` is the fix; moving to KSP also works but adds a second version pair
  to keep in sync.
- **`:plugin-api` must be published before `../extensions` builds**:
  `./gradlew :plugin-api:publishToMavenLocal` (the extensions resolve it from `mavenLocal()`).

## Troubleshooting: "Unable to resolve host ...supabase.co" / nothing loads
This means the installed APK was built without your real Supabase credentials.
1. Confirm `android-app/local.properties` has the real `SUPABASE_URL` + `SUPABASE_ANON_KEY`
   (they must match the Admin Panel's `.env`).
2. **Rebuild and reinstall** the app (Build → Rebuild Project, then Run).
3. Splash should then download the `.cs3` extensions and load their providers.

If splash shows "Supabase not configured", the keys are still missing/placeholder in `local.properties`.

## Troubleshooting: "the app opens and closes immediately"

The app now refuses to fail silently. Two things happen when anything throws on the way up:

1. `CrashLog` (installed from `FMHub24App.attachBaseContext`, i.e. before ContentProvider
   initializers run) writes the full trace —
   with the app version, `CloudStream`/`pluginApi` versions, Android version and device — to
   `files/crash.log` and a second copy to `Android/data/com.fmhub24.app/files/crash.log` (a file
   manager can read the second one without a cable), and to `logcat` under `FMHubCrash`.
2. The handler also starts `CrashReportActivity` — a plain-View screen that runs in its **own
   process** (`:crashreport`), because the crashing process cannot host a dialog about itself. It shows
   the trace with **Copy**, **Share** (send it as text) and **Clear & retry**.
3. If Android refuses that (a background activity start, i.e. the crash happened before any window
   existed), the log is still on disk and the splash screen shows it on the *next* launch with a Copy
   button — and `MainActivity` wraps `super.onCreate()` plus the whole Compose tree, so a failure in
   Hilt's graph or the UI draws the trace in framework TextViews. Nothing about the reporting depends
   on the thing that broke.
4. **Safe mode**: two recorded crashes inside ten minutes make `PluginManager.loadExtensions` skip all
   `.cs3` files with a readable reason instead of loading the dex that is killing the app. The app then
   stays open so the log can be read; "Clear & retry" on the crash screen turns safe mode off.

Reading the log without the app:

```bash
adb logcat -b crash -d
adb logcat -s FMHubCrash -d
adb shell run-as com.fmhub24.app cat files/crash.log          # internal copy
# and on the phone itself, with any file manager:
#   Android/data/com.fmhub24.app/files/crash.log
#   Download/FMHub24-crash.txt        (MediaStore copy, API 29+ - no file manager hacks needed)
```

So the report to send is: the copied trace + the Android version. With a cable it is one command:

```bash
adb logcat -b crash -d                                   # or the file itself:
adb shell run-as com.fmhub24.app cat files/crash.log
```

Nothing is swallowed: the trace is recorded *and* the process still crashes, so Android's own
reporting and logcat stay intact. `:app:lintDebug` in CI additionally fails the build on any call to
an API newer than `minSdk 24` (the one class of bug that compiles, reviews clean, and then dies on
old devices), and `compileOptions.isCoreLibraryDesugaringEnabled` ships the `java.time`/`java.nio.file`
back-ports the CloudStream libraries we link against need on Android 7/8.

## Troubleshooting: extension load failures

Splash and **Settings → Extensions** now name the failing file and the reason, and the same lines go
to logcat:

```bash
adb logcat -c && adb logcat | grep -Ei "FMHubPlugin|dex|NoClassDef|SecurityException"
```

| what you see | what it means |
| --- | --- |
| `Not a zip container` | the cached file is an HTML error page — bad/private `file_url` |
| `No classes.dex inside the container` | wrong artifact (jar/AAR); build with `:module:make` in `../extensions` |
| `classes.dex is compressed` | repack with `extensions/tools/make_repo.py` (ART cannot mmap a deflated dex) |
| `host is missing com.foo.Bar` | the plugin needs a class the host does not expose; add it as `api` in `:plugin-api` or rebuild the plugin |
| `Built for FMHub plugin API vN but this host is v1` | version gate doing its job |
| `java.lang.VerifyError: Verifier rejected class …PluginLoader` on open | a `BasePlugin`-typed local was narrowed to `MainAPI` (unrelated types) — kotlinc emits one register two ways and ART rejects the whole class | keep one type per local: cast once into a `val` of each type, never narrow a `var` into an unrelated type and back |
| crash on open, `NoClassDefFoundError: java.time...` | desugaring disabled or a plugin built for a newer JVM: rebuild the host with `isCoreLibraryDesugaringEnabled` (it is on) |

Full walkthrough (writing an extension, packaging, publishing a repo, the complete failure
catalogue): [`../docs/PLUGINS.md`](../docs/PLUGINS.md).

## Permissions
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## No Features Here
- No upload/edit/delete UI (Admin Panel only)
- No fake/mock content
- No Supabase write

## Deliverable Checklist
- [x] Supabase real active extensions fetch (with hash verification of downloads)
- [x] Real content through the real CloudStream API (`PathClassLoader` + `com.lagradost.cloudstream3.*`)
- [x] Extractor resolution for `extractorData` links, Media3 playback with the extractor's headers/referer
- [x] Per-file load diagnostics (Splash + Settings), user-level enable/disable of providers
- [x] Room favorites & progress (`cached_extensions` v2 migration for the new metadata)
- [x] MVVM + Hilt, no hardcoded dummy data
- [x] `:app:assembleDebug` + `:app:assembleRelease` and `:plugin-api:publishToMavenLocal` compile in CI
      (`.github/workflows/android-build.yml`) — the APK artifacts on the run are the proof
- [x] `extensions/fmhub-archive` builds a `.cs3` in CI, and the workflow itself asserts the package
      shape (STORED `classes.dex`, `pluginClassName`, `fmhubApiVersion = 1`) before uploading it
