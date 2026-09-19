import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchAllExtensions } from '../api/extensions';
import { fetchRepos } from '../api/repos';
import UploadForm from '../components/UploadForm';
import ExtensionTable from '../components/ExtensionTable';
import EditModal from '../components/EditModal';
import RepoManager from '../components/RepoManager';
import AppControlPanel from '../components/AppControlPanel';
import AppRepositoryManager from '../components/AppRepositoryManager';
import { Extension } from '../types/extension';

type Tab = 'extensions' | 'repos' | 'app-repos' | 'controls';

export default function Dashboard() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('extensions');
  const [editingExtension, setEditingExtension] = useState<Extension | null>(null);
  const [uploading, setUploading] = useState(false);

  const {
    data: extensions,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['extensions'],
    queryFn: fetchAllExtensions,
  });

  const repos = useQuery({ queryKey: ['extension-repos'], queryFn: fetchRepos });

  const active = extensions?.filter(e => e.status === 'active').length ?? 0;
  const repoCount = repos.data?.length ?? 0;
  const fromRepos = extensions?.filter(e => e.source_repo_url).length ?? 0;
  const incompatible = extensions?.filter(e => e.api_version && e.api_version !== 1).length ?? 0;

  const tabs: Array<{ id: Tab; label: string; count: number }> = [
    { id: 'extensions', label: 'Extensions', count: extensions?.length ?? 0 },
    { id: 'repos', label: 'Repositories', count: repoCount },
    { id: 'app-repos', label: 'App sources', count: 0 },
    { id: 'controls', label: 'Notices & Updates', count: 0 },
  ];

  return (
    <div className="min-h-screen bg-zinc-950 text-white">
      <header className="border-b border-zinc-800 bg-zinc-900/50 backdrop-blur-sm sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold bg-gradient-to-r from-orange-400 to-orange-600 bg-clip-text text-transparent">
              FM-HuB 24 Admin
            </h1>
            <p className="text-zinc-400 text-sm">Extension management for the user app</p>
          </div>
          <div className="flex gap-4 text-sm">
            <div className="text-right hidden sm:block">
              <p className="text-2xl font-bold text-green-400">{active}</p>
              <p className="text-zinc-500">Active</p>
            </div>
            <div className="text-right hidden sm:block">
              <p className="text-2xl font-bold text-orange-400">{repoCount}</p>
              <p className="text-zinc-500">Repos</p>
            </div>
            <div className="text-right hidden sm:block">
              <p className="text-2xl font-bold text-blue-400">{fromRepos}</p>
              <p className="text-zinc-500">From repos</p>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
        <div className="flex gap-1 mb-6 border-b border-zinc-800">
          {tabs.map(item => (
            <button
              key={item.id}
              onClick={() => setTab(item.id)}
              className={`px-4 py-2.5 text-sm font-medium rounded-t-lg border-b-2 -mb-px transition ${
                tab === item.id
                  ? 'border-orange-500 text-white bg-zinc-900/60'
                  : 'border-transparent text-zinc-500 hover:text-zinc-300'
              }`}
            >
              {item.label}
              <span className="ml-2 text-xs px-1.5 py-0.5 rounded-full bg-zinc-800 text-zinc-400">
                {item.count}
              </span>
            </button>
          ))}
        </div>

        {tab === 'extensions' && (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
              <Stat label="Total files" value={extensions?.length ?? 0} />
              <Stat label="Active (loaded by the app)" value={active} tone={active > 0 ? 'good' : 'neutral'} />
              <Stat label="Disabled / not selected" value={(extensions?.length ?? 0) - active} />
              <Stat
                label="API mismatch"
                value={incompatible}
                tone={incompatible > 0 ? 'bad' : 'good'}
                hint="rows whose plugin API version is not the one this app ships (currently v1) — they are reported as broken instead of being loaded"
              />
            </div>

            <div className="flex items-center justify-between gap-3 mb-4">
              <p className="text-sm text-zinc-500">
                {fromRepos > 0
                  ? `${fromRepos} of these came from a repository and keep their repo link for update checks.`
                  : 'Single-file uploads only so far — the Repositories tab can pull in a whole CloudStream repo.'}
              </p>
              <button
                onClick={() => setUploading(true)}
                className="px-4 py-2 bg-gradient-to-r from-orange-500 to-orange-600 text-white rounded-xl text-sm font-semibold shrink-0"
              >
                + Upload .cs3
              </button>
            </div>

            {error && (
              <div className="mb-4 p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
                Error: {error.message}
                <button onClick={() => refetch()} className="ml-3 underline">
                  Retry
                </button>
              </div>
            )}

            <ExtensionTable extensions={extensions ?? []} onEdit={setEditingExtension} isLoading={isLoading} />
          </>
        )}

        {tab === 'repos' && <RepoManager />}
        {tab === 'app-repos' && <AppRepositoryManager />}
        {tab === 'controls' && <AppControlPanel />}
      </main>

      {uploading && (
        <UploadForm
          onClose={() => setUploading(false)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['extensions'] })}
        />
      )}

      {editingExtension && (
        <EditModal
          extension={editingExtension}
          onClose={() => setEditingExtension(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['extensions'] })}
        />
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  hint,
  tone = 'neutral',
}: {
  label: string;
  value: number;
  hint?: string;
  tone?: 'neutral' | 'good' | 'bad';
}) {
  const color = tone === 'bad' ? 'text-red-400' : tone === 'good' ? 'text-green-400' : 'text-white';
  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-4" title={hint}>
      <p className={`text-3xl font-bold ${color}`}>{value}</p>
      <p className="text-xs text-zinc-500 mt-1">{label}</p>
    </div>
  );
}
