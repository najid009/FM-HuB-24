import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Content-Type': 'application/json',
};

type TrendItem = {
  id: number;
  mediaType: string;
  title: string;
  originalTitle?: string;
  overview?: string;
  posterPath?: string | null;
  backdropPath?: string | null;
  releaseDate?: string | null;
  popularity?: number;
  voteAverage?: number;
};

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function normalize(item: Record<string, unknown>): TrendItem {
  const mediaType = String(item.media_type ?? (item.first_air_date ? 'tv' : 'movie'));
  const title = String(item.title ?? item.name ?? '').trim();
  return {
    id: Number(item.id),
    mediaType,
    title,
    originalTitle: String(item.original_title ?? item.original_name ?? '').trim() || undefined,
    overview: String(item.overview ?? '').trim() || undefined,
    posterPath: (item.poster_path as string | null | undefined) ?? null,
    backdropPath: (item.backdrop_path as string | null | undefined) ?? null,
    releaseDate: (item.release_date ?? item.first_air_date ?? null) as string | null,
    popularity: Number(item.popularity ?? 0),
    voteAverage: Number(item.vote_average ?? 0),
  };
}

Deno.serve(async (request) => {
  if (request.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  if (request.method !== 'GET') return response({ error: 'GET required' }, 405);

  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  const token = Deno.env.get('TMDB_READ_ACCESS_TOKEN');
  if (!supabaseUrl || !serviceRoleKey || !token) {
    return response({ error: 'TMDB service is not configured' }, 503);
  }

  const admin = createClient(supabaseUrl, serviceRoleKey);
  const { data: settings } = await admin.from('tmdb_settings').select('*').eq('id', true).maybeSingle();
  if (settings?.enabled === false) return response({ success: false, items: [], disabled: true });

  const window = settings?.trending_window === 'week' ? 'week' : 'day';
  const maxItems = Math.min(Math.max(Number(settings?.max_items ?? 20), 1), 50);
  const refreshMinutes = Math.min(Math.max(Number(settings?.refresh_minutes ?? 30), 5), 1440);
  const { data: cached } = await admin.from('trending_cache').select('*').eq('id', true).maybeSingle();
  const cacheFresh = cached && new Date(cached.expires_at).getTime() > Date.now();
  if (cacheFresh) {
    return response({ success: true, source: 'cache', window: cached.window, items: cached.items, cachedAt: cached.cached_at });
  }

  try {
    const tmdbResponse = await fetch(`https://api.themoviedb.org/3/trending/all/${window}?language=en-US`, {
      headers: { Authorization: `Bearer ${token}`, accept: 'application/json' },
    });
    if (!tmdbResponse.ok) throw new Error(`TMDB returned ${tmdbResponse.status}`);
    const payload = await tmdbResponse.json();
    const items = (Array.isArray(payload.results) ? payload.results : [])
      .map((item: Record<string, unknown>) => normalize(item))
      .filter((item: TrendItem) => item.id && item.title)
      .slice(0, maxItems);
    const now = new Date();
    const expires = new Date(now.getTime() + refreshMinutes * 60_000);
    await admin.from('trending_cache').upsert({ id: true, source: 'tmdb', window, items, cached_at: now.toISOString(), expires_at: expires.toISOString() });
    return response({ success: true, source: 'tmdb', window, items, cachedAt: now.toISOString() });
  } catch (error) {
    if (cached) {
      return response({ success: true, source: 'stale-cache', window: cached.window, items: cached.items, cachedAt: cached.cached_at, warning: String(error) });
    }
    return response({ success: false, items: [], error: 'TMDB unavailable and no cache exists' }, 502);
  }
});
