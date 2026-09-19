# FM-HuB 24 — plugins, the whole story

Everything about how a `.cs3` gets from a Kotlin source file to a working row on the user app's
home screen: what the host must provide, how the API is published, how to write an extension, how
to package it, and how to read a failure.

Read this if you hit **"Failed to load any extension. N file(s) found but none produced a MainAPI
provider"** — that message used to be the *only* thing the app said, and it was always one of the
cases in [Debugging](#debugging).

---

## 1. How the pieces fit

```
   ┌──────────────────────────── android-app ────────────────────────────┐
   │  :app   host: downloads .cs3 → loads dex → shows content           │
   │           │  implementation                                          │
   │           ▼                                                         │
   │  :plugin-api  com.fmhub24.pluginapi:plugin-api                      │
   │           │  api("com.github.recloudstream.cloudstream:library:v4.8.0")
   │           ▼                                                         │
   │      real CloudStream library  →  MainAPI, APIHolder, BasePlugin,   │
   │      ExtractorApi, app/Requests, LoadResponse, …                    │
   └─────────────────────────────────────────────────────────────────────┘
                    ▲  compileOnly (classes come from the host at runtime)
                    │
   ┌───────────────┴────────────── extensions ──────────────────┐
   │  :fmhub-archive → ./gradlew make → build/fmhub-archive.cs3 │
   │  (zip: classes.dex + manifest.json)                        │
   └──────────────────────────────┬─────────────────────────────┘
                                  │ published as a repo
             Supabase  ◄──────────┴──────►  GitHub `builds` branch
      extensions + extension_repos            repo.json + plugins.json + *.cs3
```

**Path A** — the host uses the *real* CloudStream library as the extension API. This is not a
detail, it is the requirement: a compiled `.cs3` contains bytecode that references
`com.lagradost.cloudstream3.MainAPI` **by name**. If the host declares its own `MainAPI` in a
different package, or re-declares `com.lagradost.cloudstream3.MainAPI` as a stub, the plugin's class
cannot link and you get exactly the reported bug. So the app never re-declares library types — the
`app/src/main/java/com/fmhub24/app/plugins/cloudstream/Aliases.kt` file only contains
`typealias`es pointing at the library.

**Path B** — FMHub's own contract on top: `:plugin-api` adds `FMHubBasePlugin`, `FMHubProvider`,
the `FMHubApi` version marker, and the `DataStore` host shim, and publishes it to mavenLocal as
`com.fmhub24.pluginapi:plugin-api:<api>-<cloudstream>`. Extensions compile against *that* single
artifact and get the library transitively, so there is exactly one way to be compatible.

---

## 2. What a host must provide (the four things people forget)

> **Where the host touches CloudStream matters.** Both items below are *library* calls, and a host that
> makes them from `Application.attachBaseContext`/`onCreate` puts class verification on the boot path:
> if a library class cannot be linked, the process dies before an activity, a handler or a screen
> exists — the user sees "opens and closes", and there is nothing to read. `FMHub24App` therefore does
> nothing but install the crash logger, and `HostBootstrap.ensure()` runs on the loader's worker thread,
> where a failure becomes a `LoadingState.Error` on Splash/Settings instead of a vanished app.

These are not optional — each one produces a plugin that downloads, links, and then quietly
returns nothing.

| # | Requirement | Where it is done here | Symptom if missing |
|---|---|---|---|
| 1 | `app.baseClient` — CloudStream's `var app: Requests` needs an `OkHttpClient` the *host* installs | `plugins/HostBootstrap.kt` (`ensure()`), called from the loader | every provider's first `app.get()` throws; empty home screen, no load error |
| 2 | `com.lagradost.api.setContext(WeakReference(context))` — Android-side services resolve the app context through it | same `HostBootstrap.ensure()`, *not* `Application` | WebView/asset paths throw NPE deep inside a provider |
| 3 | `APIHolder.initAll()` before reading providers | `PluginLoader` | built-in services (e.g. `unixTimeMS`, subtitle providers) uninitialised |
| 4 | `com.lagradost.cloudstream3.utils.DataStore` — CloudStream keeps it in its **app** module, but extensions reference it | implemented in `:plugin-api` | `NoClassDefFoundError: ...utils/DataStore` → whole provider class fails to link |

Plus one OS-level rule: **the file must be read-only before `PathClassLoader` opens it**, on
Android 14+ (`SecurityException: Writable dex file '...' is not allowed`) — see
`PluginLoader.loadUnsafe` step 1, same as upstream `PluginManager.loadPlugin`.

And one packaging *preference*: **`classes.dex` should be STORED (uncompressed) inside the `.cs3`**.
Upstream's `make` task writes it that way and `tools/make_repo.py` re-packs it that way, because a
client that mmaps the entry in place needs it. It is not a hard requirement for this host: the loader
copies the package to a real file first, and all 86 packages of the phisher98 community repo are
DEFLATED and load fine. Treat "compressed dex" as a warning, never as a refusal — the admin panel does.

What *is* hard is which classes the host owns. Verified against the `v4.8.0` tree, not guessed:

| Reference in a `.cs3` | Resolves here? | Why |
|---|---|---|
| `com.lagradost.cloudstream3.MainAPI`, `…utils.ExtractorApi`, `…extractors.*` (110 embed extractors), `…plugins.BasePlugin`, `com.lagradost.api.Log`, `…utils.DataStore`(shim) | **yes** | in `:library`, which the app bundles and re-exports via `:plugin-api` |
| `com.lagradost.cloudstream3.CloudStreamApp`, `CommonActivity`, `MainActivity`, `utils.UIHelper`, `utils.DataStoreHelper`, `ui.*`, `database.*`, `actions.*`, `plugins.Plugin` | **no** | CloudStream's **app** module, which no extension host ships |

`extensions/tools/community_catalogue.py` classifies a whole community repo by exactly this table —
read `docs/community/phisher98-catalogue.md` for the current verdicts.

---

## 3. Building the plugin API

```bash
cd android-app
./gradlew :plugin-api:assembleRelease :plugin-api:publishToMavenLocal
```

That writes `~/.m2/repository/com/fmhub24/pluginapi/plugin-api/1-v4.8.0/`. Version components:

* `1` — `PLUGIN_API_VERSION` in `android-app/gradle.properties`, i.e. *our* contract (`FMHubApi.API_VERSION`).
* `v4.8.0` — `CLOUDSTREAM_VERSION`, the exact library artifact the host is built with.

`plugin-api/build.gradle.kts` re-exports (`api`) everything the plugin's bytecode will resolve from
the host classloader: the library itself, plus jsoup, NiceHttp, okhttp + logging-interceptor,
jackson-module-kotlin, kotlinx coroutines/serialization/datetime, atomicfu, rhino, ktor-http,
androidx core/preference. If a dependency is missing there, the extension compiles and then dies at
runtime with `NoClassDefFoundError` — the versions in that list must match CloudStream's
`gradle/libs.versions.toml` for the pinned tag.

The host declares `implementation(project(":plugin-api"))` (`android-app/app/build.gradle.kts`) so
those same classes are inside the APK.

### Version rules

* **Bump `PLUGIN_API_VERSION`** when an extension compiled against the old contract stops working:
  a renamed helper, a changed signature, a removed default. Never reuse a number.
* Do **not** bump it for a new optional helper — additions are backwards compatible.
* The `manifest.json` key `fmhubApiVersion` is written by the packaging step
  (`tools/make_repo.py`) from `PLUGIN_API_VERSION`; the host refuses a mismatch with a message
  naming both versions. Community `.cs3` files have no such key and are treated as plain
  CloudStream plugins.

---

## 4. Writing an extension

### 4.1 Create the module

Copy `extensions/fmhub-archive/` to `extensions/my-site/`, then in `extensions/settings.gradle.kts`
add `include(":my-site")`. Nothing else in the root build needs touching: the root project applies
the shared config to every subproject.

```kotlin
// extensions/my-site/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.lagradost.cloudstream3.gradle")   // <- provides `make`, `compileDex`, `generateManifest`
}

android {
    namespace = "com.fmhub24.extensions.mysite"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    // No resources unless cloudstream.requiresResources = true.
    sourceSets { getByName("main") { resources.setSrcDirs(emptyList<String>()) } }
}

cloudstream {
    description = "My site"
    authors = listOf("you")
    status = 0                 // 0 ok, 1 down, 2 error, 3 hidden
    language = "en"
    tvTypes = listOf("Movie")
    iconUrl = "https://example.com/icon.png"
}
```

`project.version` comes from `EXTENSION_VERSION` in `extensions/gradle.properties` and must be an
int: the gradle plugin writes it into `manifest.json` and `plugins.json`, and the app only
re-downloads a cached plugin when the number is greater than the one it has.

### 4.2 The plugin class

```kotlin
package com.fmhub24.extensions.mysite

import com.fmhub.plugin.api.FMHubBasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class MySitePlugin : FMHubBasePlugin() {
    override fun providers() = listOf(MySiteProvider())
    override fun extractors() = listOf(MySiteEmbed())   // optional
}
```

Rules the loader depends on: `public`, no-arg constructor, not `inner`, not abstract. `load()` is
called exactly once; `FMHubBasePlugin.load()` is `final` and registers every provider in a
`try/catch`, so one broken provider cannot take the whole plugin down. Anything that must run after
registration goes in `onProvidersRegistered()`.

### 4.3 The provider

Extend `MainAPI` directly, or `FMHubProvider` — a `MainAPI` with `getText` / `getDocument` /
`getJson` / `postJson` / `absUrl` / `cleanName` helpers that turn every network or parse failure
into `null` instead of an exception (a throwing provider takes its whole plugin down; a `null` one
hides a row).

```kotlin
package com.fmhub24.extensions.mysite

import com.fmhub.plugin.api.FMHubProvider
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class MySiteProvider : FMHubProvider() {
    override val name = "MySite"
    override val mainUrl = "https://example.com"
    override val siteUrl = mainUrl              // FMHubProvider: base for absUrl()
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)
    override val lang = "eng"

    // mainPageOf(data to displayName) — `data` is what comes back as request.data.
    override val mainPage = mainPageOf(
        "popular" to "Popular",
        "latest" to "Latest",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val doc = getDocument("$mainUrl/list/${request.data}?page=$page") ?: return null
        val items: List<SearchResponse> = doc.select(".card").mapNotNull { el ->
            val href = absUrl(el.selectFirst("a")?.attr("href"))
            if (href.isBlank()) return@mapNotNull null
            newMovieSearchResponse(name = el.text(), url = href, type = TvType.Movie) {
                this.posterUrl = absUrl(el.selectFirst("img")?.attr("src"))
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = getDocument("$mainUrl/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
            ?: return emptyList()
        return doc.select(".card").mapNotNull { el ->
            val href = absUrl(el.selectFirst("a")?.attr("href"))
            if (href.isBlank()) return@mapNotNull null
            newMovieSearchResponse(name = el.text(), url = href, type = TvType.Movie) {
                this.posterUrl = absUrl(el.selectFirst("img")?.attr("src"))
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = getDocument(url) ?: return null
        return newMovieLoadResponse(name = doc.title(), url = url, type = TvType.Movie, dataUrl = url) {
            this.plot = doc.selectFirst(".description")?.text()
            this.year = doc.selectFirst(".year")?.text()?.toIntOrNull()
            this.tags = doc.select(".genre").map { it.text() }.ifEmpty { null }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val doc = getDocument(data) ?: return false
        val src = doc.selectFirst("video source")?.attr("src")?.let { absUrl(it) }.orEmpty()
        if (src.isBlank()) return false
        callback(
            newExtractorLink(source = name, name = name, url = src, type = ExtractorLinkType.VIDEO) {
                this.quality = Qualities.Unknown.value
                this.referer = data
            }
        )
        return true
    }
}
```

Non-negotiable API facts for **v4.8** (each of these has bitten someone):

* Direct constructors of `LoadResponse`/`SearchResponse` subtypes, `Episode`, `SubtitleFile` and
  `ExtractorLink` are `@Deprecated(level = ERROR)`. Use the builders:
  `newMovieSearchResponse`, `newTvSeriesSearchResponse`, `newAnimeSearchResponse`,
  `newMovieLoadResponse`, `newTvSeriesLoadResponse`, `newAnimeLoadResponse`, `newEpisode`,
  `newSubtitleFile`, `newExtractorLink`, `newHomePageResponse`.
  `newMovie*`/`newTvSeries*`/`newEpisode` are **members of `MainAPI`** (no import inside a provider);
  `newHomePageResponse`, `mainPageOf` and `newExtractorLink` are **top-level** (they do need imports).
* Every builder takes the required fields as normal arguments and the optional ones in a trailing
  `initializer` lambda — that is where `posterUrl`, `plot`, `year`, `quality` go.
* `SearchResponse` is an interface with `name, url, apiName, type, posterUrl, posterHeaders, id,
  quality, score` — **no `year`** (only the concrete subtypes declare it).
* `MainPageRequest(name, data, horizontalImages)` — three required arguments; a provider never
  constructs it, it just reads what the host passes.
* `loadLinks` returns `Boolean`; return `false` only when nothing was emitted.
* Series: `TvSeriesLoadResponse.episodes` is `List<Episode>`, `AnimeLoadResponse.episodes` is
  `Map<DubStatus, List<Episode>>`. There is no `getEpisodes()` on `EpisodeResponse`.
* `override val settingsForProvider = SettingsJson()` only matters in CloudStream's own UI — this
  host has no per-provider settings screen, so keep provider options out of the contract and put
  defaults in code.

### 4.3.1 Vendoring a community provider instead of writing one

If the site already has a working provider in a community repo, copy it rather than rewriting the
scraper — but *rebuild* it, never ship their `.cs3` (their binaries link against the app module;
`NEEDS-ENTRY-SHIM` / `HOST-COMPAT` in the catalogue is that fact, stated per file). The five modules
under `extensions/` were added this way; `extensions/VENDORED.md` lists the exact edits:

1. find the sources (`gh api "search/code?q=<ProviderName>+extension:kt"` — the repo URL inside a
   published `manifest.json` is useless, 0 of 86 pointed at a source repo),
2. copy `src/main/kotlin/**` only: no `AndroidManifest.xml` (AGP 8 takes `namespace` from the build
   script), no upstream root `build.gradle.kts`, no BOM (two upstream files start with `\ufeff`),
3. if the entry class extends `plugins.Plugin`, change it to `BasePlugin` — that is the *only* edit
   any of our five modules needed, and it is one `sed` on the import and the class header,
4. write our own module `build.gradle.kts` (`com.android.library` + `kotlin-android` +
   `com.lagradost.cloudstream3.gradle`, `version = <upstream's number>`, `cloudstream { … }` with the
   upstream description/authors/language/tvTypes/iconUrl so the app shows what upstream shows),
5. `include(":ModuleName")` in `extensions/settings.gradle.kts` — the module name becomes the published
   provider name, so use upstream's spelling,
6. keep the provenance header in every copied file and the GPL-3.0 note in `VENDORED.md`; CloudStream
   extensions are derivative works of GPL code, so the sources stay public.

Everything else compiles as-is against the pinned `CLOUDSTREAM_VERSION` — including
`search(query, page): SearchResponseList`, `Score`, `addDate`, `newExtractorLink { quality = … }`,
`INFER_TYPE` and `com.lagradost.api.Log`, all of which already exist in `v4.8.0`.

### 4.4 Extractors (optional but usual)

```kotlin
class MySiteEmbed : ExtractorApi() {
    override val name = "MySiteEmbed"
    override val mainUrl = "https://example.com/e"
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) { … }
}
```

The host calls `getUrl` only for links whose `extractorData` is non-blank, and picks the extractor by
matching `ExtractorApi.name` against `link.source`. Two consequences: the `name` is a protocol value
(the site's `source` string must match it), and if nothing matches, the host keeps the raw link
instead of dropping it. Upstream's `getExtractorApiFromName` falls back to `extractorApis[0]` when
unmatched — which silently uses the wrong extractor — so we never call it.

---

## 5. Build and package

```bash
cd extensions
./gradlew :my-site:make                    # -> my-site/build/my-site.cs3
./gradlew :my-site:writeCacheEntry makePluginsJson   # -> build/plugins.json
python3 tools/make_repo.py \
    --base-url https://raw.githubusercontent.com/OWNER/REPO/builds \
    --name "FMHub24 Extensions" --out dist
```

`make_repo.py` does the three things the app needs and the gradle plugin does not do for you:

1. writes `repo.json` (`manifestVersion: 1`, `pluginLists: [<base>/plugins.json]`),
2. rewrites every entry's `url` to `<base>/<file>.cs3` (upstream writes a relative `innerUrl`),
3. patches `fmhubApiVersion` (+ `apiVersion`) into each package's `manifest.json` and re-stores
   `classes.dex` uncompressed, recording `fileHash`/`fileHashType=sha256`/`fileSize`.

Publish `dist/` to the `builds` branch (`git push` of a git worktree — see
`extensions/README.md`), or upload `dist/*` to the Supabase Storage bucket `extensions` and use
`https://<project>.supabase.co/storage/v1/object/public/extensions/repo.json` as the repo link.

---

## 6. Getting it into the app

Two paths, both real, both supported:

**A. Repository import (recommended).** Admin panel → **Repositories** → paste the `repo.json` link
→ the panel reads `pluginLists`, fetches every `plugins.json`, and lists all providers with
version/language/size/API compatibility → tick the ones the app may load. Selected rows go into
`extensions` with `status='active'` (everything else from that repo becomes `'disabled'`), with
`source_repo_url`, `internal_name`, `file_url`, `version`, `file_hash`, `api_version` filled from
the repo metadata. Re-opening the dialog diffs versions so an updated repo bumps only what changed.
The `.cs3` files stay where the repo hosts them — Supabase only stores metadata.

**B. Single upload.** Admin panel → **Extensions** → *Upload .cs3*. The panel opens the archive in
the browser (`src/api/cs3.ts`), reads `manifest.json`, computes SHA-256, and **refuses** the file if
`classes.dex` is missing or deflated, or the manifest has no `pluginClassName` — i.e. the mistakes
that used to only appear as a load failure on a phone are reported at upload time.

Either way the app then: fetches `status='active'` rows → downloads (verifying sha256 when known) →
caches into `cached_extensions` → loads each file → shows per-file results, including failures, on
Splash and Settings.

---

## 7. Debugging

```bash
adb logcat -c && adb logcat | grep -Ei "FMHubPlugin|dex|NoClassDef|SecurityException|LinkingError"
```

On a phone without a cable: every refusal the loader makes is also appended to the app log with the
line `Source not started: <file>` followed by the reason, and the whole log is mirrored to
**`Download/FMHub24-crash.txt`** once per start (`CrashLog.note` + `publishDiagnostics`). Send that
file when something does not load — the screens themselves deliberately say nothing about extensions,
plugins or where content comes from, so a screenshot of a spinner carries no diagnosis.

The app never fails silently: `CrashLog` writes every uncaught exception (and every uncaught
coroutine failure) to `files/crash.log` in the app's own storage, and the next launch shows that log on
the splash screen with a Copy button — if the crash happens before Compose exists, `MainActivity`
draws the trace in plain Views instead. So a launch that "just closes" is still a readable report:

```bash
adb logcat -s FMHubCrash -d
adb shell run-as com.fmhub24.app cat files/crash.log
```

| Message in the app | Meaning | Fix |
|---|---|---|
| app closes on open, log says `NoClassDefFoundError: java.time.*` / `java.nio.file.*` | host built without core-library desugaring while the CloudStream libraries still need those on API 24-25 | `isCoreLibraryDesugaringEnabled = true` + `coreLibraryDesugaring(desugar_jdk_libs_nio)` in `app/build.gradle.kts` (both are on; keep them if you fork) |
| app closes on open, log says `ClassNotFoundException: com.lagradost.cloudstream3.X` | the host does not ship the class the plugin was compiled against | rebuild the host against the pinned `:plugin-api`; do not add stub types |
| app closes on open with no message at all | only possible on a build older than the crash log above | install a current debug build, then read `files/crash.log` |
| `Not a zip container` | the cached file is an HTML error page (a 404 saved as `.cs3`) | the `file_url` is wrong/private; re-import the repo row or fix the storage path |
| `No classes.dex inside the container` | a `.jar`/AAR or a stripped zip | build with `:module:make`, not `assembleRelease` |
| `classes.dex is compressed` | zip used DEFLATE for the dex | repack with `tools/make_repo.py` (it stores the dex) |
| `Writable dex file … is not allowed` (SecurityException) | file was writable when opened | host bug — `setReadOnly()` must run before `PathClassLoader`; keep the file read-only |
| `host is missing com.foo.Bar` | ART could not link one class → the whole provider class is unusable | add the missing dependency as `api` in `:plugin-api` (if the host can supply it) or stop using it in the extension |
| `host is missing com.lagradost.cloudstream3.utils.DataStoreHelper … that type lives in CloudStream's app module` | the extension was compiled against CloudStream's **app**, not the plugin API | use only `MainAPI`/`ExtractorApi`/`DataStore`/`app.get`; rebuild against `plugin-api` |
| `Built for FMHub plugin API v2 but this host is v1` | version gate | rebuild against the host's `:plugin-api`, or update the app |
| `incompatible API: …NoSuchMethodError` | extension compiled against a newer library than the host ships | align `CLOUDSTREAM_VERSION` in both `gradle.properties` files and rebuild both |
| `BasePlugin.load() threw: …` | the plugin's own registration code crashed | wrap risky work in the plugin; registration must not throw |
| `Plugin class loaded and load() ran, but it registered no MainAPI` | `pluginClassName` points at a class whose `load()` never calls `registerMainAPI` (it extends `Plugin` from CloudStream's app module, or registers outside `load()`) | extend `BasePlugin`/`FMHubBasePlugin` and register inside `load()`; a bare `MainAPI` as `pluginClassName` *is* accepted (registered directly, mirroring `registerMainAPI`) |
| loads, 0 providers, no error | `load()` registered nothing: wrong `pluginClassName`, or providers registered outside `load()` | `generateManifest` derives the class from `@CloudstreamPlugin`; check `manifest.json` inside the `.cs3` |
| providers load, home empty | `hasMainPage=false`, or `getMainPage` returned null/empty for every provider | check `hasMainPage`, and that `getMainPage` does not depend on `settingsForProvider` (this host does not apply per-provider settings; only `Show adult content` is honoured, and it only hides providers whose every type is `NSFW`) |
| every request fails | `app.baseClient` not installed | see §2 row 1 |

To inspect a package by hand:

```bash
unzip -l build/my-site.cs3            # manifest.json + classes.dex (dex must be "Stored")
unzip -p build/my-site.cs3 manifest.json | jq
python3 - <<'PY'
import hashlib,sys; print(hashlib.sha256(open('build/my-site.cs3','rb').read()).hexdigest())
PY
```

Compare that hash with `file_hash` on the admin row — if they differ, the app rejects the download
before it ever reaches the loader.

Fast iteration: `./gradlew :my-site:deployWithAdb` (the CloudStream gradle plugin's task) pushes the
`.cs3` straight into the app's extension directory, so you can test a provider without the admin
panel at all.

---

## 8. Test the whole loop on a device

1. Run the SQL in `supabase_schema.sql` (the v2 block at the bottom is idempotent).
2. `cd admin-panel && npm install && npm run dev` → log in → Repositories → add your `repo.json`.
3. Tick one provider, activate it.
4. Build + install the app (`cd android-app && ./gradlew :app:assembleDebug`) with the same
   `SUPABASE_URL`/`SUPABASE_ANON_KEY` in `android-app/local.properties`.
5. On Splash you should see `Loading N extension(s)… → N loaded, M providers`. Anything else is a
   row in the failure list with one of the sentences from §7 next to it.
