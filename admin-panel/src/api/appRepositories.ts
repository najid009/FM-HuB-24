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

type ExistingRepository = {
  id: string;
  url: string;
  name: string;
  enabled: boolean;
  created_at: string;
  updated_at: string;
};

function normalize(row: ExistingRepository): AppRepository {
  return { ...row, icon_url: null, priority: 0 };
}

export async function fetchAppRepositories(): Promise<AppRepository[]> {
  const { data, error } = await supabase
    .from('extension_repos')
    .select('id,url,name,enabled,created_at,updated_at')
    .order('name', { ascending: true });
  if (error) throw error;
  return ((data ?? []) as ExistingRepository[]).map(normalize);
}

export async function addAppRepository(input: { name: string; url: string; icon_url?: string; priority?: number }): Promise<AppRepository> {
  const url = input.url.trim();
  if (!url.startsWith('https://')) throw new Error('Repository URL must use HTTPS.');
  if (!url.endsWith('repo.json') && !url.includes('/repo')) {
    throw new Error('Enter the public CloudStream repo.json URL, not a GitHub project page.');
  }
  const { data, error } = await supabase
    .from('extension_repos')
    .insert({ name: input.name.trim() || 'FMHuB24 Repository', url, enabled: true })
    .select('id,url,name,enabled,created_at,updated_at')
    .single();
  if (error) {
    if (error.code === '23505') throw new Error('This repository URL is already added.');
    if (error.code === '42501') throw new Error('Your logged-in Supabase user is not allowed to manage repositories.');
    throw new Error(error.message);
  }
  return normalize(data as ExistingRepository);
}

export async function setAppRepositoryEnabled(id: string, enabled: boolean): Promise<void> {
  const { error } = await supabase.from('extension_repos').update({ enabled, updated_at: new Date().toISOString() }).eq('id', id);
  if (error) throw new Error(error.code === '42501' ? 'Admin permission is required to change this repository.' : error.message);
}

export async function deleteAppRepository(id: string): Promise<void> {
  const { error } = await supabase.from('extension_repos').delete().eq('id', id);
  if (error) throw new Error(error.code === '42501' ? 'Admin permission is required to delete this repository.' : error.message);
}
