# Vendored extensions

Five provider modules copied out of the community `phisher98/cloudstream-extensions-phisher` repo
(via the full-source mirror `mahmoodnizamani94-png/cloudstream-extensions-phisher`, `main`) so they
can be compiled against **our** pinned host API (`CLOUDSTREAM_VERSION` in `android-app/gradle.properties`)
instead of the `pre-release` API upstream targets.

The module name *is* the provider name in the published `manifest.json` (the CloudStream gradle plugin
takes `name` from `project.name`), hence upstream's CamelCase directory names.

| module                | version | entry class (`pluginClassName`)        | provider class                     |
|-----------------------|---------|----------------------------------------|------------------------------------|
| `BanglaPlex`          | 5       | `com.BanglaPlex.BanglaPlexPlugin`      | `com.BanglaPlex.Banglaplex`        |
| `Cinefreak`           | 9       | `com.cinefreak.CinefreakPlugin`        | `com.cinefreak.Cinefreak`          |
| `HDhub4u`             | 50      | `com.hdhub4u.HDhub4uPlugin`            | `com.hdhub4u.HDhub4uProvider`      |
| `MovieBoxProvider`    | 24      | `com.MovieBox.MovieBoxProviderPlugin`  | `com.MovieBox.MovieBoxProvider`    |
| `AllMovieLandProvider`| 20      | `com.phisher98.AllMovieLandProviderPlugin` | `com.phisher98.AllMovieLandProvider` |

## What we changed when copying

1. BOM stripped from the sources (the mirror ships `﻿` at the head of two plugin files, which
   Kotlin accepts but `git diff` renders as garbage).
2. `MovieBoxProvider` only: its entry class extended `com.lagradost.cloudstream3.plugins.Plugin`, which lives
   in CloudStream's **app** module and therefore does not exist for us (our host ships
   `plugin-api`'s `BasePlugin`). Rewritten to `BasePlugin` — same `@CloudstreamPlugin` +
   `registerMainAPI` / `registerExtractorAPI` body, `load()` instead of `load(context)`.
3. Empty `AndroidManifest.xml` files dropped (AGP 8 takes `namespace` from the build script).
4. Each module got its own `build.gradle.kts`: `com.android.library` + `kotlin-android` +
   `com.lagradost.cloudstream3.gradle`, `minSdk 24`, `jvmTarget 17`, and the `cloudstream { }` metadata
   (description / authors / status / language / tvTypes / iconUrl) copied from upstream's module so the
   admin panel and the app show what upstream shows. The root project supplies the CloudStream dependency
   at **our** pinned `CLOUDSTREAM_VERSION`, the `fmhubApiVersion` stamping and the `make` task.
5. No compat shims were needed: `search(query, page): SearchResponseList`, `Score`,
   `newExtractorLink { quality = ... }`, `INFER_TYPE` and `com.lagradost.api.Log` all already exist in
   `v4.8.0`, and the `com.lagradost.cloudstream3.extractors.*` embed classes they import are in the
   **library** at that tag (110 of them), so our host resolves them.

Nothing else was touched: scraper logic, embed hosts, domain rotation and `Domains` companions are
upstream's. Grep `// Vendored from` at the top of each file for the provenance header.

## Updating

```bash
# 1. re-download the module from the mirror (Contents API, base64) and re-run the copy,
# 2. bump `version = N` in that module's build.gradle.kts,
# 3. commit, push — .github/workflows/extensions-build.yml rebuilds every module on its own.
```

## Licence

Upstream is a CloudStream extension repo, i.e. derived from CloudStream (GPL-3.0). The mirror itself
ships no `LICENSE` file; keep these sources in this **public** repository and keep the provenance
header in every vendored file. Do not relicense. The providers scrape third-party sites — shipping
them is the app owner's call, same as upstream.
