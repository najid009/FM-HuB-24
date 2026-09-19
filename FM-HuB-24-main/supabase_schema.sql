-- FMHuB24 Supabase Schema - Shared by Android App and Admin Panel

create table extensions (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  file_url text not null,          -- Supabase Storage public URL
  version int not null default 1,
  language text,
  tv_types text[],                 -- {"Movie","TvSeries"}
  status text not null default 'active', -- active / disabled / broken
  icon_url text,
  description text,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

-- Enable RLS
alter table extensions enable row level security;

-- Public read only active extensions (for Android User App)
create policy "Public read active extensions"
  on extensions for select
  using (status = 'active');

-- Admin full access (authenticated users)
create policy "Admin full access"
  on extensions for all
  using (auth.role() = 'authenticated')
  with check (auth.role() = 'authenticated');

-- Storage Bucket: extensions
-- Create via Dashboard > Storage > New Bucket
-- Name: extensions
-- Public: true
-- Policies:
-- 1. Public read: allow select for all
-- 2. Auth write: allow insert/update/delete for authenticated

-- Example storage policies SQL (run in storage schema if needed):
-- For public read:
-- create policy "Public read extensions bucket"
-- on storage.objects for select
-- using (bucket_id = 'extensions');

-- For auth write:
-- create policy "Auth write extensions bucket"
-- on storage.objects for all
-- using (bucket_id = 'extensions' and auth.role() = 'authenticated')
-- with check (bucket_id = 'extensions' and auth.role() = 'authenticated');

-- =============================================================================
-- v2 — repository imports, plugin metadata, load diagnostics
-- Idempotent: safe to run on a database created by the block above, and on a fresh one.
-- =============================================================================

-- What the app needs to load a package deterministically. Without these the host has to guess the
-- entry class from the dex, which is exactly how "1 file found but none produced a MainAPI
-- provider" happens: the file is fine, the metadata is not.
alter table extensions add column if not exists internal_name      text;
alter table extensions add column if not exists plugin_class_name  text;
alter table extensions add column if not exists api_version        int;
alter table extensions add column if not exists cs_api_version     int;
alter table extensions add column if not exists file_hash          text;
alter table extensions add column if not exists file_name          text;
alter table extensions add column if not exists size_bytes         bigint;
alter table extensions add column if not exists source_repo_url    text;
alter table extensions add column if not exists requires_resources boolean not null default false;

comment on column extensions.internal_name is 'Stable id inside a repository (plugins.json internalName); null for manual uploads.';
comment on column extensions.plugin_class_name is 'manifest.json pluginClassName — the BasePlugin subclass the host instantiates.';
comment on column extensions.api_version is 'FMHub plugin-contract version (FMHubApi.API_VERSION on the host). A mismatch is reported as broken instead of loaded.';
comment on column extensions.cs_api_version is 'CloudStream own apiVersion from plugins.json — the host/library level the extension was compiled against.';
comment on column extensions.file_hash is 'sha256 of the .cs3; the app verifies the download before it touches the cache.';
comment on column extensions.source_repo_url is 'repo.json this row was imported from — kept so a refresh can diff versions and so unselected providers can be disabled. Not a secret: the file is fetched from this URL by the app.';

-- The app filters status=eq.active and orders by updated_at desc on every launch.
create index if not exists extensions_active_updated_idx on extensions (status, updated_at desc);
-- The repo refresh looks rows up by (repo, internal_name); non-unique on purpose because manual
-- uploads have no internal_name and PostgREST upserts are not used (see importRepoEntries).
create index if not exists extensions_repo_internal_idx on extensions (source_repo_url, internal_name);

-- status is an enum in all but name; a typo here silently hides a provider from the app.
-- Normalise first, otherwise the CHECK below would abort the whole migration on an old row.
update extensions set status = 'disabled'
  where status is null or status not in ('active', 'disabled', 'broken');

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'extensions_status_check'
  ) and exists (
    select 1 from information_schema.tables where table_name = 'extensions'
  ) then
    alter table extensions add constraint extensions_status_check
      check (status in ('active', 'disabled', 'broken'));
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- Repositories the admin subscribes to (CloudStream-style repo links)
-- ---------------------------------------------------------------------------
create table if not exists extension_repos (
  id uuid primary key default gen_random_uuid(),
  url text not null unique,             -- full link to repo.json
  name text not null,
  description text,
  enabled boolean not null default true, -- paused repos stop being refreshed; imported rows are kept
  last_synced_at timestamptz,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

alter table extension_repos enable row level security;

-- The user app may list repos (it uses no secrets here); writing is admin-only.
drop policy if exists "Public read enabled repos" on extension_repos;
create policy "Public read enabled repos"
  on extension_repos for select
  to anon, authenticated
  using (enabled = true);

drop policy if exists "Admin manages repos" on extension_repos;
create policy "Admin manages repos"
  on extension_repos for all
  to authenticated
  using (auth.role() = 'authenticated')
  with check (auth.role() = 'authenticated');

-- The existing "Public read active extensions" policy already restricts anon to status='active';
-- disabled and broken rows are therefore invisible to the app but editable in the admin panel.
-- Nothing to change there — this is listed so the intent is on record:
--   * 'active'   -> the app downloads and loads it
--   * 'disabled' -> deliberately unselected from a repo; the file stays published
--   * 'broken'   -> known-incompatible (wrong api_version / bad package); never loaded
