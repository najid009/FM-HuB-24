import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

export const isSupabaseConfigured = Boolean(supabaseUrl && supabaseAnonKey);

// Keep the module importable so the app can render a useful setup screen when .env is missing.
export const supabase = createClient(
  supabaseUrl || 'https://configuration-missing.invalid',
  supabaseAnonKey || 'missing-anon-key',
  { auth: { persistSession: true, autoRefreshToken: true } },
);

export const STORAGE_BUCKET = 'extensions';
