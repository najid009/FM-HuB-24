# FMHuB24 Admin Panel

React + TypeScript + Vite admin panel for managing .cs3 extensions. Connected to shared Supabase backend with FMHuB24 Android app.

## Features (All Real, No Mock)

- **Auth**: Supabase email/password login (no public signup)
- **Dashboard**: Real-time fetch of all extensions (active/disabled/broken), stats, filter box
- **Upload**: Real file upload to Supabase Storage `extensions` bucket with progress tracking + DB insert
- **Package check before publish**: the `.cs3` is opened *in the browser* (`src/api/cs3.ts` parses
  the zip central directory, reads `manifest.json`, computes SHA-256). Upload is refused when
  `classes.dex` is missing, when the dex is deflated (ART cannot mmap it), or when the manifest has
  no `pluginClassName` — the mistakes that used to surface only as a load failure on a phone.
- **Repositories tab**: paste a CloudStream-style `repo.json` link → every provider the repo
  publishes is listed with version/language/size and a compatibility verdict → tick the ones the app
  may load. Selected rows become `active`, unselected become `disabled` (never deleted), and a
  re-sync bumps only rows whose `version`/`fileHash` changed.
- **Edit**: Real `update()` query, including `pluginClassName` / `internal name` / `requiresResources`
  (a replacement file goes through the same package check as an upload)
- **Toggle Status**: Real status toggle
- **Delete**: Real storage file delete + row delete (removing a repo can delete its imported rows too)

## Supabase Setup

### 1. Create Project
Go to supabase.com, create new project.

### 2. Run SQL
Run the whole file [`../supabase_schema.sql`](../supabase_schema.sql) (SQL editor → New query →
paste → Run). It creates `extensions` + RLS, and the **v2 block** at the bottom adds what the plugin
flow needs. The v2 part is idempotent (`add column if not exists`), so re-running it on an existing
project is safe:

| column | used for |
| --- | --- |
| `plugin_class_name` | the `BasePlugin` subclass the host instantiates — no more guessing from the dex |
| `internal_name` | stable id inside a repo; how repo rows are matched on re-sync |
| `api_version` | FMHub plugin-contract version; a mismatch is stored and shown, not loaded |
| `cs_api_version` | CloudStream's own `apiVersion` from `plugins.json` |
| `file_hash` | SHA-256 the app verifies its download against |
| `file_name`, `size_bytes` | container type survives (a `.cs3` renamed to `.zip` still loads); size for the UI |
| `source_repo_url` | which repo link this row came from (null for manual uploads) |
| `requires_resources` | the app must unpack `res/` + `resources.arsc` next to the dex |

Plus `extension_repos (id, url unique, name, description, enabled, last_synced_at, created_at,
updated_at)` with the same RLS shape: anon may read enabled rows, only authenticated users write.

### 3. Storage
Create bucket `extensions`, set Public = true.
Add policy: allow authenticated upload/delete, public read.

### 4. Auth
- Disable public signup in Auth settings
- Manually create admin user via Dashboard > Auth > Users

### 5. Env
Copy `.env.example` to `.env` and fill:
```
VITE_SUPABASE_URL=https://xxx.supabase.co
VITE_SUPABASE_ANON_KEY=eyJ...
```

## Run

```bash
npm install
npm run dev
```

Build: `npm run build`
Host on Vercel/Netlify - set env vars.

## Shared Backend
Same Supabase project is used by Android app (read-only, anon key, only active extensions visible).

## What `status` means to the app

| status | app behaviour | how it gets set |
| --- | --- | --- |
| `active` | downloaded and loaded | upload default; ticking a provider in the Repositories dialog |
| `disabled` | never fetched (RLS hides it from anon) | un-ticking a provider, or the Enable/Disable button |
| `broken` | never fetched | auto-marked when a repo entry was built for another plugin API version, or set by hand after `adb logcat` says why |

Because RLS limits anon reads to `status='active'`, a disabled/broken row cannot reach a phone at
all — and it still shows here with its reason, so fixing it is a click rather than a re-upload.

## Notes
- Repo import stores **URLs only**: the `.cs3` bytes stay on GitHub/wherever the repo publishes them,
  so the link in `plugins.json` must stay publicly fetchable. CORS must allow the browser, which
  `raw.githubusercontent.com` does.
- SHA-256 needs `crypto.subtle`, i.e. a secure context — `http://localhost` is fine, plain
  `http://192.168.x.x` is not; the row is then saved without a hash and the app skips verification.
