import { supabase } from '../lib/supabase';

export type Notice = {
  id: string;
  title: string;
  message: string;
  enabled: boolean;
  priority: number;
  published_at: string;
};

export type Release = {
  id: string;
  version_code: number;
  version_name: string;
  download_url: string;
  release_notes: string | null;
  is_required: boolean;
  enabled: boolean;
};

export async function fetchNotices(): Promise<Notice[]> {
  const { data, error } = await supabase.from('app_notices').select('*').order('priority', { ascending: false }).order('published_at', { ascending: false });
  if (error) throw error;
  return (data ?? []) as Notice[];
}

export async function createNotice(input: Pick<Notice, 'title' | 'message' | 'priority'>): Promise<Notice> {
  const { data, error } = await supabase.from('app_notices').insert({ ...input, enabled: true }).select().single();
  if (error) throw error;
  return data as Notice;
}

export async function toggleNotice(id: string, enabled: boolean): Promise<void> {
  const { error } = await supabase.from('app_notices').update({ enabled, updated_at: new Date().toISOString() }).eq('id', id);
  if (error) throw error;
}

export async function fetchReleases(): Promise<Release[]> {
  const { data, error } = await supabase.from('app_releases').select('*').order('version_code', { ascending: false });
  if (error) throw error;
  return (data ?? []) as Release[];
}

export async function publishRelease(file: File, versionName: string, versionCode: number, notes: string, required: boolean): Promise<Release> {
  if (!file.name.toLowerCase().endsWith('.apk')) throw new Error('Please select an APK file.');
  const path = `${versionCode}-${file.name.replace(/[^A-Za-z0-9._-]/g, '_')}`;
  const upload = await supabase.storage.from('app-releases').upload(path, file, { upsert: true, contentType: 'application/vnd.android.package-archive' });
  if (upload.error) throw upload.error;
  const { data: url } = supabase.storage.from('app-releases').getPublicUrl(path);
  const { data, error } = await supabase.from('app_releases').insert({
    version_code: versionCode,
    version_name: versionName,
    download_url: url.publicUrl,
    release_notes: notes || null,
    is_required: required,
    enabled: true,
  }).select().single();
  if (error) throw error;
  return data as Release;
}
