import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addRepo,
  deleteExtensionsOfRepo,
  fetchRepos,
  removeRepo,
  setRepoEnabled,
} from '../api/repos';
import { Extension, ExtensionRepo } from '../types/extension';
import { fetchAllExtensions } from '../api/extensions';
import RepoImportDialog from './RepoImportDialog';

/**
 * Repositories the app may load providers from — the admin-side half of the
 * "paste a CloudStream repo link, pick the servers" flow.
 */
export default function RepoManager() {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState('');
  const [inputError, setInputError] = useState('');
  const [browsing, setBrowsing] = useState<ExtensionRepo | null>(null);

  const repos = useQuery({ queryKey: ['extension-repos'], queryFn: fetchRepos });
  const extensions = useQuery({ queryKey: ['extensions'], queryFn: fetchAllExtensions });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['extension-repos'] });
    queryClient.invalidateQueries({ queryKey: ['extensions'] });
  };

  const add = useMutation({
    mutationFn: () => addRepo(url),
    onSuccess: repo => {
      setUrl('');
      setInputError('');
      invalidate();
      setBrowsing(repo);
    },
    onError: (err: Error) => setInputError(err.message),
  });

  const toggle = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => setRepoEnabled(id, enabled),
    onSuccess: invalidate,
  });

  const drop = useMutation({
    mutationFn: async ({ repo, alsoExtensions }: { repo: ExtensionRepo; alsoExtensions: boolean }) => {
      const removed = alsoExtensions ? await deleteExtensionsOfRepo(repo.url) : 0;
      await removeRepo(repo);
      return removed;
    },
    onSuccess: invalidate,
  });

  const [confirmDrop, setConfirmDrop] = useState<ExtensionRepo | null>(null);
  const [alsoDelete, setAlsoDelete] = useState(true);

  const countFor = (repoUrl: string) =>
    ((extensions.data ?? []) as Extension[]).filter(ext => ext.source_repo_url === repoUrl);

  return (
    <div className="space-y-6">
      <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5">
        <h3 className="text-white font-semibold mb-1">Add an extension repository</h3>
        <p className="text-xs text-zinc-500 mb-4 leading-relaxed">
          Paste a <code className="text-zinc-300">repo.json</code> link (the same kind you would put in
          CloudStream → Settings → Extensions → Add repository). We read its{' '}
          <code className="text-zinc-300">pluginLists</code>, show every provider it publishes, and you
          tick which ones the user app should load.
        </p>
        <div className="flex flex-col sm:flex-row gap-3">
          <input
            value={url}
            onChange={e => {
              setUrl(e.target.value);
              setInputError('');
            }}
            placeholder="https://raw.githubusercontent.com/user/repo/builds/repo.json"
            className="flex-1 px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white text-sm placeholder-zinc-600 focus:outline-none focus:ring-2 focus:ring-orange-500"
          />
          <button
            onClick={() => add.mutate()}
            disabled={add.isPending || !url.trim()}
            className="px-5 py-3 bg-gradient-to-r from-orange-500 to-orange-600 disabled:opacity-50 text-white rounded-xl text-sm font-semibold"
          >
            {add.isPending ? 'Reading…' : 'Add & browse'}
          </button>
        </div>
        {(inputError || (add.error as Error | null)?.message) && (
          <p className="mt-3 text-xs text-red-400 whitespace-pre-wrap">
            {inputError || (add.error as Error).message}
          </p>
        )}
        <p className="mt-3 text-[11px] text-zinc-600">
          Example: <span className="text-zinc-400">https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json</span>
        </p>
      </div>

      {repos.isLoading && <p className="text-zinc-500 text-sm">Loading repositories…</p>}

      {!repos.isLoading && (repos.data?.length ?? 0) === 0 && (
        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-10 text-center">
          <p className="text-white font-medium mb-1">No repositories yet</p>
          <p className="text-zinc-500 text-sm">
            Add one above, or keep uploading single <code>.cs3</code> files in the Extensions tab.
          </p>
        </div>
      )}

      {(repos.data?.length ?? 0) > 0 && (
        <div className="space-y-3">
          {repos.data!.map(repo => {
            const rows = countFor(repo.url);
            const active = rows.filter(ext => ext.status === 'active').length;
            return (
              <div
                key={repo.id}
                className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5 flex flex-col sm:flex-row sm:items-center gap-4"
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-white font-semibold truncate">{repo.name}</span>
                    {!repo.enabled && (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-400">
                        paused
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-zinc-500 truncate">{repo.url}</p>
                  <p className="text-[11px] text-zinc-600 mt-1">
                    {rows.length} imported ({active} active) ·{' '}
                    {repo.last_synced_at ? `last sync ${new Date(repo.last_synced_at).toLocaleString()}` : 'never synced'}
                  </p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => setBrowsing(repo)}
                    className="px-3 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs rounded-lg"
                  >
                    Servers / select
                  </button>
                  <button
                    onClick={() => toggle.mutate({ id: repo.id, enabled: !repo.enabled })}
                    className="px-3 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-xs rounded-lg"
                  >
                    {repo.enabled ? 'Pause' : 'Resume'}
                  </button>
                  <button
                    onClick={() => setConfirmDrop(repo)}
                    className="px-3 py-2 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 text-xs rounded-lg"
                  >
                    Remove
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {browsing && <RepoImportDialog repo={browsing} onClose={() => setBrowsing(null)} onSynced={invalidate} />}

      {confirmDrop && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 w-full max-w-md">
            <h3 className="text-lg font-semibold text-white mb-2">Remove repository?</h3>
            <p className="text-zinc-400 text-sm mb-4">
              <span className="text-white">{confirmDrop.name}</span> will stop being listed. Its{' '}
              {countFor(confirmDrop.url).length} imported row(s) can be removed with it.
            </p>
            <label className="flex items-center gap-2 text-sm text-zinc-300 mb-5">
              <input
                type="checkbox"
                checked={alsoDelete}
                onChange={e => setAlsoDelete(e.target.checked)}
                className="accent-orange-500 w-4 h-4"
              />
              Also delete the imported extension rows
            </label>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setConfirmDrop(null)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-xl text-sm"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  drop.mutate({ repo: confirmDrop, alsoExtensions: alsoDelete });
                  setConfirmDrop(null);
                }}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-xl text-sm font-medium"
              >
                Remove
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
