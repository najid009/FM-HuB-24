-- FMHuB24 CloudStream fork: admin-controlled extension repositories.
-- The Android fork reads enabled rows through Supabase REST (or an equivalent JSON endpoint).
create table if not exists public.app_extension_repositories (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  url text not null,
  icon_url text,
  priority integer not null default 0,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint app_extension_repositories_https_url check (url ~ '^https://'),
  constraint app_extension_repositories_unique_url unique (url)
);

alter table public.app_extension_repositories enable row level security;

drop policy if exists "Public read enabled extension repositories" on public.app_extension_repositories;
create policy "Public read enabled extension repositories"
on public.app_extension_repositories for select to anon, authenticated
using (enabled = true);

drop policy if exists "Admin manages extension repositories" on public.app_extension_repositories;
create policy "Admin manages extension repositories"
on public.app_extension_repositories for all to authenticated
using (auth.role() = 'authenticated')
with check (auth.role() = 'authenticated');

create index if not exists app_extension_repositories_enabled_priority_idx
  on public.app_extension_repositories (enabled, priority);

-- Example REST URL for the Android build field:
-- https://<project>.supabase.co/rest/v1/app_extension_repositories?enabled=eq.true&select=name,url,icon_url&order=priority.asc
