import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createNotice, fetchNotices, fetchReleases, publishRelease, toggleNotice } from '../api/appControl';

export default function AppControlPanel() {
  const client = useQueryClient();
  const notices = useQuery({ queryKey: ['notices'], queryFn: fetchNotices });
  const releases = useQuery({ queryKey: ['releases'], queryFn: fetchReleases });
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [versionName, setVersionName] = useState('');
  const [versionCode, setVersionCode] = useState('');
  const [notes, setNotes] = useState('');
  const [required, setRequired] = useState(false);
  const noticeMutation = useMutation({ mutationFn: () => createNotice({ title, message, priority: 0 }), onSuccess: () => { setTitle(''); setMessage(''); client.invalidateQueries({ queryKey: ['notices'] }); } });
  const releaseMutation = useMutation({ mutationFn: () => publishRelease(file!, versionName, Number(versionCode), notes, required), onSuccess: () => { setFile(null); setVersionName(''); setVersionCode(''); setNotes(''); client.invalidateQueries({ queryKey: ['releases'] }); } });
  return <div className="grid lg:grid-cols-2 gap-6">
    <section className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5">
      <h2 className="text-lg font-semibold mb-1">In-app notices</h2>
      <p className="text-xs text-zinc-500 mb-4">The latest enabled notice appears when the user opens the app.</p>
      <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Notice title" className="w-full bg-zinc-950 border border-zinc-700 rounded-lg p-2 mb-2" />
      <textarea value={message} onChange={e => setMessage(e.target.value)} placeholder="Message" rows={4} className="w-full bg-zinc-950 border border-zinc-700 rounded-lg p-2 mb-2" />
      <button disabled={!title || !message || noticeMutation.isPending} onClick={() => noticeMutation.mutate()} className="px-4 py-2 bg-orange-600 rounded-lg text-sm">Publish notice</button>
      <div className="mt-5 space-y-2">{notices.data?.map(n => <div key={n.id} className="flex items-center justify-between gap-3 border-t border-zinc-800 pt-2 text-sm"><span>{n.title}</span><button onClick={() => toggleNotice(n.id, !n.enabled).then(() => client.invalidateQueries({ queryKey: ['notices'] }))} className="text-xs text-zinc-400">{n.enabled ? 'Disable' : 'Enable'}</button></div>)}</div>
    </section>
    <section className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5">
      <h2 className="text-lg font-semibold mb-1">App update</h2>
      <p className="text-xs text-zinc-500 mb-4">Upload a release APK. Required releases block old app versions at startup.</p>
      <div className="grid grid-cols-2 gap-2 mb-2"><input value={versionName} onChange={e => setVersionName(e.target.value)} placeholder="Version name 2.1.0" className="bg-zinc-950 border border-zinc-700 rounded-lg p-2" /><input value={versionCode} onChange={e => setVersionCode(e.target.value)} placeholder="Version code 3" type="number" className="bg-zinc-950 border border-zinc-700 rounded-lg p-2" /></div>
      <input type="file" accept=".apk" onChange={e => setFile(e.target.files?.[0] ?? null)} className="w-full text-sm mb-2" />
      <textarea value={notes} onChange={e => setNotes(e.target.value)} placeholder="Release notes" rows={3} className="w-full bg-zinc-950 border border-zinc-700 rounded-lg p-2 mb-2" />
      <label className="flex items-center gap-2 text-sm mb-3"><input type="checkbox" checked={required} onChange={e => setRequired(e.target.checked)} /> Require this update</label>
      <button disabled={!file || !versionName || !versionCode || releaseMutation.isPending} onClick={() => releaseMutation.mutate()} className="px-4 py-2 bg-blue-600 rounded-lg text-sm">Publish APK update</button>
      <div className="mt-5 space-y-2">{releases.data?.map(r => <div key={r.id} className="border-t border-zinc-800 pt-2 text-sm"><b>{r.version_name}</b> ({r.version_code}) {r.is_required ? <span className="text-red-400">required</span> : null}</div>)}</div>
    </section>
  </div>;
}
