-- FMHuB24 catalogue rules: one controlled provider map per category.
create table if not exists public.app_categories (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  sort_order integer not null default 0,
  enabled boolean not null default true,
  max_items integer not null default 20,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.app_provider_mappings (
  id uuid primary key default gen_random_uuid(),
  category_id uuid not null references public.app_categories(id) on delete cascade,
  provider_key text not null,
  provider_name text not null,
  priority integer not null default 0,
  enabled boolean not null default true,
  language text,
  region text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(category_id, provider_key)
);

create table if not exists public.tmdb_settings (
  id boolean primary key default true check (id = true),
  enabled boolean not null default true,
  trending_window text not null default 'day' check (trending_window in ('day', 'week')),
  max_items integer not null default 20,
  refresh_minutes integer not null default 30,
  updated_at timestamptz not null default now()
);
insert into public.tmdb_settings (id) values (true) on conflict (id) do nothing;

create table if not exists public.trending_cache (
  id boolean primary key default true check (id = true),
  source text not null default 'tmdb',
  window text not null default 'day',
  items jsonb not null default '[]'::jsonb,
  cached_at timestamptz not null default now(),
  expires_at timestamptz not null default now()
);

alter table public.app_categories enable row level security;
alter table public.app_provider_mappings enable row level security;
alter table public.tmdb_settings enable row level security;
alter table public.trending_cache enable row level security;

drop policy if exists "Public read enabled categories" on public.app_categories;
create policy "Public read enabled categories" on public.app_categories for select to anon, authenticated using (enabled = true);
drop policy if exists "Authenticated manages categories" on public.app_categories;
create policy "Authenticated manages categories" on public.app_categories for all to authenticated using (auth.role() = 'authenticated') with check (auth.role() = 'authenticated');

drop policy if exists "Public read enabled mappings" on public.app_provider_mappings;
create policy "Public read enabled mappings" on public.app_provider_mappings for select to anon, authenticated using (enabled = true);
drop policy if exists "Authenticated manages mappings" on public.app_provider_mappings;
create policy "Authenticated manages mappings" on public.app_provider_mappings for all to authenticated using (auth.role() = 'authenticated') with check (auth.role() = 'authenticated');

drop policy if exists "Public read TMDB settings" on public.tmdb_settings;
create policy "Public read TMDB settings" on public.tmdb_settings for select to anon, authenticated using (true);
drop policy if exists "Authenticated manages TMDB settings" on public.tmdb_settings;
create policy "Authenticated manages TMDB settings" on public.tmdb_settings for all to authenticated using (auth.role() = 'authenticated') with check (auth.role() = 'authenticated');

drop policy if exists "Public read trending cache" on public.trending_cache;
create policy "Public read trending cache" on public.trending_cache for select to anon, authenticated using (true);
drop policy if exists "Authenticated manages trending cache" on public.trending_cache;
create policy "Authenticated manages trending cache" on public.trending_cache for all to authenticated using (auth.role() = 'authenticated') with check (auth.role() = 'authenticated');

create index if not exists app_categories_enabled_sort_idx on public.app_categories(enabled, sort_order);
create index if not exists app_provider_mappings_category_priority_idx on public.app_provider_mappings(category_id, enabled, priority);

insert into public.app_categories (slug, name, sort_order) values
  ('trending', 'Trending', 10),
  ('movies', 'Movies', 20),
  ('anime', 'Anime', 30),
  ('web-series', 'Web Series', 40),
  ('series', 'Series', 50),
  ('drama', 'Drama', 60)
on conflict (slug) do nothing;

-- The token is deliberately not stored in this table. Set it as the
-- Supabase Edge Function secret TMDB_READ_ACCESS_TOKEN.
