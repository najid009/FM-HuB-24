import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  RepoFormatError,
  compatibility,
  fetchRepo,
  formatBytes,
  repoEntryToRow,
  touchRepo,
} from '../api/repos';
import { fetchAllExtensions, importRepoEntries, setExtensionsStatus } from '../api/extensions';
import { Extension, ExtensionRepo } from '../types/extension';
import { communityNote, communityNoteLabel } from '../data/communityCatalogue';

interface Props {
  repo: ExtensionRepo;
  onClose: () => void;
  onSynced?: () => void;
}

/**
 * CloudStream's "add repository" screen, on the admin side: every provider the repo publishes
 * is listed, and ticking one means "the user app must load this". Un-ticking keeps the row but
 * sets status='disabled', so nothing has to be re-downloaded when it comes back.
 */
export default function RepoImportDialog({ repo, onClose, onSynced }: Props) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [filter, setFilter] = useState('');

  const { data: extensions } = useQuery({
    queryKey: ['extensions'],
    queryFn: fetchAllExtensions,
  });

  const query = useQuery({
    queryKey: ['repo', repo.url],
    queryFn: () => fetchRepo(repo.url),
    retry: 0,
  });

  const imported = useMemo(() => {
    const map = new Map<string, Extension>();
    for (const ext of (extensions ?? []) as Extension[]) {
      if (ext.source_repo_url !== repo.url) continue;
      const key = ext.internal_name || ext.name;
      map.set(key, ext);
    }
    return map;
  }, [extensions, repo.url]);

  const plugins = query.data?.plugins ?? [];

  // First load: preselect everything compatible that is not imported yet.
  useEffect(() => {
    if (!plugins.length) return;
    setSelected(prev => {
      if (prev.size) return prev;
      return new Set(
        plugins
          .filter(entry => compatibility(entry).ok)
          .filter(entry => !imported.has(entry.internalName || entry.name))
          .map(entry => entry.internalName || entry.name)
      );
    });
  }, [plugins, imported]);

  const mutation = useMutation({
    mutationFn: async () => {
      const chosen = plugins.filter(entry => selected.has(entry.internalName || entry.name));
      const result = await importRepoEntries(repo.url, chosen, repoEntryToRow);
      // Anything from this repo that is no longer selected gets disabled rather than deleted,
      // so the user app stops loading it without losing the downloaded copy.
      const stillSelected = new Set(chosen.map(e => e.internalName || e.name));
      const toDisable = ((extensions ?? []) as Extension[])
        .filter(ext => ext.source_repo_url === repo.url && !stillSelected.has(ext.internal_name || ext.name))
        .filter(ext => ext.status === 'active')
        .map(ext => ext.id);
      if (toDisable.length) await setExtensionsStatus(toDisable, 'disabled');
      return { ...result, disabled: toDisable.length };
    },
    onSuccess: async () => {
      await touchRepo(repo.id).catch(() => undefined);
      await queryClient.invalidateQueries({ queryKey: ['extensions'] });
      await queryClient.invalidateQueries({ queryKey: ['extension-repos'] });
      onSynced?.();
      onClose();
    },
  });

  const error = query.error as RepoFormatError | null;

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
      <div className="bg-zinc-900 border border-zinc-800 rounded-2xl w-full max-w-4xl max-h-[85vh] flex flex-col">
        <div className="p-5 border-b border-zinc-800 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-white">{repo.name}</h2>
            <p className="text-xs text-zinc-500 break-all mt-1">{repo.url}</p>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-400 text-sm shrink-0"
          >
            ✕
          </button>
        </div>

        <div className="p-5 overflow-y-auto flex-1">
          {query.isLoading && <p className="text-zinc-400 text-sm">Reading repository…</p>}

          {error && (
            <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-300 text-sm whitespace-pre-wrap">
              {error.message}
            </div>
          )}

          {!query.isLoading && !error && plugins.length > 0 && (
            <>
              <div className="flex items-center justify-between gap-3 mb-3">
                <input
                  value={filter}
                  onChange={e => setFilter(e.target.value)}
                  placeholder="Filter providers…"
                  className="px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-white text-sm w-64 focus:outline-none focus:ring-2 focus:ring-orange-500"
                />
                <div className="flex gap-2 text-xs">
                  <button
                    onClick={() => setSelected(new Set(plugins.filter(p => compatibility(p).ok).map(p => p.internalName || p.name)))}
                    className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 rounded-lg text-zinc-300"
                  >
                    Select all
                  </button>
                  <button
                    onClick={() => setSelected(new Set())}
                    className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 rounded-lg text-zinc-300"
                  >
                    Clear
                  </button>
                </div>
              </div>

              <div className="border border-zinc-800 rounded-xl overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-zinc-900/60 text-zinc-400 text-xs uppercase tracking-wider">
                      <th className="w-10 px-3 py-2"></th>
                      <th className="text-left px-3 py-2">Provider</th>
                      <th className="text-left px-3 py-2">Ver</th>
                      <th className="text-left px-3 py-2">Lang</th>
                      <th className="text-left px-3 py-2">Size</th>
                      <th className="text-left px-3 py-2">API</th>
                      <th className="text-left px-3 py-2">State</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-800/60">
                    {plugins
                      .filter(p => !filter || p.name.toLowerCase().includes(filter.toLowerCase()))
                      .map(entry => {
                        const key = entry.internalName || entry.name;
                        const compat = compatibility(entry);
                        // What our own scan of the *published binary* said about this provider — see
                        // admin-panel/src/data/communityCatalogue.ts (regenerated from the docs JSON).
                        const scanned = communityNote(entry.name, entry.internalName);
                        const already = imported.get(key);
                        const checked = selected.has(key);
                        return (
                          <tr
                            key={key}
                            className={`transition ${checked ? 'bg-orange-500/5' : ''} ${
                              compat.ok ? '' : 'opacity-60'
                            }`}
                          >
                            <td className="px-3 py-2">
                              <input
                                type="checkbox"
                                checked={checked}
                                disabled={!compat.ok}
                                onChange={() =>
                                  setSelected(prev => {
                                    const next = new Set(prev);
                                    if (next.has(key)) next.delete(key);
                                    else next.add(key);
                                    return next;
                                  })
                                }
                                className="accent-orange-500 w-4 h-4"
                                title={compat.ok ? undefined : compat.reason}
                              />
                            </td>
                            <td className="px-3 py-2">
                              <div className="text-white">{entry.name}</div>
                              {entry.description && (
                                <div className="text-[11px] text-zinc-500 line-clamp-1 max-w-[320px]">
                                  {entry.description}
                                </div>
                              )}
                              {scanned && (
                                <div
                                  className={`text-[11px] max-w-[320px] ${
                                    scanned.verdict === 'NEEDS-ENTRY-SHIM' || scanned.verdict === 'UNREADABLE'
                                      ? 'text-red-400/80'
                                      : scanned.verdict?.startsWith('READY')
                                      ? 'text-green-400/80'
                                      : 'text-yellow-400/80'
                                  }`}
                                  title={communityNoteLabel(scanned)}
                                >
                                  scanned: {scanned.verdict}
                                  {scanned.appOnly.length > 0 && ` · app-only ${scanned.appOnly.slice(0, 3).join(', ')}`}
                                  {scanned.extendsPlugin && ' · extends app Plugin (cannot load here)'}
                                </div>
                              )}
                            </td>
                            <td className="px-3 py-2 text-zinc-400">v{entry.version}</td>
                            <td className="px-3 py-2 text-zinc-400">{entry.language ?? '-'}</td>
                            <td className="px-3 py-2 text-zinc-400">{formatBytes(entry.fileSize)}</td>
                            <td className="px-3 py-2">
                              <span
                                className={`text-[11px] px-2 py-0.5 rounded-full border ${
                                  entry.fmhubApiVersion == null
                                    ? 'text-zinc-400 border-zinc-700'
                                    : compat.ok
                                    ? 'text-green-400 border-green-500/20'
                                    : 'text-red-400 border-red-500/20'
                                }`}
                                title={compat.reason ?? 'no fmhubApiVersion: treated as a plain CloudStream plugin'}
                              >
                                {entry.fmhubApiVersion != null ? `v${entry.fmhubApiVersion}` : 'cs-only'}
                              </span>
                            </td>
                            <td className="px-3 py-2 text-[11px]">
                              {!compat.ok ? (
                                <span className="text-red-400">{compat.reason}</span>
                              ) : already ? (
                                <span className={already.status === 'active' ? 'text-green-400' : 'text-yellow-400'}>
                                  {already.status === 'active' ? 'active' : already.status}
                                </span>
                              ) : (
                                <span className="text-zinc-500">not imported</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                  </tbody>
                </table>
              </div>

              <p className="text-xs text-zinc-500 mt-3">
                {plugins.length} provider(s) in this repo · {selected.size} selected ·{' '}
                {imported.size} already in the app. Files are <em>not</em> copied into Supabase — the
                app downloads them from the repo URL, so keep the repo public.
              </p>
            </>
          )}

          {mutation.error && (
            <div className="mt-3 p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-xs">
              {(mutation.error as Error).message}
            </div>
          )}
        </div>

        <div className="p-5 border-t border-zinc-800 flex items-center justify-between gap-3">
          <button
            onClick={() => query.refetch()}
            className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-xl text-sm"
          >
            Re-read repo
          </button>
          <div className="flex gap-3">
            <button onClick={onClose} className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-xl text-sm">
              Cancel
            </button>
            <button
              onClick={() => mutation.mutate()}
              disabled={mutation.isPending || selected.size === 0}
              className="px-5 py-2 bg-gradient-to-r from-orange-500 to-orange-600 disabled:opacity-50 text-white rounded-xl text-sm font-semibold"
            >
              {mutation.isPending ? 'Applying…' : `Activate ${selected.size} provider(s)`}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
