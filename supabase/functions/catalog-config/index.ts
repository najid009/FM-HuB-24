import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

const headers = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Content-Type': 'application/json',
};

Deno.serve(async (request) => {
  if (request.method === 'OPTIONS') return new Response('ok', { headers });
  if (request.method !== 'GET') return new Response(JSON.stringify({ error: 'GET required' }), { status: 405, headers });

  const url = Deno.env.get('SUPABASE_URL');
  const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!url || !key) return new Response(JSON.stringify({ success: false, error: 'Service is not configured' }), { status: 503, headers });

  const admin = createClient(url, key);
  const { data, error } = await admin
    .from('app_categories')
    .select('id,slug,name,sort_order,max_items,app_provider_mappings(id,provider_key,provider_name,priority,language,region)')
    .eq('enabled', true)
    .order('sort_order', { ascending: true });

  const { data: repositories, error: repositoryError } = await admin
    .from('extension_repos')
    .select('name,url')
    .eq('enabled', true)
    .order('name', { ascending: true });
  if (repositoryError) return new Response(JSON.stringify({ success: false, error: repositoryError.message }), { status: 500, headers });

  return new Response(JSON.stringify({ success: true, categories: data ?? [], repositories: repositories ?? [], warning: error?.message, generatedAt: new Date().toISOString() }), { headers });
});
