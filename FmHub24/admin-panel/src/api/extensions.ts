import { supabase, STORAGE_BUCKET } from '../lib/supabase';
import { Extension, ExtensionFormData, RepoPluginEntry } from '../types/extension';
import { auditDex, dexMethod, inspectCs3, type DexAudit } from './cs3';

/** Fields derived from the package itself, so the row can never disagree with the bytes. */
export interface PackageMetadata {
  file_hash: string | null;
  file_name: string;
  size_bytes: number;
  plugin_class_name: string | null;
  internal_name: string | null;
  api_version: number | null;
  requires_resources: boolean;
  /** manifest.json's own name/version — the form prefills from them and warns on disagreement. */
  manifest_name: string | null;
  manifest_version: number | null;
  warnings: string[];
  /** false = the file parses but our host cannot load it; publish it as `broken`, not `active`. */
  loadable: boolean;
  /** The hard reasons `loadable` is false, one sentence each — shown to the admin, never to users. */
  load_issues: string[];
  dex_audit: DexAudit | null;
}

/**
 * Reads the uploaded .cs3 before anything is sent to storage.
 * Rejecting here is deliberate: a file that has no classes.dex or no pluginClassName will be
 * downloaded, cached, fail to link, and the only trace is a log line on somebody's phone.
 */
export async function inspectExtensionFile(file: File): Promise<PackageMetadata> {
  const lower = file.name.toLowerCase();
  if (!/\.(cs3|apk|zip)$/.test(lower)) {
    throw new Error('Unsupported file type — the user app loads CloudStream packages (.cs3, or a .apk/.zip that contains manifest.json + classes.dex).');
  }

  let inspection;
  try {
    inspection = await inspectCs3(file);
  } catch (e) {
    throw new Error(`${file.name} is not a readable package: ${(e as Error).message}`);
  }

  const warnings: string[] = [];
  if (!inspection.hasDex) {
    throw new Error(`${file.name} has no classes.dex — that is the plugin code, so nothing can be loaded from it.`);
  }
  const audit = await auditDex(await file.arrayBuffer(), inspection.entries, inspection.manifest?.pluginClassName);
  const loadIssues: string[] = [];

  if (dexMethod(inspection.entries) !== 0) {
    warnings.push(
      'classes.dex is deflated rather than STORED. That loads on our host (every community package is built that way), but a CloudStream client that mmaps the entry in place would fail — repack with STORED via extensions/tools/make_repo.py if you also publish a plain CloudStream repo.'
    );
  }
  if (audit) {
    if (audit.appOnlyTypes.length) {
      loadIssues.push(
        `This package links against ${audit.appOnlyTypes.length} class(es) that only exist in CloudStream's app module (${audit.appOnlyTypes.slice(0, 3).map(s => s.slice(1, -1).replace(/\//g, '.')).join(', ')}). FMHub24 ships the library + its own plugin API, so these files fail at load time. They must be rebuilt against :plugin-api — see docs/PLUGINS.md.`
      );
    }
    if (audit.declaredClassFound === false) {
      loadIssues.push(
        `manifest.json declares ${inspection.manifest?.pluginClassName}, but classes.dex has no such class. The app can still find providers by scanning the dex, so this is usually only a stale manifest.`
      );
    }
    if (!audit.extendsBasePlugin && !audit.implementsMainApi) {
      loadIssues.push(
        'classes.dex contains neither BasePlugin nor a MainAPI implementation — there is nothing in this file the host could register.'
      );
    }
  }
  if (loadIssues.length && !inspection.manifest) {
    loadIssues.unshift('No readable manifest.json: the app has to guess the entry class from the dex.');
  }
  if (inspection.error) warnings.push(inspection.error);
  if (inspection.hasResources && !inspection.manifest?.requiresResources) {
    warnings.push('The archive contains resources.arsc but the manifest says requiresResources=false — the app will not copy them, so any R.* lookup in the plugin will throw.');
  }
  if (!inspection.sha256) {
    warnings.push('SHA-256 could not be computed (the browser page must be served over https for SubtleCrypto). The app will skip integrity verification for this file.');
  }

  return {
    // Computed here, from the exact bytes being published, so the app can verify what it downloaded.
    // (Repo imports get the same treatment in repos.ts -> normalizeHash.)
    file_hash: inspection.sha256,
    loadable: loadIssues.length === 0,
    load_issues: loadIssues,
    dex_audit: audit,
    file_name: file.name,
    size_bytes: file.size,
    plugin_class_name: inspection.manifest?.pluginClassName ?? null,
    internal_name: lower.replace(/\.(cs3|apk|zip)$/, '') || null,
    api_version: inspection.manifest?.fmhubApiVersion ?? null,
    requires_resources: !!inspection.manifest?.requiresResources,
    manifest_name: inspection.manifest?.name ?? null,
    manifest_version: typeof inspection.manifest?.version === 'number' ? inspection.manifest.version : null,
    warnings,
  };
}

export async function fetchAllExtensions(): Promise<Extension[]> {
  const { data, error } = await supabase
    .from('extensions')
    .select('*')
    .order('updated_at', { ascending: false });

  if (error) throw error;
  return data as Extension[];
}

export async function fetchExtensionById(id: string): Promise<Extension> {
  const { data, error } = await supabase
    .from('extensions')
    .select('*')
    .eq('id', id)
    .single();

  if (error) throw error;
  return data as Extension;
}

export async function createExtension(
  formData: ExtensionFormData,
  file: File,
  onProgress?: (progress: number) => void
): Promise<Extension> {
  // 1. Upload file to storage with progress tracking
  const fileName = `${Date.now()}-${file.name}`;
  const filePath = `${fileName}`;

  // Use upload with onUploadProgress if available, otherwise simulate
  // Supabase JS v2 supports upload with options, we will track via custom wrapper
  // For real progress, we use the storage upload and report via callback

  // Create upload task
  const { data: uploadData, error: uploadError } = await supabase.storage
    .from(STORAGE_BUCKET)
    .upload(filePath, file, {
      cacheControl: '3600',
      upsert: false,
    });

  if (uploadError) throw uploadError;

  // Simulate progress callback completion if no real progress events
  onProgress?.(100);

  // 2. Get public URL
  const { data: publicUrlData } = supabase.storage
    .from(STORAGE_BUCKET)
    .getPublicUrl(uploadData.path);

  const fileUrl = publicUrlData.publicUrl;

  // 3. Insert row
  const { data, error } = await supabase
    .from('extensions')
    .insert([
      {
        name: formData.name,
        file_url: fileUrl,
        version: formData.version,
        language: formData.language || null,
        tv_types: formData.tv_types.length ? formData.tv_types : null,
        status: formData.status,
        icon_url: formData.icon_url || null,
        description: formData.description || null,
      },
    ])
    .select()
    .single();

  if (error) {
    // Rollback: delete uploaded file if DB insert fails
    await supabase.storage.from(STORAGE_BUCKET).remove([uploadData.path]);
    throw error;
  }

  return data as Extension;
}

// Alternative upload with XHR for real progress tracking
export async function createExtensionWithProgress(
  formData: ExtensionFormData,
  file: File,
  onProgress: (progress: number) => void
): Promise<Extension> {
  const fileName = `${Date.now()}-${file.name}`;
  
  // We will use direct fetch with progress via XMLHttpRequest to Supabase Storage
  // Because supabase-js upload doesn't expose progress events in all versions,
  // we implement real progress using XHR for the upload part
  
  return new Promise(async (resolve, reject) => {
    try {
      // Get session for auth header
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) throw new Error('Not authenticated');

      let meta: PackageMetadata;
      try {
        meta = await inspectExtensionFile(file);
      } catch (e) {
        reject(e);
        return;
      }
      // The manifest is the authority on the version number: CloudStream compares it with
      // plugins.json to decide whether an update exists, and a row that disagrees produces an
      // update loop that never converges.
      const manifestVersion = meta.manifest_version && meta.manifest_version > 0 ? meta.manifest_version : null;
      const version = manifestVersion ?? formData.version ?? 1;

      const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
      const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

      const uploadUrl = `${supabaseUrl}/storage/v1/object/${STORAGE_BUCKET}/${fileName}`;

      const xhr = new XMLHttpRequest();
      
      xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
          const percent = Math.round((e.loaded / e.total) * 90); // 90% for upload, 10% for DB
          onProgress(percent);
        }
      });

      xhr.addEventListener('load', async () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            onProgress(95);
            const { data: publicUrlData } = supabase.storage
              .from(STORAGE_BUCKET)
              .getPublicUrl(fileName);

            const fileUrl = publicUrlData.publicUrl;

            const { data, error } = await supabase
              .from('extensions')
              .insert([
                {
                  name: formData.name,
                  file_url: fileUrl,
                  version,
                  language: formData.language || null,
                  tv_types: formData.tv_types.length ? formData.tv_types : null,
                  status: formData.status,
                  icon_url: formData.icon_url || null,
                  description: formData.description || null,
                  file_hash: meta.file_hash,
                  file_name: meta.file_name,
                  size_bytes: meta.size_bytes,
                  plugin_class_name: meta.plugin_class_name,
                  internal_name: meta.internal_name,
                  api_version: meta.api_version,
                  requires_resources: meta.requires_resources,
                  source_repo_url: null,
                  updated_at: new Date().toISOString(),
                },
              ])
              .select()
              .single();

            if (error) throw error;
            onProgress(100);
            resolve(data as Extension);
          } catch (err) {
            reject(err);
          }
        } else {
          reject(new Error(`Upload failed: ${xhr.statusText} - ${xhr.responseText}`));
        }
      });

      xhr.addEventListener('error', () => {
        reject(new Error('Upload failed due to network error'));
      });

      xhr.open('POST', uploadUrl);
      xhr.setRequestHeader('Authorization', `Bearer ${session.access_token}`);
      xhr.setRequestHeader('apikey', anonKey);
      xhr.setRequestHeader('x-upsert', 'false');
      xhr.send(file);

    } catch (err) {
      reject(err);
    }
  });
}

/**
 * Extracts the storage object path from a Supabase Storage public URL.
 * e.g. https://xxx.supabase.co/storage/v1/object/public/extensions/a.cs3 -> a.cs3
 */
export function extractStoragePath(fileUrl: string): string | null {
  try {
    const url = new URL(fileUrl);
    const pathSegments = url.pathname.split('/');
    const bucketIndex = pathSegments.indexOf(STORAGE_BUCKET);
    if (bucketIndex !== -1) {
      const filePath = pathSegments.slice(bucketIndex + 1).join('/');
      return filePath || null;
    }
    // Fallback: take last segment
    const last = pathSegments[pathSegments.length - 1];
    return last || null;
  } catch {
    return null;
  }
}

export async function updateExtension(
  id: string,
  formData: Partial<ExtensionFormData> & { file_url?: string },
  newFile?: File,
  onProgress?: (progress: number) => void,
  oldFileUrl?: string
): Promise<Extension> {
  let fileUrl = formData.file_url;
  let meta: PackageMetadata | null = null;

  if (newFile) {
    // A replacement file has to satisfy exactly the same checks as a first upload, otherwise
    // "fix it in the edit dialog" becomes a way to publish a package the app cannot link.
    meta = await inspectExtensionFile(newFile);
    const fileName = `${Date.now()}-${newFile.name}`;
    const { data: uploadData, error: uploadError } = await supabase.storage
      .from(STORAGE_BUCKET)
      .upload(fileName, newFile, {
        cacheControl: '3600',
        upsert: false,
      });

    if (uploadError) throw uploadError;
    onProgress?.(100);

    const { data: publicUrlData } = supabase.storage
      .from(STORAGE_BUCKET)
      .getPublicUrl(uploadData.path);

    fileUrl = publicUrlData.publicUrl;
  }

  const manifestVersion = meta?.manifest_version && meta.manifest_version > 0 ? meta.manifest_version : null;

  const updatePayload: any = {
    ...(formData.name !== undefined && { name: formData.name }),
    ...(fileUrl && { file_url: fileUrl }),
    ...(formData.version !== undefined && { version: formData.version }),
    ...(formData.language !== undefined && { language: formData.language || null }),
    ...(formData.tv_types !== undefined && { tv_types: formData.tv_types.length ? formData.tv_types : null }),
    ...(formData.status !== undefined && { status: formData.status }),
    ...(formData.icon_url !== undefined && { icon_url: formData.icon_url || null }),
    ...(formData.description !== undefined && { description: formData.description || null }),
    ...(formData.plugin_class_name !== undefined && { plugin_class_name: formData.plugin_class_name || null }),
    ...(formData.internal_name !== undefined && { internal_name: formData.internal_name || null }),
    ...(formData.api_version !== undefined && { api_version: formData.api_version ?? null }),
    ...(formData.requires_resources !== undefined && { requires_resources: !!formData.requires_resources }),
    ...(manifestVersion !== null && { version: manifestVersion }),
    ...(meta && {
      file_hash: meta.file_hash,
      file_name: meta.file_name,
      size_bytes: meta.size_bytes,
      plugin_class_name: meta.plugin_class_name,
      api_version: meta.api_version,
      requires_resources: meta.requires_resources,
    }),
    updated_at: new Date().toISOString(),
  };

  const { data, error } = await supabase
    .from('extensions')
    .update(updatePayload)
    .eq('id', id)
    .select()
    .single();

  if (error) throw error;

  // If the file was replaced, remove the old object so storage doesn't leak.
  if (newFile && oldFileUrl && oldFileUrl !== fileUrl) {
    const oldPath = extractStoragePath(oldFileUrl);
    if (oldPath) {
      const { error: storageError } = await supabase.storage
        .from(STORAGE_BUCKET)
        .remove([oldPath]);
      if (storageError) {
        console.warn('Could not delete old file from storage:', storageError);
      }
    }
  }

  return data as Extension;
}

export async function toggleExtensionStatus(id: string, currentStatus: string): Promise<Extension> {
  const newStatus = currentStatus === 'active' ? 'disabled' : 'active';
  
  const { data, error } = await supabase
    .from('extensions')
    .update({ status: newStatus, updated_at: new Date().toISOString() })
    .eq('id', id)
    .select()
    .single();

  if (error) throw error;
  return data as Extension;
}

export async function deleteExtension(id: string, fileUrl: string): Promise<void> {
  // Extract file path from the storage URL
  const filePath = extractStoragePath(fileUrl);
  if (filePath) {
    const { error: storageError } = await supabase.storage
      .from(STORAGE_BUCKET)
      .remove([filePath]);

    // Log but don't fail if storage delete fails (file might already be gone)
    if (storageError) {
      console.warn('Storage delete warning:', storageError);
    }
  }

  const { error } = await supabase
    .from('extensions')
    .delete()
    .eq('id', id);

  if (error) throw error;
}

// ---------------------------------------------------------------------------
// Repo import: the "paste a repo link, tick the providers you want" flow.
// ---------------------------------------------------------------------------

export interface ImportResult {
  inserted: number;
  updated: number;
  skipped: number;
}

/**
 * Insert or update the selected entries of a repository.
 *
 * Matching happens on (source_repo_url, internal_name) rather than through an ON CONFLICT
 * upsert: `internal_name` is nullable for manual uploads, and a partial unique index does not
 * satisfy PostgREST's upsert. Reading the existing rows first is also what lets us report
 * "3 new, 1 updated" instead of a blind count.
 */
export async function importRepoEntries(
  repoUrl: string,
  entries: RepoPluginEntry[],
  toRow: (entry: RepoPluginEntry, repoUrl: string) => Record<string, unknown>
): Promise<ImportResult> {
  const { data: existing, error: readError } = await supabase
    .from('extensions')
    .select('id, internal_name, name, version, file_url')
    .eq('source_repo_url', repoUrl);

  if (readError) throw readError;

  const byInternalName = new Map<string, { id: string; version: number }>();
  for (const row of (existing ?? []) as Array<{ id: string; internal_name: string | null; version: number }>) {
    if (row.internal_name) byInternalName.set(row.internal_name, { id: row.id, version: row.version });
  }

  const toInsert: Record<string, unknown>[] = [];
  const updates: Array<{ id: string; patch: Record<string, unknown> }> = [];
  let skipped = 0;

  for (const entry of entries) {
    const row = toRow(entry, repoUrl);
    const internalName = (row.internal_name as string | null) ?? null;
    const match = internalName ? byInternalName.get(internalName) : undefined;

    if (!match) {
      toInsert.push(row);
      continue;
    }
    if ((entry.version ?? 1) <= match.version) {
      skipped += 1;
      continue;
    }
    const { id: _dropId, ...patch } = row;
    updates.push({ id: match.id, patch: { ...patch, updated_at: new Date().toISOString() } });
  }

  if (toInsert.length) {
    const { error } = await supabase.from('extensions').insert(toInsert);
    if (error) throw error;
  }
  // Sequential on purpose: a handful of rows, and parallel writes to the same table have
  // been observed to trip Supabase's rate limiter on the free tier.
  for (const update of updates) {
    const { error } = await supabase.from('extensions').update(update.patch).eq('id', update.id);
    if (error) throw error;
  }

  return { inserted: toInsert.length, updated: updates.length, skipped };
}

/** Bulk enable/disable — "which servers the user app will actually load". */
export async function setExtensionsStatus(ids: string[], status: 'active' | 'disabled' | 'broken'): Promise<void> {
  if (!ids.length) return;
  const { error } = await supabase
    .from('extensions')
    .update({ status, updated_at: new Date().toISOString() })
    .in('id', ids);
  if (error) throw error;
}
