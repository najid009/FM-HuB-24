# FM-HuB-24

Two independent codebases — the Android user app and the web admin panel — sharing one Supabase
backend, plus `extensions/`, the Gradle project that builds the `.cs3` plugins the app loads.

## Projects

### 1. `android-app/` — FMHuB24 User App
- **Type:** Android, Kotlin, Jetpack Compose, Material 3, Dark theme
- **Package:** `com.fmhub24.app`, Min SDK 24, Target 35, compileSdk 36
- **Two modules:** `:app` (the host) and `:plugin-api` (the published extension contract, `com.fmhub24.pluginapi:plugin-api`)
- **Backend:** Supabase read-only, fetches `status='active'` extensions
- **Core:** downloads `.cs3` files, verifies sha256, loads the dex with `PathClassLoader` against the **real CloudStream library API** (`com.github.recloudstream.cloudstream:library`), calls `getMainPage()` / `search()` / `load()` / `loadLinks()`, resolves extractor links, plays via Media3
- **Diagnostics:** every load failure is reported with a reason on Splash and in Settings → Extensions — "0 providers" is never the whole message anymore
- **No fake data** — empty state if no extensions
- **Storage:** Room for favorites, watch progress, cached extensions

See `android-app/README.md` for full details.

### 2. `admin-panel/` — FMHuB24 Admin Panel
- **Type:** Web SPA, React + TypeScript + Vite, Tailwind, Supabase JS, React Query
- **Auth:** Supabase email/password, no public signup
- **Features:** Real Supabase auth + CRUD; upload `.cs3` to Storage bucket `extensions` with progress (the package is opened in the browser first: dex present, dex STORED, `pluginClassName` found, sha256 recorded); edit, toggle status, delete (storage + DB)
- **Repositories tab:** paste a CloudStream-style `repo.json` link, see every provider it publishes, tick which ones the user app may load (unselected ones become `disabled`, never deleted)
- **Run locally (no hosting needed):** `cd admin-panel && npm install && npm run dev` (needs `.env` with `VITE_SUPABASE_URL` + `VITE_SUPABASE_ANON_KEY`)
- **Hosting (optional):** Vercel/Netlify free tier

See `admin-panel/README.md`.

## Plugin System (how content actually gets in)

An extension is a zip with `classes.dex` + `manifest.json` (`.cs3`). The dex references CloudStream
types **by name**, so the host must expose the real library classes — any local re-declaration
breaks linking. `:plugin-api` is therefore just the real library plus FMHub's additions
(`FMHubBasePlugin`, `FMHubProvider`, `FMHubApi.API_VERSION`, and the `com.lagradost.cloudstream3.utils.DataStore`
host shim CloudStream keeps in its app module), published to mavenLocal for `extensions/` to compile against.

```bash
cd android-app && ./gradlew :plugin-api:publishToMavenLocal     # the contract
cd extensions && ./gradlew :fmhub-archive:make                   # -> fmhub-archive/build/fmhub-archive.cs3
cd extensions && python3 tools/make_repo.py --base-url https://raw.githubusercontent.com/OWNER/REPO/builds --out dist
```

Then upload the `.cs3` in the admin panel, or publish `dist/` to a `builds` branch and paste the
`repo.json` URL into **Repositories**. Full walkthrough, API rules and failure catalogue:
**[`docs/PLUGINS.md`](docs/PLUGINS.md)**. CI: `.github/workflows/extensions-build.yml`.

## Shared Supabase Backend

- **Project URL:** `https://your-project.supabase.co` (from env)
- **Tables:** `extensions` (file + plugin metadata: `plugin_class_name`, `internal_name`, `api_version`, `cs_api_version`, `file_hash`, `file_name`, `size_bytes`, `source_repo_url`, `requires_resources`) and `extension_repos` (saved repo links, `enabled`, `last_synced_at`). Full DDL, RLS and the idempotent v2 migration: `supabase_schema.sql`
- **RLS:**
  - Public read where `status='active'` (for User App)
  - Auth full access (for Admin Panel)
- **Storage:** bucket `extensions`, public read, auth write

### SQL Setup
```bash
# whole file, safe to re-run (the v2 block uses `add column if not exists`)
psql "$SUPABASE_DB_URL" -f supabase_schema.sql      # or paste it into the Supabase SQL editor
```

`status` drives what the app sees: `active` is downloaded and loaded, `disabled` is deliberately
unselected, `broken` is known-incompatible (wrong plugin API, unreadable package) and never loaded.
RLS keeps anon reads to `status='active'`, so disabled/broken rows are invisible to the app but
editable in the panel.

## Separation Rule
> ⚠️ Two codebases are completely independent, only linked by Supabase. Do not merge.

- User App: read-only, no upload/edit/delete, no fake data, real extension loading
- Admin Panel: write, real Supabase connection, no mock, no public signup

## Env

Admin Panel `.env` (from `.env.example`, never commit real keys):
```
VITE_SUPABASE_URL=https://your-project.supabase.co
VITE_SUPABASE_ANON_KEY=your-anon-key-here
```

Android `local.properties` (from `local.properties.example`):
```
sdk.dir=/path/to/Android/Sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key-here
```

## Run

Admin:
```bash
cd admin-panel
npm install
npm run dev
```

Android:
- Open `android-app` in Android Studio
- Sync, Run

## Deliverables Met
- [x] Android app fetches real extensions, loads them against the real CloudStream API, shows real content, plays real links, Room persistence, MVVM+Hilt, no dummy data
- [x] Admin Panel real Supabase auth + CRUD + storage upload with progress + package validation before publish
- [x] CloudStream-style repository import in the admin panel: repo link → list providers → choose which stay active
- [x] Publishable plugin API (`:plugin-api`) + reference extension (`extensions/fmhub-archive`) + packaging tool (`tools/make_repo.py`) + CI
- [x] Load failures explained per file (missing class, wrong API version, compressed dex, unreadable manifest) instead of one generic error
- [x] Clean architecture, separate codebases, shared backend
