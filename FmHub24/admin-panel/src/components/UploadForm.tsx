import { useState } from 'react';
import { ExtensionFormData, TV_TYPES } from '../types/extension';
import { createExtensionWithProgress, inspectExtensionFile, type PackageMetadata } from '../api/extensions';
import { useMutation, useQueryClient } from '@tanstack/react-query';

interface Props {
  onClose: () => void;
  onSuccess: () => void;
}

export default function UploadForm({ onClose, onSuccess }: Props) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [form, setForm] = useState<ExtensionFormData>({
    name: '',
    version: 1,
    language: 'en',
    tv_types: ['Movie'],
    description: '',
    icon_url: '',
    status: 'active',
  });
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState('');
  const [meta, setMeta] = useState<PackageMetadata | null>(null);
  const [checking, setChecking] = useState(false);
  const [checkError, setCheckError] = useState('');

  /**
   * Reading the package is what turns "the app says Failed to load any extension" into a message
   * written here, before anything is published: no dex, compressed dex, or a manifest without
   * pluginClassName all mean the file can never load, so they must not reach the app at all.
   */
  const handleFile = async (picked: File | null) => {
    setFile(picked);
    setMeta(null);
    setCheckError('');
    if (!picked) return;
    if (!/\.(cs3|apk|zip)$/i.test(picked.name)) {
      setCheckError(`Unsupported extension. The app loads CloudStream packages — pick a .cs3 (or a .apk/.zip built as one), not "${picked.name}".`);
      return;
    }
    setChecking(true);
    try {
      const result = await inspectExtensionFile(picked);
      setMeta(result);
      // A file the host cannot load is still publishable - as `broken`, so it stays out of the app's
      // `status = active` query while the admin reads why. Refusing the upload would just push people
      // to publish it somewhere else with no diagnosis at all.
      if (!result.loadable) setForm(prev => ({ ...prev, status: 'broken' }));
      if (result.warnings.length) setCheckError('');
      setForm(prev => ({
        ...prev,
        name: prev.name || result.manifest_name || picked.name.replace(/\.(cs3|apk|zip)$/i, ''),
        version: result.manifest_version && result.manifest_version > 0 ? result.manifest_version : prev.version,
        plugin_class_name: result.plugin_class_name ?? prev.plugin_class_name,
        internal_name: result.internal_name ?? prev.internal_name,
        api_version: result.api_version ?? prev.api_version,
        requires_resources: result.requires_resources,
      }));
    } catch (e) {
      setCheckError((e as Error).message);
    } finally {
      setChecking(false);
    }
  };

  const mutation = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('No file selected');
      return createExtensionWithProgress(form, file, setProgress);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['extensions'] });
      onSuccess();
      onClose();
    },
    onError: (err: any) => {
      setError(err.message || 'Upload failed');
      setProgress(0);
    },
  });

  const handleTvTypeToggle = (type: string) => {
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
    if (!file) {
      setError('Please select a .cs3 file');
      return;
    }
    if (checking) {
      setError('Still reading the package — one second.');
      return;
    }
    if (checkError) {
      setError(checkError);
      return;
    }
    if (!file.name.trim() || !form.name.trim()) {
      setError('A name is required — it is what the app shows next to every result from this provider.');
      return;
    }
    mutation.mutate();
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50 overflow-y-auto">
      <div className="bg-zinc-900 border border-zinc-800 rounded-2xl w-full max-w-2xl my-8">
        <div className="p-6 border-b border-zinc-800 flex items-center justify-between">
          <h2 className="text-xl font-semibold text-white">Upload New Extension</h2>
          <button onClick={onClose} className="w-8 h-8 rounded-lg bg-zinc-800 hover:bg-zinc-700 flex items-center justify-center text-zinc-400 transition">
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          {/* File Upload */}
          <div>
            <label className="block text-sm font-medium text-zinc-300 mb-2">
              Extension File (.cs3) *
            </label>
            <div className="relative">
              <input
                type="file"
                accept=".cs3,.apk,.zip"
                onChange={(e) => handleFile(e.target.files?.[0] || null)}
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-orange-500 file:text-white file:text-sm file:font-medium hover:file:bg-orange-600 file:transition"
              />
            </div>
            {file && (
              <div className="mt-2 text-xs">
                <p className="text-zinc-400">
                  Selected: <span className="text-zinc-200">{file.name}</span> ({(file.size / 1024 / 1024).toFixed(2)} MB)
                </p>
                {checking && <p className="text-zinc-500 mt-1">Reading archive…</p>}
                {checkError && (
                  <p className="mt-2 p-2.5 bg-red-500/10 border border-red-500/20 rounded-lg text-red-300 whitespace-pre-wrap">
                    {checkError}
                  </p>
                )}
                {meta && (
                  <div className="mt-2 p-3 bg-zinc-800/60 border border-zinc-700 rounded-xl space-y-1">
                    <Row ok={meta.size_bytes > 0} label="archive" value={`${meta.size_bytes} bytes`} />
                    <Row ok label="classes.dex" value="found, stored uncompressed (linkable)" />
                    <Row
                      ok={!!meta.plugin_class_name}
                      label="pluginClassName"
                      value={meta.plugin_class_name ?? 'missing — the app would have nothing to instantiate'}
                    />
                    <Row
                      ok={meta.api_version === null || meta.api_version === 1}
                      label="plugin API"
                      value={
                        meta.api_version === null
                          ? 'not stamped (plain CloudStream package — loads only if it targets CloudStream v4.8)'
                          : `v${meta.api_version}${meta.api_version === 1 ? ' (this host)' : ' — incompatible with this host (v1)'}`
                      }
                    />
                    {meta.requires_resources && (
                      <Row ok={meta.api_version !== null} label="resources" value="requiresResources=true — the app copies res/ + resources.arsc next to the dex" />
                    )}
                    <Row ok={!!meta.file_hash} label="sha256" value={meta.file_hash ? meta.file_hash.slice(0, 24) + '…' : 'not available (needs https)'} />
                    <Row
                      ok={meta.dex_audit?.method === 0}
                      label="classes.dex"
                      value={
                        meta.dex_audit === null
                          ? 'missing'
                          : `${meta.dex_audit.method === 0 ? 'STORED' : 'DEFLATED'} — ${(meta.dex_audit.dexBytes / 1024).toFixed(0)} KB` +
                            (meta.dex_audit.method === 0 ? '' : ' (compressed; this host still loads it)')
                      }
                    />
                    <Row
                      ok={meta.loadable}
                      label="host load gate"
                      value={
                        meta.loadable
                          ? `ok — ${meta.dex_audit?.extendsBasePlugin ? 'BasePlugin' : 'MainAPI'} found in the dex, no app-module types`
                          : `${meta.load_issues.length} reason(s) why the app cannot load this file`
                      }
                    />
                    {meta.load_issues.map(issue => (
                      <p key={issue} className="pt-1 text-red-400 whitespace-pre-wrap">✕ {issue}</p>
                    ))}
                    {!meta.loadable && (
                      <p className="pt-1 text-zinc-400">
                        Status was set to <span className="text-red-400">broken</span>, so the user app skips this row. Fix it by
                        rebuilding the provider against <code className="text-zinc-200">:plugin-api</code> (see docs/PLUGINS.md) —
                        or publish it anyway by setting the status back yourself.
                      </p>
                    )}
                    {meta.warnings.map(w => (
                      <p key={w} className="pt-1 text-yellow-400 whitespace-pre-wrap">⚠ {w}</p>
                    ))}
                    <p className="pt-1 text-zinc-500">
                      These fields were read from the file and will be saved with the row, so the app can verify its download.
                    </p>
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-zinc-300 mb-2">Name *</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
                placeholder="e.g. SFlix"
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-orange-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-zinc-300 mb-2">Version *</label>
              <input
                type="number"
                min={1}
                value={form.version}
                onChange={(e) => setForm({ ...form, version: parseInt(e.target.value) || 1 })}
                required
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
                placeholder="en, bn, hi..."
                className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-orange-500"
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
                  onClick={() => handleTvTypeToggle(t)}
                  className={`px-3 py-1.5 rounded-full text-xs font-medium border transition ${
                    form.tv_types.includes(t)
                      ? 'bg-orange-500/20 text-orange-400 border-orange-500/30'
                      : 'bg-zinc-800 text-zinc-400 border-zinc-700 hover:border-zinc-600'
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-zinc-300 mb-2">Icon URL</label>
            <input
              type="url"
              value={form.icon_url}
              onChange={(e) => setForm({ ...form, icon_url: e.target.value })}
              placeholder="https://..."
              className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-orange-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-zinc-300 mb-2">Description</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows={3}
              placeholder="Brief description..."
              className="w-full px-4 py-3 bg-zinc-800 border border-zinc-700 rounded-xl text-white placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-orange-500 resize-none"
            />
          </div>

          {mutation.isPending && (
            <div className="space-y-2">
              <div className="flex justify-between text-xs text-zinc-400">
                <span>Uploading...</span>
                <span>{progress}%</span>
              </div>
              <div className="w-full h-2 bg-zinc-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-orange-500 to-orange-600 transition-all duration-300"
                  style={{ width: `${progress}%` }}
                />
              </div>
            </div>
          )}

          {error && (
            <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
              {error}
            </div>
          )}

          <div className="flex gap-3 justify-end pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={mutation.isPending}
              className="px-5 py-2.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-xl text-sm font-medium transition disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending || checking || !meta}
              className="px-5 py-2.5 bg-gradient-to-r from-orange-500 to-orange-600 hover:from-orange-600 hover:to-orange-700 text-white rounded-xl text-sm font-medium transition shadow-lg shadow-orange-500/20 disabled:opacity-50"
            >
              {mutation.isPending ? `Uploading ${progress}%` : 'Upload Extension'}
            </button>
          </div>
        </form>      </div>
    </div>
  );
}

function Row({ ok, label, value }: { ok: boolean; label: string; value: string }) {
  return (
    <p className="flex gap-2 items-start">
      <span className={ok ? 'text-green-400' : 'text-red-400'}>{ok ? '✓' : '✕'}</span>
      <span className="text-zinc-500 w-28 shrink-0">{label}</span>
      <span className="text-zinc-300 break-all">{value}</span>
    </p>
  );
}
