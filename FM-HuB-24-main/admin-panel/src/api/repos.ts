import { ExtensionRepo, FMHUB_PLUGIN_API_VERSION, RepoJson, RepoPluginEntry } from '../types/extension';
import { supabase } from '../lib/supabase';

/**
 * Reading an extension repository.
 *
 * A CloudStream repo is two JSON documents:
 *   repo.json     -> { name, description, manifestVersion: 1, pluginLists: [url, …] }
 *   plugins.json  -> [ { name, url, version, internalName, language, tvTypes, fileHash,
 *                        fileSize, apiVersion, … }, … ]
 *
 * The admin flow mirrors what CloudStream's own "add repository" screen does — paste a link,
 * see every provider the repo publishes, tick the ones you want — with one addition: the
 * selected providers are what your *user app* gets, so the selection is persisted per repo.
 */

export class RepoFormatError extends Error {}

async function getJson(url: string): Promise<unknown> {
  let response: Response;
  try {
    response = await fetch(url, { headers: { accept: 'application/json' } });
  } catch {
    throw new RepoFormatError(
      `Could not reach ${url}. Public repos only (CORS): raw.githubusercontent.com, GitLab raw, ` +
      `GitHub Pages or your own host with Access-Control-Allow-Origin: *.`
    );
  }
  if (!response.ok) {
    throw new RepoFormatError(`${url} returned HTTP ${response.status} ${response.statusText}`);
  }
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    throw new RepoFormatError(
      `${url} is not JSON. For a CloudStream repo the link must point at repo.json, not at the repository page.`
    );
  }
}

const isEntryArray = (value: unknown): value is RepoPluginEntry[] =>
  Array.isArray(value) && value.every(item => item && typeof item === 'object' && 'url' in item);

/** Resolves either a repo.json (following pluginLists) or a direct plugins.json array. */
export async function fetchRepo(repoUrl: string): Promise<{ meta: RepoJson; plugins: RepoPluginEntry[] }> {
  const document = await getJson(repoUrl);

  if (isEntryArray(document)) {
    return { meta: { name: deriveName(repoUrl) }, plugins: document };
  }

  if (!document || typeof document !== 'object') {
    throw new RepoFormatError(`${repoUrl} returned an unexpected document`);
  }

  const meta = document as RepoJson;
  const inline = meta.plugins ?? meta.extensions;
  if (isEntryArray(inline)) {
    return { meta: { ...meta, name: meta.name ?? deriveName(repoUrl) }, plugins: inline };
  }

  const lists = meta.pluginLists ?? [];
  if (lists.length === 0) {
    throw new RepoFormatError(
      `${repoUrl} has neither pluginLists nor an inline plugin array — it is not a CloudStream repo.json`
    );
  }

  const pages = await Promise.all(
    lists.map(async listUrl => {
      const absolute = new URL(listUrl, repoUrl).toString();
      const entries = await getJson(absolute);
      if (!isEntryArray(entries)) {
        throw new RepoFormatError(`${absolute} is not a plugins.json array`);
      }
      return entries;
    })
  );

  // Same provider can appear on several pages (language splits); keep the newest version.
  const merged = new Map<string, RepoPluginEntry>();
  for (const entry of pages.flat()) {
    const key = `${entry.internalName ?? entry.name}::${entry.url}`;
    const previous = merged.get(key);
    if (!previous || (entry.version ?? 0) > (previous.version ?? 0)) merged.set(key, entry);
  }

  return {
    meta: { ...meta, name: meta.name ?? deriveName(repoUrl) },
    plugins: [...merged.values()].sort((a, b) => a.name.localeCompare(b.name)),
  };
}

function deriveName(url: string): string {
  try {
    const parsed = new URL(url);
    const file = decodeURIComponent(parsed.pathname.split('/').pop() ?? 'repo');
    const owner = parsed.hostname.includes('github') ? parsed.pathname.split('/')[1] : '';
    return owner ? `${owner}/${file}` : file;
  } catch {
    return url;
  }
}

/** Turns a repo entry into the columns of the `extensions` table. */
export function repoEntryToRow(entry: RepoPluginEntry, repoUrl: string) {
  return {
    name: entry.name,
    file_url: entry.url,
    version: Number.isFinite(entry.version) ? entry.version : 1,
    language: entry.language ?? null,
    tv_types: entry.tvTypes?.length ? entry.tvTypes : null,
    icon_url: entry.iconUrl ?? null,
    description:
      [entry.description, entry.authors?.length ? `by ${entry.authors.join(', ')}` : null]
        .filter(Boolean)
        .join(' — ') || null,
    internal_name: entry.internalName ?? null,
    plugin_class_name: entry.pluginClassName ?? null,
    api_version: entry.fmhubApiVersion ?? null,
    cs_api_version: entry.apiVersion ?? null,
    file_hash: normalizeHash(entry.fileHash, entry.fileHashType),
    file_name: fileNameOf(entry.url),
    size_bytes: entry.fileSize ?? null,
    source_repo_url: repoUrl,
    requires_resources: entry.requiresResources ?? false,
    // Providers that were built for another FMHub contract never load; mark them broken so the
    // user app's "active" filter skips them instead of showing a load failure.
    status: compatibility(entry).ok ? ('active' as const) : ('broken' as const),
  };
}

export type Compat = { ok: boolean; reason?: string };

/**
 * What the app will report if this is enabled: wrong FMHub contract, a repo format we cannot
 * read, or a package that is not a .cs3 at all.
 */
export function compatibility(entry: RepoPluginEntry): Compat {
  if (entry.fmhubApiVersion != null && entry.fmhubApiVersion !== FMHUB_PLUGIN_API_VERSION) {
    return {
      ok: false,
      reason: `built for plugin API v${entry.fmhubApiVersion}, this host is v${FMHUB_PLUGIN_API_VERSION}`,
    };
  }
  if (entry.apiVersion != null && entry.apiVersion > 1) {
    return { ok: false, reason: `repo format v${entry.apiVersion} is newer than the v1 reader here` };
  }
  const name = fileNameOf(entry.url).toLowerCase();
  if (!/\.(cs3|zip|apk)$/.test(name)) {
    return { ok: false, reason: `not a plugin container (.cs3/.zip/.apk): ${name.split('.').pop()}` };
  }
  // CloudStream's `status` is a quality label, not an on/off switch: 0 down, 1 ok, 2 slow, 3 beta
  // (recloudstream/cloudstream app/.../plugins/RepositoryManager.kt). Only 0 means "do not use".
  // Treating 1/2/3 as down-marked hid every healthy server from the user app.
  if (entry.status === 0) {
    return { ok: false, reason: 'the repo itself marks this provider as down (status=0)' };
  }
  if (entry.status === 2 || entry.status === 3) {
    return {
      ok: true,
      reason: `the repo labels this provider ${entry.status === 2 ? 'slow' : 'beta'} (status=${entry.status})`,
    };
  }
  return { ok: true };
}

export function fileNameOf(url: string): string {
  try {
    return decodeURIComponent(new URL(url).pathname.split('/').filter(Boolean).pop() ?? 'plugin.cs3');
  } catch {
    return url.split('/').filter(Boolean).pop() ?? 'plugin.cs3';
  }
}

export function formatBytes(bytes?: number | null): string {
  if (bytes == null) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KiB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

// --------------------------------------------------------------------------- extension_repos

export async function fetchRepos(): Promise<ExtensionRepo[]> {
  const { data, error } = await supabase
    .from('extension_repos')
    .select('*')
    .order('created_at', { ascending: true });
  if (error) throw error;
  return (data ?? []) as ExtensionRepo[];
}

/**
 * Accept either the exact repo.json link or the directory/branch it lives in — people share
 * repos as "https://github.com/user/repo/tree/builds", and CloudStream appends /repo.json too.
 */
export function normaliseRepoUrl(url: string): string {
  const trimmed = url.trim().replace(/\/+$/, '');
  if (!trimmed) return trimmed;
  if (/\.json(\?.*)?$/i.test(trimmed)) return trimmed;
  return `${trimmed}/repo.json`;
}

export async function addRepo(url: string, name?: string): Promise<ExtensionRepo> {
  const clean = normaliseRepoUrl(url);
  if (!/^https?:\/\//i.test(clean)) throw new RepoFormatError('The repo link must start with http(s)://');
  // Read it once here: a typo or a link that is not a repo.json should fail while it is being
  // typed, not show up later as an empty server list. CORS blocks look the same as 404s —
  // hence the explicit hint in parseRepo/fetchRepo errors.
  await fetchRepo(clean);

  const { data, error } = await supabase
    .from('extension_repos')
    .upsert({ url: clean, name: name?.trim() || deriveName(clean), enabled: true, updated_at: new Date().toISOString() }, { onConflict: 'url' })
    .select()
    .single();
  if (error) throw error;
  return data as ExtensionRepo;
}

export async function setRepoEnabled(id: string, enabled: boolean): Promise<void> {
  const { error } = await supabase
    .from('extension_repos')
    .update({ enabled, updated_at: new Date().toISOString() })
    .eq('id', id);
  if (error) throw error;
}

export async function touchRepo(id: string): Promise<void> {
  const { error } = await supabase
    .from('extension_repos')
    .update({ last_synced_at: new Date().toISOString(), updated_at: new Date().toISOString() })
    .eq('id', id);
  if (error) throw error;
}

export async function removeRepo(repo: ExtensionRepo): Promise<void> {
  const { error } = await supabase.from('extension_repos').delete().eq('id', repo.id);
  if (error) throw error;
}

/** Rows that came from this repo, so "remove repo" can also clean up its extensions. */
export async function deleteExtensionsOfRepo(repoUrl: string): Promise<number> {
  const { data, error } = await supabase.from('extensions').delete().eq('source_repo_url', repoUrl).select('id');
  if (error) throw error;
  return data?.length ?? 0;
}

/**
 * `fileHash` in community repos is written as `sha256-<hex>` (sometimes `md5-<hex>`), while the app
 * compares raw hex after download. Keep only the hex, and drop anything that is not sha256.
 */
export function normalizeHash(value?: string | null, type?: string | null): string | null {
  if (!value) return null;
  const trimmed = value.trim().toLowerCase();
  if (type && type !== 'sha256') return null;
  const hex = trimmed.includes('-') ? trimmed.slice(trimmed.lastIndexOf('-') + 1) : trimmed;
  return /^[0-9a-f]{64}$/.test(hex) ? hex : null;
}
