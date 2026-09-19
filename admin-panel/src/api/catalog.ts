import { supabase } from '../lib/supabase';

export type ProviderMapping = {
  id: string;
  category_id: string;
  provider_key: string;
  provider_name: string;
  priority: number;
  enabled: boolean;
  language: string | null;
  region: string | null;
};
export type Category = {
  id: string;
  slug: string;
  name: string;
  sort_order: number;
  enabled: boolean;
  max_items: number;
  app_provider_mappings: ProviderMapping[];
};
export type TmdbSettings = {
  id: boolean;
  enabled: boolean;
  trending_window: 'day' | 'week';
  max_items: number;
  refresh_minutes: number;
};

export async function fetchCategories(): Promise<Category[]> {
  const { data, error } = await supabase
    .from('app_categories')
    .select('*, app_provider_mappings(*)')
    .order('sort_order', { ascending: true });
  if (error) throw error;
  return (data ?? []) as Category[];
}

export async function updateCategory(id: string, patch: Partial<Pick<Category, 'name' | 'sort_order' | 'enabled' | 'max_items'>>): Promise<void> {
  const { error } = await supabase.from('app_categories').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', id);
  if (error) throw error;
}

export async function addProviderMapping(input: Omit<ProviderMapping, 'id' | 'category_id'> & { category_id: string }): Promise<void> {
  const { error } = await supabase.from('app_provider_mappings').insert({ ...input, updated_at: new Date().toISOString() });
  if (error) throw error;
}

export async function updateProviderMapping(id: string, patch: Partial<ProviderMapping>): Promise<void> {
  const { error } = await supabase.from('app_provider_mappings').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', id);
  if (error) throw error;
}

export async function deleteProviderMapping(id: string): Promise<void> {
  const { error } = await supabase.from('app_provider_mappings').delete().eq('id', id);
  if (error) throw error;
}

export async function fetchTmdbSettings(): Promise<TmdbSettings> {
  const { data, error } = await supabase.from('tmdb_settings').select('*').eq('id', true).single();
  if (error) throw error;
  return data as TmdbSettings;
}

export async function updateTmdbSettings(patch: Partial<Omit<TmdbSettings, 'id'>>): Promise<void> {
  const { error } = await supabase.from('tmdb_settings').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', true);
  if (error) throw error;
}

export async function fetchTrendingCache(): Promise<{ items: Array<{ title: string; mediaType: string; id: number }>; cached_at: string } | null> {
  const { data, error } = await supabase.from('trending_cache').select('items,cached_at').eq('id', true).maybeSingle();
  if (error) throw error;
  return data as { items: Array<{ title: string; mediaType: string; id: number }>; cached_at: string } | null;
}
