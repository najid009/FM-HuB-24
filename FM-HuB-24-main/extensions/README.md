# FMHub24 extensions

Source of every `.cs3` the FM-HuB 24 app can load. This is a **standalone Gradle project** — it is
deliberately *not* included in `android-app/settings.gradle.kts`, because an extension must be
compiled against the host API but must never bundle a second copy of it. Bundling a private
`MainAPI` is exactly what produces *"1 file found but none produced a MainAPI provider"*.

The long version — API rules, the whole failure catalogue — is in [`../docs/PLUGINS.md`](../docs/PLUGINS.md).

## Layout

```
extensions/
├── settings.gradle.kts          # include(":fmhub-archive") — one line per extension
├── build.gradle.kts             # shared toolchain + compileOnly deps for every module
├── gradle.properties            # CLOUDSTREAM_VERSION / PLUGIN_API_VERSION / EXTENSION_VERSION
├── fmhub-archive/               # reference extension: archive.org, public-domain media
│   ├── build.gradle.kts         # android {} + cloudstream {} (repo metadata)
│   └── src/main/java/com/fmhub24/extensions/archive/
│       ├── FMHubArchivePlugin.kt      # @CloudstreamPlugin entry point (extends FMHubBasePlugin)
│       └── InternetArchiveProvider.kt # the MainAPI provider: main page, search, load, loadLinks
└── tools/make_repo.py           # pack modules into repo.json + plugins.json + fixed .cs3 files
```

`compileSdk`/AGP/Kotlin here match `android-app` on purpose — Kotlin metadata and the stdlib at
runtime must not be older than what the dex was compiled with.

## Build

Extensions compile against `com.fmhub24.pluginapi:plugin-api`, which is resolved from **mavenLocal**,
so publish it once per machine (or per CI run) first:

```bash
cd ../android-app && ./gradlew :plugin-api:publishToMavenLocal && cd ../extensions

./gradlew :fmhub-archive:make
# -> fmhub-archive/build/fmhub-archive.cs3   (the file you upload or publish)

./gradlew writeCacheEntry makePluginsJson
# -> build/plugins.json                        (repo metadata, before URL/hash normalisation)

./gradlew make                                 # every module at once
./gradlew :fmhub-archive:deployWithAdb         # push straight into a connected device's app
```

`make` is the CloudStream gradle plugin's task (`id("com.lagradost.cloudstream3.gradle")`): it runs
`compileDex` + `generateManifest` and zips `classes.dex` + `manifest.json` into
`build/<module-name>.cs3`. Don't hand-roll that zip.

## Publish a repository

```bash
python3 tools/make_repo.py \
  --base-url https://raw.githubusercontent.com/OWNER/REPO/builds \
  --name "FMHub24 Extensions" \
  --out dist
```

The tool produces a CloudStream-compatible repository and does three things the app depends on:

| what | why it matters |
|---|---|
| `repo.json` with `manifestVersion: 1` + `pluginLists: [<base>/plugins.json]` | the exact format CloudStream (and the admin panel) reads |
| rewrites each entry's `url` to `<base>/<file>.cs3` | the gradle plugin leaves `innerUrl` relative; the app cannot resolve that |
| stores `classes.dex` **uncompressed**, records `fileHash` (sha256) / `fileSize`, injects `fmhubApiVersion` into `manifest.json` | ART cannot mmap a compressed dex; the hash lets the app reject a corrupted download; the API stamp lets the host refuse an incompatible build with a message instead of a silent 0-provider load |

Then either

* push `dist/` to a `builds` branch (CI does it for you: **Actions → Extensions Build → Run
  workflow → tick *Publish*** — see `.github/workflows/extensions-build.yml`), or
* upload `dist/*` to the Supabase Storage bucket `extensions` and use
  `https://<project>.supabase.co/storage/v1/object/public/extensions/repo.json`.

Paste that `repo.json` URL into the admin panel under **Repositories**; tick the providers the app
may load. Nothing copies the `.cs3` bytes into Supabase in that mode — the file must stay publicly
fetchable at the URL in `plugins.json`.

## Adding an extension

1. `cp -r fmhub-archive my-site`, rename the package/namespace to `com.fmhub24.extensions.mysite`.
2. Add `include(":my-site")` to `settings.gradle.kts`.
3. Write the plugin class (`FMHubBasePlugin` + `providers()`) and the provider (`FMHubProvider` or a
   plain `MainAPI`). §4 of [`../docs/PLUGINS.md`](../docs/PLUGINS.md) is the checklist, including the
   v4.8 builder functions you must use instead of the deprecated constructors.
4. Bump `EXTENSION_VERSION` in `gradle.properties` when you publish a change — the app only
   re-downloads when that int is greater than the cached version.
5. Build, pack, publish, tick it in the admin panel.

## Debugging a package

```bash
unzip -l fmhub-archive/build/fmhub-archive.cs3     # manifest.json + classes.dex (dex must be Stored)
unzip -p fmhub-archive/build/fmhub-archive.cs3 manifest.json | python3 -m json.tool
python3 tools/make_repo.py --base-url https://x/y --out /tmp/check --dry-run
```

If the app loads the file but shows nothing, the answer is in `adb logcat | grep FMHubPlugin` — and
the same sentence appears under the failed provider on the app's Settings → Extensions screen.
