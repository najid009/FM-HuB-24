# FMHuB24 CloudStream Fork

FMHuB24 now includes a vendored fork baseline under `android-app-cloudstream/`, copied from the official `recloudstream/cloudstream` source. The existing `android-app/` remains untouched as a fallback while the migration is validated.

## What is implemented

The fork has the FMHuB24 application ID (`com.fmhub24.app`) and default English branding. Before CloudStream loads online plugins, it performs a fail-open synchronization from an administrator-controlled HTTPS JSON endpoint. The endpoint may return either a wrapped document:

```json
{
  "repositories": [
    { "name": "FMHuB24 Sources", "url": "https://example.org/repo.json", "iconUrl": "https://example.org/icon.png" }
  ]
}
```

or a Supabase REST array containing `name`, `url`, and `icon_url` fields. Only HTTPS repository URLs are accepted. Existing user repositories are not removed.

The admin panel has a new **App sources** tab backed by `app_extension_repositories`. Run `supabase_migrations/20260919_cloudstream_repositories.sql` first. The Android build then needs the public REST URL in either the environment variable `FMHUB_REPOSITORY_CONFIG_URL` or `local.properties`:

```properties
fmhub.repository.config.url=https://PROJECT.supabase.co/rest/v1/app_extension_repositories?enabled=eq.true&select=name,url,icon_url&order=priority.asc
```

The Supabase anon REST read policy is intentionally limited to enabled rows. Admin writes still require an authenticated Supabase session.

## Build

Build from the fork directory with the normal CloudStream Gradle commands, for example:

```bash
cd android-app-cloudstream
./gradlew :app:assembleStableDebug
```

The fork requires the Android SDK and the same toolchain expected by the upstream project. A missing or unavailable remote control endpoint must not prevent the app from opening; it only means no admin repositories are added during that launch.

## Licensing and attribution

The upstream CloudStream source is GPL-3.0. This fork must retain the upstream `LICENSE`, attribution, and source-distribution obligations. FMHuB24 branding must not imply official CloudStream endorsement. Only approved and legally usable extension repositories should be published through the admin panel.

## Next migration steps

1. Build and smoke-test the fork on an Android device/emulator.
2. Copy only required FMHuB24-specific branding/assets and Bengali strings.
3. Configure the Supabase REST URL through the release build environment.
4. Verify repository disable/enable behaviour and plugin rollback.
5. Migrate any remaining FMHuB24-only backend features after the CloudStream baseline is stable.

## Final GitHub/Supabase secrets

The signed Android workflow expects these GitHub Actions secrets:

```text
SUPABASE_URL
SUPABASE_ANON_KEY
SUPABASE_PROJECT_REF
SUPABASE_ACCESS_TOKEN
TMDB_READ_ACCESS_TOKEN
ANDROID_KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

Run the **Supabase schema and functions deploy** workflow once after adding them. It applies migrations, stores `TMDB_READ_ACCESS_TOKEN` as an Edge Function secret, and deploys `/functions/v1/catalog-config` and `/functions/v1/tmdb-trending`. The Android release workflow derives those two function URLs from `SUPABASE_PROJECT_REF`, so separate URL secrets are not required.
