# Rust provider migration

## Scope and safety

The migration is being performed on `rust-provider-migration`. The repository state before migration is preserved by the local `legacy-extension-architecture` branch at the original `main` commit. No history is rewritten, and no secrets, signing files, user data, or GitHub Actions credentials are touched.

Legacy extension files remain in place until every Android consumer has moved to the provider boundary. This is intentional: deleting plugin code before replacing its consumers would create a broken intermediate build.

## Target boundary

```text
Compose screens -> Kotlin ProviderCoreRepository -> compiled Rust provider core
                                                   -> authorized HTTPS provider endpoint
                                                   -> typed catalog/details/episode/stream/subtitle models
                                                   -> Media3
```

The provider core is `rust-core/`. It contains no terminal UI, desktop launcher, updater, dynamic dex loading, credentials, or hardcoded catalogue. It requires HTTPS, maps retryable HTTP failures to a user-safe error, applies a bounded timeout/retry policy, and caches the home page in memory. The endpoint contract is documented in `rust-core/README.md` and must be backed by a public or explicitly authorized API before production use.

The Kotlin DTOs and `ProviderCoreRepository` contract are in `android-app/app/src/main/java/com/fmhub24/app/data/provider/`. They are deliberately independent of CloudStream types so Home, Search, Details, and Player can be migrated one flow at a time. Until the native ABI bridge is compiled and an authorized endpoint is configured, the safe implementation returns an unavailable state; it never fabricates content.

## Remaining phases

1. Add the Android native build (UniFFI or a small JNI bridge) and map Rust results to the Kotlin DTOs.
2. Migrate `ContentRepository`, `HomeViewModel`, `SearchViewModel`, `DetailsViewModel`, and player stream resolution to `ProviderCoreRepository`.
3. Retain Room-backed favorites/history/continue-watching while removing extension-only persistence.
4. Build and test the new flow on an emulator or device.
5. Search all consumers again, then remove `PluginManager`, `PluginLoader`, `.cs3` assets, `plugin-api`, extension workflows, and extension-only Supabase schema in separate verified commits.
6. Run `git grep` for all legacy identifiers and perform a clean Android/Rust build before any main-branch push.

A provider source change requires a new APK because the provider implementation is compiled into the application. Remote executable code updates are intentionally not supported.
