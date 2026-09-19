import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addAppRepository,
  deleteAppRepository,
  fetchAppRepositories,
  setAppRepositoryEnabled,
  AppRepository,
} from '../api/appRepositories';

export default function AppRepositoryManager() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [priority, setPriority] = useState('0');
  const [error, setError] = useState('');
  const repos = useQuery({ queryKey: ['app-repositories'], queryFn: fetchAppRepositories });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['app-repositories'] });

  const add = useMutation({
    mutationFn: () => addAppRepository({ name, url, priority: Number(priority) || 0 }),
    onSuccess: () => {
      setName('');
      setUrl('');
      setPriority('0');
      setError('');
      invalidate();
    },
    onError: (err: Error) => setError(err.message),
  });
  const toggle = useMutation({
    mutationFn: ({ repo, enabled }: { repo: AppRepository; enabled: boolean }) =>
      setAppRepositoryEnabled(repo.id, enabled),
    onSuccess: invalidate,
  });
  const remove = useMutation({ mutationFn: deleteAppRepository, onSuccess: invalidate });

  return (
    <div className="space-y-6">
      <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5">
        <h3 className="text-white font-semibold mb-1">FMHuB24 app repositories</h3>
        <p className="text-xs text-zinc-500 mb-4 leading-relaxed">
          These HTTPS repository URLs are delivered to the FMHuB24 CloudStream fork at startup.
          This controls the app allowlist; it does not upload or rewrite third-party extensions.
        </p>
        <div className="grid sm:grid-cols-3 gap-3">
          <input value={name} onChange={e => setName(e.target.value)} placeholder="Repository name" className="px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white text-sm" />
          <input value={url} onChange={e => setUrl(e.target.value)} placeholder="https://.../repo.json" className="px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white text-sm sm:col-span-2" />
          <input value={priority} onChange={e => setPriority(e.target.value)} type="number" placeholder="Priority" className="px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white text-sm" />
          <button onClick={() => add.mutate()} disabled={add.isPending || !url.trim()} className="sm:col-span-2 px-5 py-3 bg-gradient-to-r from-orange-500 to-orange-600 disabled:opacity-50 text-white rounded-xl text-sm font-semibold">
            {add.isPending ? 'Saving…' : 'Add app repository'}
          </button>
        </div>
        {error && <p className="mt-3 text-xs text-red-400">{error}</p>}
      </div>

      {repos.isLoading && <p className="text-zinc-500 text-sm">Loading app repositories…</p>}
      {(repos.data ?? []).map(repo => (
        <div key={repo.id} className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5 flex flex-col sm:flex-row sm:items-center gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-white font-semibold truncate">{repo.name}</span>
              {!repo.enabled && <span className="text-[10px] px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-400">paused</span>}
            </div>
            <p className="text-xs text-zinc-500 truncate">{repo.url}</p>
            <p className="text-[11px] text-zinc-600 mt-1">Priority {repo.priority} · {repo.enabled ? 'delivered to app' : 'not delivered'}</p>
          </div>
          <div className="flex gap-2 shrink-0">
            <button onClick={() => toggle.mutate({ repo, enabled: !repo.enabled })} className="px-3 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-xs rounded-lg">{repo.enabled ? 'Pause' : 'Enable'}</button>
            <button onClick={() => remove.mutate(repo.id)} className="px-3 py-2 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 text-xs rounded-lg">Remove</button>
          </div>
        </div>
      ))}
      {!repos.isLoading && (repos.data ?? []).length === 0 && <p className="text-zinc-500 text-sm">No app repositories configured yet.</p>}
    </div>
  );
}
