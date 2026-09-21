# FM-HuB-24 Rust provider core

This crate is the in-process provider boundary for the Android application. It exposes typed catalog, details, season/episode, stream, and subtitle operations. The Android app is expected to compile this library into the APK; no executable provider code is downloaded or loaded at runtime.

The HTTP source is configured as an HTTPS base URL by the native bridge. The crate deliberately does not contain credentials, private keys, anti-bot bypasses, or a hard-coded catalogue. If the configured authorized source is unavailable, the Android layer should render its error or empty state rather than inventing content.

## Endpoint contract

The current adapter expects JSON responses at `/home?page=N`, `/search?q=...&page=N`, `/details/{id}`, `/details/{id}/season/{season}?page=N`, `/streams/{id}`, and `/subtitles/{id}`. These paths are an explicit typed boundary, not a claim that any third-party site provides them. A provider adapter may map an authorized public API into this contract before shipping.

## Reference and licensing

The design is independently implemented from the provider/client/adaptation/session separation used by [MovieBox-TUI](https://github.com/mesamirh/MovieBox-TUI). No terminal UI, desktop launcher, updater, or filesystem code is included here. The reference repository is MIT licensed by MovieBox Contributors; retain its `LICENSE-MIT` attribution if source is copied in a future adapter. This crate is MIT licensed.
