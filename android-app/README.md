# FM HuB 24 Android App

FM HuB 24 is a native Kotlin/Compose application with a typed Rust provider core. The app does not load executable extension packages, dynamic dex files, or CloudStream runtime modules.

## Architecture

The code is organized into three layers:

- `domain`: media models and repository contracts.
- `data`: Rust JNI provider adapter, JSON validation, local settings, and collections.
- `presentation`: the complete Compose UI and page state model.

The provider boundary returns typed JSON envelopes. Kotlin rejects malformed responses and exposes explicit unavailable states when a provider source has not been configured.

## Pages

The redesigned app includes Discover/Home, Search, Details, Player, My List, Downloads, and Settings. Navigation stays local to the app and does not depend on extension metadata.

## Build

```bash
./gradlew :app:assembleDebug
```

The native provider library is built in CI for `arm64-v8a`, `armeabi-v7a`, and `x86_64`. Configure the provider endpoint through the `PROVIDER_BASE_URL` Gradle property or the in-app Settings page.
