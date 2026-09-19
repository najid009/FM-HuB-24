import { supabase } from '../lib/supabase';

export type AppRepository = {
  id: string;
  name: string;
  url: string;
  icon_url: string | null;
  priority: number;
  enabled: boolean;
  created_at: string;
  updated_at: string;
};

export async function fetchAppRepositories(): Promise<AppRepository[]> {
  const { data, error } = await supabase
    .from('app_extension_repositories')
    .select('*')
    .order('priority', { ascending: true })
    .order('name', { ascending: true });
  if (error) throw error;
  return (data ?? []) as AppRepository[];
}

export async function addAppRepository(input: {
  name: string;
  url: string;
  icon_url?: string;
  priority?: number;
}): Promise<AppRepository> {
  const url = input.url.trim();
  if (!url.startsWith('https://')) throw new Error('Repository URL must use HTTPS.');
  const { data, error } = await supabase
    .from('app_extension_repositories')
    .insert({
      name: input.name.trim() || 'FMHuB24 Repository',
      url,
      icon_url: input.icon_url?.trim() || null,
      priority: input.priority ?? 0,
      enabled: true,
    })
    .select('*')
    .single();
  if (error) throw error;
  return data as AppRepository;
}

export async function setAppRepositoryEnabled(id: string, enabled: boolean): Promise<void> {
  const { error } = await supabase
    .from('app_extension_repositories')
    .update({ enabled, updated_at: new Date().toISOString() })
    .eq('id', id);
  if (error) throw error;
}

export async function deleteAppRepository(id: string): Promise<void> {
  const { error } = await supabase.from('app_extension_repositories').delete().eq('id', id);
  if (error) throw error;
}
