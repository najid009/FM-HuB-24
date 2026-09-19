import { useMemo, useState } from 'react';
import { Extension } from '../types/extension';
import { formatBytes } from '../api/repos';
import { FMHUB_PLUGIN_API_VERSION as HOST_PLUGIN_API_VERSION } from '../types/extension';
import { toggleExtensionStatus, deleteExtension } from '../api/extensions';
import { useMutation, useQueryClient } from '@tanstack/react-query';

interface Props {
  extensions: Extension[];
  onEdit: (ext: Extension) => void;
  isLoading?: boolean;
}

export default function ExtensionTable({ extensions, onEdit, isLoading }: Props) {
  const queryClient = useQueryClient();
  const [deleteConfirm, setDeleteConfirm] = useState<Extension | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [filter, setFilter] = useState('');

  // A repo import can put a hundred rows here at once, so the table needs a filter.
  const visible = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    if (!needle) return extensions;
    return extensions.filter(ext =>
      [ext.name, ext.internal_name, ext.plugin_class_name, ext.language, ext.status]
        .filter(Boolean)
        .some(field => String(field).toLowerCase().includes(needle))
    );
  }, [extensions, filter]);

  const toggleMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => toggleExtensionStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['extensions'] });
    },
    onError: (err: any) => setActionError(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: ({ id, fileUrl }: { id: string; fileUrl: string }) => deleteExtension(id, fileUrl),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['extensions'] });
      setDeleteConfirm(null);
    },
    onError: (err: any) => setActionError(err.message),
  });

  if (isLoading) {
    return (
      <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-zinc-800 rounded w-1/4"></div>
          <div className="space-y-3">
            {[1,2,3].map(i => <div key={i} className="h-12 bg-zinc-800 rounded"></div>)}
          </div>
        </div>
      </div>
    );
  }

  if (extensions.length === 0) {
    return (
      <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-12 text-center">
        <div className="w-16 h-16 bg-zinc-800 rounded-2xl flex items-center justify-center mx-auto mb-4">
          <span className="text-2xl">📦</span>
        </div>
        <h3 className="text-white font-semibold mb-2">No extensions yet</h3>
        <p className="text-zinc-400 text-sm">Upload your first .cs3 extension to get started.</p>
      </div>
    );
  }

  return (
    <>
      <div className="flex items-center gap-3 mb-3">
        <input
          value={filter}
          onChange={e => setFilter(e.target.value)}
          placeholder="Filter by name, class, language…"
          className="px-3 py-2 bg-zinc-900 border border-zinc-800 rounded-lg text-white text-sm w-72 placeholder-zinc-600 focus:outline-none focus:ring-2 focus:ring-orange-500"
        />
        <p className="text-xs text-zinc-600">
          {visible.length === extensions.length ? `${extensions.length} file(s)` : `${visible.length} of ${extensions.length}`}
        </p>
      </div>
      <div className="bg-zinc-900 border border-zinc-800 rounded-2xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-zinc-800 bg-zinc-900/50">
                <th className="text-left py-4 px-6 text-xs font-semibold text-zinc-400 uppercase tracking-wider">Name</th>
                <th className="text-left py-4 px-6 text-xs font-semibold text-zinc-400 uppercase tracking-wider">Version</th>
                <th className="text-left py-4 px-6 text-xs font-semibold text-zinc-400 uppercase tracking-wider">Language</th>
                <th className="text-left py-4 px-6 text-xs font-semibold text-zinc-400 uppercase tracking-wider">TvTypes</th>
                <th className="text-left py-4 px-6 text-xs font-semibold text-zinc-400 uppercase tracking-wider">Package</th>
                <th className="text-left py-4 px-6 text-xs font-semibold text-zinc-400 uppercase tracking-wider">Status</th>
                <th className="text-left py-4 px-6 text-xs font-semibold text-zinc-400 uppercase tracking-wider">Updated</th>
                <th className="text-right py-4 px-6 text-xs font-semibold text-zinc-400 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800/50">
              {visible.map((ext) => (
                <tr key={ext.id} className="hover:bg-zinc-800/30 transition">
                  <td className="py-4 px-6">
                    <div className="flex items-center gap-3">
                      {ext.icon_url ? (
                        <img src={ext.icon_url} alt={ext.name} className="w-8 h-8 rounded-lg object-cover bg-zinc-800" />
                      ) : (
                        <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-orange-500/20 to-orange-600/20 flex items-center justify-center">
                          <span className="text-xs font-bold text-orange-400">{ext.name.slice(0,2).toUpperCase()}</span>
                        </div>
                      )}
                      <div>
                        <div className="font-medium text-white">{ext.name}</div>
                        {ext.description && <div className="text-xs text-zinc-500 truncate max-w-[200px]">{ext.description}</div>}
                        <div className="text-[10px] text-zinc-600 flex items-center gap-1.5">
                          {ext.source_repo_url ? (
                            <a
                              href={ext.source_repo_url}
                              target="_blank"
                              rel="noreferrer"
                              className="text-blue-400/80 hover:text-blue-300"
                              title={`Imported from ${ext.source_repo_url}`}
                            >
                              from repo
                            </a>
                          ) : (
                            <span title="Uploaded directly to Supabase Storage">uploaded</span>
                          )}
                          {ext.requires_resources && <span title="requiresResources">· res</span>}
                          {ext.api_version != null && ext.api_version !== HOST_PLUGIN_API_VERSION && (
                            <span className="text-red-400" title={`Built for plugin API v${ext.api_version}, this app is v${HOST_PLUGIN_API_VERSION}`}>
                              · api v{ext.api_version}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="py-4 px-6 text-sm text-zinc-300">v{ext.version}</td>
                  <td className="py-4 px-6 text-sm text-zinc-400">{ext.language || '-'}</td>
                  <td className="py-4 px-6">
                    <div className="flex flex-wrap gap-1 max-w-[180px]">
                      {ext.tv_types?.slice(0,3).map((t) => (
                        <span key={t} className="px-2 py-0.5 bg-zinc-800 text-zinc-300 rounded-full text-[10px] font-medium">{t}</span>
                      ))}
                      {(ext.tv_types?.length || 0) > 3 && (
                        <span className="px-2 py-0.5 bg-zinc-800 text-zinc-500 rounded-full text-[10px]">+{ext.tv_types!.length - 3}</span>
                      )}
                    </div>
                  </td>
                  <td className="py-4 px-6">
                    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${
                      ext.status === 'active' 
                        ? 'bg-green-500/10 text-green-400 border-green-500/20' 
                        : ext.status === 'disabled'
                        ? 'bg-yellow-500/10 text-yellow-400 border-yellow-500/20'
                        : 'bg-red-500/10 text-red-400 border-red-500/20'
                    }`}>
                      <span className={`w-1.5 h-1.5 rounded-full mr-1.5 ${
                        ext.status === 'active' ? 'bg-green-400' : ext.status === 'disabled' ? 'bg-yellow-400' : 'bg-red-400'
                      }`}></span>
                      {ext.status}
                    </span>
                  </td>
                  <td className="py-4 px-6 text-xs text-zinc-500">
                    {ext.updated_at ? new Date(ext.updated_at).toLocaleDateString() : '-'}
                  </td>
                  <td className="py-4 px-6">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={() => onEdit(ext)}
                        className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-xs font-medium rounded-lg transition"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => toggleMutation.mutate({ id: ext.id, status: ext.status })}
                        disabled={toggleMutation.isPending}
                        className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-xs font-medium rounded-lg transition disabled:opacity-50"
                      >
                        {ext.status === 'active' ? 'Disable' : 'Enable'}
                      </button>
                      <button
                        onClick={() => setDeleteConfirm(ext)}
                        className="px-3 py-1.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 text-xs font-medium rounded-lg transition border border-red-500/20"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {actionError && (
          <div className="p-4 bg-red-500/10 border-t border-red-500/20 text-red-400 text-sm">
            Error: {actionError}
          </div>
        )}
      </div>

      {/* Delete Confirm Modal */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 w-full max-w-md">
            <h3 className="text-lg font-semibold text-white mb-2">Delete Extension?</h3>
            <p className="text-zinc-400 text-sm mb-6">
              This will permanently delete <span className="text-white font-medium">{deleteConfirm.name}</span> and its file from storage. This action cannot be undone.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setDeleteConfirm(null)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-xl text-sm font-medium transition"
              >
                Cancel
              </button>
              <button
                onClick={() => deleteMutation.mutate({ id: deleteConfirm.id, fileUrl: deleteConfirm.file_url })}
                disabled={deleteMutation.isPending}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-xl text-sm font-medium transition disabled:opacity-50"
              >
                {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
              </button>
            </div>
            {deleteMutation.isError && (
              <div className="mt-4 p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-xs">
                {(deleteMutation.error as any)?.message}
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
