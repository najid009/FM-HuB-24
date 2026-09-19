import { useState } from 'react';
import { Extension, FMHUB_PLUGIN_API_VERSION, TV_TYPES } from '../types/extension';
import { formatBytes } from '../api/repos';
import { inspectExtensionFile, updateExtension, type PackageMetadata } from '../api/extensions';
import { useMutation, useQueryClient } from '@tanstack/react-query';

interface Props {
  extension: Extension;
  onClose: () => void;
  onSuccess: () => void;
}

export default function EditModal({ extension, onClose, onSuccess }: Props) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [form, setForm] = useState({
    name: extension.name,
    version: extension.version,
    language: extension.language || '',
    tv_types: extension.tv_types || [],
    description: extension.description || '',
    icon_url: extension.icon_url || '',
    status: extension.status,
    plugin_class_name: extension.plugin_class_name || '',
    internal_name: extension.internal_name || '',
    requires_resources: !!extension.requires_resources,
  });
  const [error, setError] = useState('');
  const [meta, setMeta] = useState<PackageMetadata | null>(null);
  const [replacement, setReplacement] = useState('');

  const mutation = useMutation({
    mutationFn: () =>
      updateExtension(extension.id, form, file || undefined, undefined, extension.file_url),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['extensions'] });
      onSuccess();
      onClose();
    },
    onError: (err: any) => setError(err.message),
  });

  const toggleTvType = (type: string) => {
    setForm(prev => ({
      ...prev,
      tv_types: prev.tv_types.includes(type)
        ? prev.tv_types.filter(t => t !== type)
        : [...prev.tv_types, type],
    }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    mutation.mutate();
  };

  /** Reading the replacement package up front is what makes "broken" rows fixable in one step. */
  const handleFile = async (picked: File | null) => {
    setFile(picked);
    setMeta(null);
    setReplacement('');
    if (!picked) return;
    try {
      const result = await inspectExtensionFile(picked);
      setMeta(result);
      setForm(prev => ({
        ...prev,
        version: result.manifest_version && result.manifest_version > 0 ? result.manifest_version : prev.version,
        plugin_class_name: result.plugin_class_name || prev.plugin_class_name,
        internal_name: result.internal_name || prev.internal_name,
        requires_resources: result.requires_resources,
      }));
      if (result.api_version != null && result.api_version !== FMHUB_PLUGIN_API_VERSION) {
        setReplacement(`This package declares plugin API v${result.api_version}; this app is v${FMHUB_PLUGIN_API_VERSION}. Saving it will mark the row broken.`);
      }
    } catch (e) {
      setReplacement((e as Error).message);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50 overflow-y-auto">
      <div className="bg-zinc-900 border border-zinc-800 rounded-2xl w-full max-w-2xl my-8">
        <div className="p-6 border-b border-zinc-800 flex items-center justify-between">
          <h2 className="text-xl font-semibold text-white">Edit Extension</h2>
          <button onClick={onClose} className="w-8 h-8 rounded-lg bg-zinc-800 hover:bg-zinc-700 flex items-center justify-center text-zinc-400">
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          <div>
            <label className="block text-sm font-medium text-zinc-300 mb-2">Replace File (optional)</label>
            <input
              type="file"
              accept=".cs3,.apk,.zip"
              onChange={(e) => handleFile(e.target.files?.[0] || null)}
              className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-zinc-700 file:text-white file:text-sm"
            />
            <p className="text-xs text-zinc-500 mt-1">
              Current: <span className="text-zinc-300">{extension.file_name ?? extension.file_url.split('/').pop()}</span>
              {' · '}{formatBytes(extension.size_bytes)}
              {extension.file_hash ? ` · sha256 ${extension.file_hash.slice(0, 12)}…` : ' · no recorded hash'}
              {extension.source_repo_url ? (
                <>
                  {' · '}
                  <a href={extension.source_repo_url} target="_blank" rel="noreferrer" className="text-blue-400 hover:text-blue-300">
                    from repo
                  </a>
                </>
              ) : (
                ' · uploaded directly'
              )}
            </p>
            {replacement && (
              <p className={`mt-2 p-2.5 rounded-lg text-xs whitespace-pre-wrap ${meta ? 'bg-yellow-500/10 border border-yellow-500/20 text-yellow-300' : 'bg-red-500/10 border border-red-500/20 text-red-300'}`}>
                {replacement}
              </p>
            )}
            {meta && (
              <div className="mt-2 p-3 bg-zinc-800/60 border border-zinc-700 rounded-xl text-xs space-y-1">
                <p className="text-zinc-400">New file: {meta.file_name} ({formatBytes(meta.size_bytes)})</p>
                <p className="text-zinc-400">pluginClassName: <span className="text-zinc-200 font-mono">{meta.plugin_class_name ?? 'MISSING'}</span></p>
                {meta.warnings.map(w => <p key={w} className="text-yellow-400">⚠ {w}</p>)}
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-zinc-300 mb-2">Name</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-orange-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-zinc-300 mb-2">Version</label>
              <input
                type="number"
                value={form.version}
                onChange={(e) => setForm({ ...form, version: parseInt(e.target.value) || 1 })}
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-orange-500"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-zinc-300 mb-2">Language</label>
              <input
                type="text"
                value={form.language}
                onChange={(e) => setForm({ ...form, language: e.target.value })}
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-orange-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-zinc-300 mb-2">Status</label>
              <select
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value as any })}
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-orange-500"
              >
                <option value="active">Active</option>
                <option value="disabled">Disabled</option>
                <option value="broken">Broken</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-zinc-300 mb-2">TV Types</label>
            <div className="flex flex-wrap gap-2">
              {TV_TYPES.map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => toggleTvType(t)}
                  className={`px-3 py-1.5 rounded-full text-xs font-medium border transition ${
                    form.tv_types.includes(t)
                      ? 'bg-orange-500/20 text-orange-400 border-orange-500/30'
                      : 'bg-zinc-800 text-zinc-400 border-zinc-700'
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-zinc-300 mb-2">pluginClassName</label>
              <input
                type="text"
                value={form.plugin_class_name}
                onChange={(e) => setForm({ ...form, plugin_class_name: e.target.value })}
                placeholder="com.FMHub24TestExtension"
                title="Must be the class that extends BasePlugin and implements MainAPI — a wrong value here is the classic '0 providers' cause"
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white text-sm font-mono focus:outline-none focus:ring-2 focus:ring-orange-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-zinc-300 mb-2">internal name</label>
              <input
                type="text"
                value={form.internal_name}
                onChange={(e) => setForm({ ...form, internal_name: e.target.value })}
                placeholder="fmhub-archive"
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white text-sm focus:outline-none focus:ring-2 focus:ring-orange-500"
              />
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm text-zinc-300">
            <input
              type="checkbox"
              checked={form.requires_resources}
              onChange={(e) => setForm({ ...form, requires_resources: e.target.checked })}
              className="accent-orange-500 w-4 h-4"
            />
            requiresResources — the app must unpack res/ + resources.arsc next to the dex
          </label>

          <div>
            <label className="block text-sm font-medium text-zinc-300 mb-2">Icon URL</label>
            <input
              type="url"
              value={form.icon_url}
              onChange={(e) => setForm({ ...form, icon_url: e.target.value })}
              className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-orange-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-zinc-300 mb-2">Description</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows={3}
              className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-orange-500 resize-none"
            />
          </div>

          {error && (
            <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
              {error}
            </div>
          )}

          <div className="flex gap-3 justify-end">
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-xl text-sm font-medium"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="px-5 py-2.5 bg-orange-600 hover:bg-orange-700 text-white rounded-xl text-sm font-medium disabled:opacity-50"
            >
              {mutation.isPending ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
