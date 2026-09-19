import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
import { addAppRepository, AppRepository, deleteAppRepository, fetchAppRepositories, setAppRepositoryEnabled } from '../api/appRepositories';
import { addProviderMapping, Category, deleteProviderMapping, fetchCategories, fetchTmdbSettings, ProviderMapping, updateCategory, updateProviderMapping, updateTmdbSettings } from '../api/catalog';

type Tab = 'overview' | 'categories' | 'sources' | 'tmdb';

export default function Dashboard() {
  const { user, signOut } = useAuth();
  const [tab, setTab] = useState<Tab>('overview');
  const queryClient = useQueryClient();
  const categories = useQuery({ queryKey: ['catalog-categories'], queryFn: fetchCategories });
  const sources = useQuery({ queryKey: ['app-repositories'], queryFn: fetchAppRepositories });
  const tmdb = useQuery({ queryKey: ['tmdb-settings'], queryFn: fetchTmdbSettings });
  const invalidate = (key: string) => queryClient.invalidateQueries({ queryKey: [key] });

  const tabs: Array<{ id: Tab; label: string }> = [
    { id: 'overview', label: 'Overview' },
    { id: 'categories', label: 'Category rules' },
    { id: 'sources', label: 'App sources' },
    { id: 'tmdb', label: 'TMDB & Trending' },
  ];

  return (
    <div className="min-h-screen bg-zinc-950 text-white">
      <header className="border-b border-zinc-800 bg-zinc-900/70 sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between gap-4">
          <div>
            <p className="text-xs tracking-[0.3em] text-orange-400 uppercase">FMHuB24 control room</p>
            <h1 className="text-2xl font-bold">Catalogue administration</h1>
            <p className="text-xs text-zinc-500 mt-1">Route each category to the provider you choose.</p>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden sm:block text-xs text-zinc-500 truncate max-w-48">{user?.email}</span>
            <button onClick={signOut} className="px-3 py-2 bg-zinc-800 hover:bg-zinc-700 rounded-lg text-xs text-zinc-300">Sign out</button>
          </div>
        </div>
      </header>
      <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
        <div className="flex gap-1 mb-6 border-b border-zinc-800 overflow-x-auto">
          {tabs.map(item => <button key={item.id} onClick={() => setTab(item.id)} className={`px-4 py-2.5 text-sm font-medium rounded-t-lg border-b-2 -mb-px whitespace-nowrap ${tab === item.id ? 'border-orange-500 text-white bg-zinc-900/60' : 'border-transparent text-zinc-500 hover:text-zinc-300'}`}>{item.label}</button>)}
        </div>
        {tab === 'overview' && <Overview categories={categories.data ?? []} sources={sources.data ?? []} tmdbEnabled={tmdb.data?.enabled ?? false} />}
        {tab === 'categories' && <CategoryRules categories={categories.data ?? []} loading={categories.isLoading} onRefresh={() => invalidate('catalog-categories')} />}
        {tab === 'sources' && <SourceManager sources={sources.data ?? []} onRefresh={() => invalidate('app-repositories')} />}
        {tab === 'tmdb' && <TmdbPanel settings={tmdb.data} loading={tmdb.isLoading} onRefresh={() => invalidate('tmdb-settings')} />}
      </main>
    </div>
  );
}

function Overview({ categories, sources, tmdbEnabled }: { categories: Category[]; sources: AppRepository[]; tmdbEnabled: boolean }) {
  const mapped = categories.reduce((count, item) => count + (item.app_provider_mappings?.filter(mapping => mapping.enabled).length ?? 0), 0);
  return <div className="space-y-6">
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4"><Stat label="Categories" value={categories.filter(item => item.enabled).length} /><Stat label="Provider routes" value={mapped} /><Stat label="App sources" value={sources.filter(item => item.enabled).length} /><Stat label="TMDB trending" value={tmdbEnabled ? 'ON' : 'OFF'} tone={tmdbEnabled ? 'good' : 'neutral'} /></div>
    <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6"><h2 className="text-lg font-semibold mb-2">How FMHuB24 loads content</h2><p className="text-sm text-zinc-400 leading-relaxed">The app does not merge every provider into every screen. A category uses only its enabled provider routes, ordered by priority. Trending is sourced from the secure TMDB function and resolved against the provider rules.</p><div className="mt-5 grid md:grid-cols-3 gap-3 text-sm"><Flow title="1. Category" text="Movies, anime, web series, drama" /><Flow title="2. Provider route" text="Admin-selected extension/provider" /><Flow title="3. App result" text="Deduplicated catalogue item" /></div></div>
  </div>;
}

function CategoryRules({ categories, loading, onRefresh }: { categories: Category[]; loading: boolean; onRefresh: () => void }) {
  const [draft, setDraft] = useState<Record<string, { key: string; name: string }>>({});
  const update = useMutation({ mutationFn: ({ id, patch }: { id: string; patch: Parameters<typeof updateCategory>[1] }) => updateCategory(id, patch), onSuccess: onRefresh });
  const add = useMutation({ mutationFn: addProviderMapping, onSuccess: onRefresh });
  const remove = useMutation({ mutationFn: deleteProviderMapping, onSuccess: onRefresh });
  if (loading) return <Loading />;
  return <div className="space-y-4">{categories.map(category => { const value = draft[category.id] ?? { key: '', name: '' }; return <section key={category.id} className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5">
    <div className="flex flex-col sm:flex-row sm:items-center gap-3 justify-between"><div><h2 className="font-semibold text-white">{category.name}</h2><p className="text-xs text-zinc-500">slug: {category.slug} · {category.app_provider_mappings?.filter(item => item.enabled).length ?? 0} active route(s)</p></div><div className="flex gap-2"><button onClick={() => update.mutate({ id: category.id, patch: { enabled: !category.enabled } })} className={`px-3 py-2 rounded-lg text-xs ${category.enabled ? 'bg-green-500/10 text-green-400' : 'bg-zinc-800 text-zinc-500'}`}>{category.enabled ? 'Enabled' : 'Disabled'}</button><input aria-label="max items" value={category.max_items} onChange={event => update.mutate({ id: category.id, patch: { max_items: Number(event.target.value) || 20 } })} className="w-20 px-2 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-xs" /></div></div>
    <div className="mt-4 space-y-2">{(category.app_provider_mappings ?? []).map(mapping => <MappingRow key={mapping.id} mapping={mapping} onToggle={() => updateProviderMapping(mapping.id, { enabled: !mapping.enabled }).then(onRefresh)} onDelete={() => remove.mutate(mapping.id)} />)}</div>
    <div className="mt-4 grid sm:grid-cols-[1fr_1fr_auto] gap-2"><input value={value.key} onChange={event => setDraft({ ...draft, [category.id]: { ...value, key: event.target.value } })} placeholder="provider key / internal name" className="px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-xs" /><input value={value.name} onChange={event => setDraft({ ...draft, [category.id]: { ...value, name: event.target.value } })} placeholder="provider display name" className="px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-xs" /><button disabled={!value.key.trim() || add.isPending} onClick={() => { add.mutate({ category_id: category.id, provider_key: value.key.trim(), provider_name: value.name.trim() || value.key.trim(), priority: 0, enabled: true, language: null, region: null }); setDraft({ ...draft, [category.id]: { key: '', name: '' } }); }} className="px-4 py-2 bg-orange-600 disabled:opacity-40 rounded-lg text-xs font-semibold">Add route</button></div>
  </section>; })}</div>;
}

function MappingRow({ mapping, onToggle, onDelete }: { mapping: ProviderMapping; onToggle: () => void; onDelete: () => void }) { return <div className="flex items-center gap-3 bg-zinc-950/60 border border-zinc-800 rounded-xl px-3 py-2"><div className="flex-1 min-w-0"><p className="text-sm text-zinc-200 truncate">{mapping.provider_name}</p><p className="text-[11px] text-zinc-600 truncate">{mapping.provider_key} · priority {mapping.priority}</p></div><button onClick={onToggle} className={`text-xs ${mapping.enabled ? 'text-green-400' : 'text-zinc-600'}`}>{mapping.enabled ? 'active' : 'off'}</button><button onClick={onDelete} className="text-xs text-red-400">Remove</button></div>; }

function SourceManager({ sources, onRefresh }: { sources: AppRepository[]; onRefresh: () => void }) {
  const [name, setName] = useState(''); const [url, setUrl] = useState('');
  const add = useMutation({ mutationFn: () => addAppRepository({ name, url }), onSuccess: () => { setName(''); setUrl(''); onRefresh(); } });
  const toggle = useMutation({ mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => setAppRepositoryEnabled(id, enabled), onSuccess: onRefresh });
  const remove = useMutation({ mutationFn: deleteAppRepository, onSuccess: onRefresh });
  return <div className="space-y-4"><div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5"><h2 className="font-semibold">Approved app sources</h2><p className="text-xs text-zinc-500 mt-1 mb-4">Only these HTTPS CloudStream repositories are delivered to the FMHuB24 app.</p><div className="grid sm:grid-cols-[1fr_2fr_auto] gap-2"><input value={name} onChange={event => setName(event.target.value)} placeholder="Source name" className="px-3 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-sm" /><input value={url} onChange={event => setUrl(event.target.value)} placeholder="https://.../repo.json" className="px-3 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-sm" /><button onClick={() => add.mutate()} disabled={add.isPending || !url.trim()} className="px-4 py-3 bg-orange-600 disabled:opacity-40 rounded-xl text-sm font-semibold">Add</button></div>{add.error && <p className="text-xs text-red-400 mt-2">{(add.error as Error).message}</p>}</div>{sources.map(source => <div key={source.id} className="bg-zinc-900 border border-zinc-800 rounded-2xl p-4 flex items-center gap-4"><div className="flex-1 min-w-0"><p className="font-medium truncate">{source.name}</p><p className="text-xs text-zinc-500 truncate">{source.url}</p></div><button onClick={() => toggle.mutate({ id: source.id, enabled: !source.enabled })} className="text-xs text-zinc-300">{source.enabled ? 'Pause' : 'Enable'}</button><button onClick={() => remove.mutate(source.id)} className="text-xs text-red-400">Remove</button></div>)}</div>;
}

function TmdbPanel({ settings, loading, onRefresh }: { settings?: { enabled: boolean; trending_window: 'day' | 'week'; max_items: number; refresh_minutes: number }; loading: boolean; onRefresh: () => void }) {
  const [endpoint, setEndpoint] = useState('');
  const save = useMutation({ mutationFn: (patch: Record<string, unknown>) => updateTmdbSettings(patch), onSuccess: onRefresh });
  if (loading || !settings) return <Loading />;
  return <div className="space-y-4"><section className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6"><h2 className="font-semibold">TMDB trending</h2><p className="text-xs text-zinc-500 mt-1 mb-5">The token stays in the Supabase Edge Function secret <code>TMDB_READ_ACCESS_TOKEN</code>. This panel only controls public behaviour.</p><div className="grid sm:grid-cols-2 gap-4"><label className="flex items-center gap-3 text-sm"><input type="checkbox" checked={settings.enabled} onChange={event => save.mutate({ enabled: event.target.checked })} className="accent-orange-500" /> Enable trending</label><select value={settings.trending_window} onChange={event => save.mutate({ trending_window: event.target.value })} className="px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-sm"><option value="day">Today</option><option value="week">This week</option></select><label className="text-xs text-zinc-500">Maximum items<input type="number" min="1" max="50" value={settings.max_items} onChange={event => save.mutate({ max_items: Number(event.target.value) || 20 })} className="block mt-1 w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-sm text-white" /></label><label className="text-xs text-zinc-500">Refresh minutes<input type="number" min="5" value={settings.refresh_minutes} onChange={event => save.mutate({ refresh_minutes: Number(event.target.value) || 30 })} className="block mt-1 w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-sm text-white" /></label></div></section><section className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6"><h2 className="font-semibold">Mobile endpoint</h2><p className="text-xs text-zinc-500 mt-1">After deploying the function, set this URL in the Android build configuration.</p><input value={endpoint} onChange={event => setEndpoint(event.target.value)} placeholder="https://PROJECT.supabase.co/functions/v1/tmdb-trending" className="mt-3 w-full px-3 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-sm" /></section></div>;
}

function Stat({ label, value, tone = 'neutral' }: { label: string; value: string | number; tone?: 'neutral' | 'good' }) { return <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5"><p className={`text-3xl font-bold ${tone === 'good' ? 'text-green-400' : 'text-white'}`}>{value}</p><p className="text-xs text-zinc-500 mt-1">{label}</p></div>; }
function Flow({ title, text }: { title: string; text: string }) { return <div className="rounded-xl border border-zinc-800 bg-zinc-950/60 p-4"><p className="text-orange-400 text-xs font-semibold">{title}</p><p className="text-zinc-300 text-sm mt-1">{text}</p></div>; }
function Loading() { return <div className="text-zinc-500 text-sm py-10">Loading configuration…</div>; }
