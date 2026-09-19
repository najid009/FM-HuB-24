-- FMHub24 app controls migration
-- Run this once in Supabase SQL Editor. It is safe to run again.

create table if not exists public.app_notices (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  message text not null,
  enabled boolean not null default true,
  priority integer not null default 0,
  published_at timestamptz not null default now(),
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.app_notices enable row level security;

drop policy if exists "Public read active notices" on public.app_notices;
create policy "Public read active notices"
on public.app_notices for select to anon, authenticated
using (
  enabled = true
  and (expires_at is null or expires_at > now())
);

drop policy if exists "Admin manages notices" on public.app_notices;
create policy "Admin manages notices"
on public.app_notices for all to authenticated
using (auth.role() = 'authenticated')
with check (auth.role() = 'authenticated');

create table if not exists public.app_releases (
  id uuid primary key default gen_random_uuid(),
  version_code integer not null unique,
  version_name text not null,
  download_url text not null,
  release_notes text,
  is_required boolean not null default false,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.app_releases enable row level security;

drop policy if exists "Public read enabled releases" on public.app_releases;
create policy "Public read enabled releases"
on public.app_releases for select to anon, authenticated
using (enabled = true);

drop policy if exists "Admin manages releases" on public.app_releases;
create policy "Admin manages releases"
on public.app_releases for all to authenticated
using (auth.role() = 'authenticated')
with check (auth.role() = 'authenticated');

-- Public APK download + authenticated admin upload.
insert into storage.buckets (id, name, public)
values ('app-releases', 'app-releases', true)
on conflict (id) do update set public = true;

drop policy if exists "Public read app releases" on storage.objects;
create policy "Public read app releases"
on storage.objects for select to anon, authenticated
using (bucket_id = 'app-releases');

drop policy if exists "Authenticated manage app releases" on storage.objects;
create policy "Authenticated manage app releases"
on storage.objects for all to authenticated
using (bucket_id = 'app-releases')
with check (bucket_id = 'app-releases');

-- Optional test rows: keep commented out unless you want to test the app immediately.
-- insert into public.app_notices (title, message, priority)
-- values ('Welcome to FMHub24', 'New movies and providers are now available.', 10);

-- Check after running:
-- select * from public.app_notices;
-- select * from public.app_releases;
-- select id, name, public from storage.buckets where id = 'app-releases';
