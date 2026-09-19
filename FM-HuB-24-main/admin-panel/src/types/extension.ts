export type ExtensionStatus = 'active' | 'disabled' | 'broken';

/** One row of the `extensions` table (what the user app reads). */
export interface Extension {
  id: string;
  name: string;
  file_url: string;
  version: number;
  language?: string | null;
  tv_types?: string[] | null;
  status: ExtensionStatus;
  icon_url?: string | null;
  description?: string | null;
  created_at?: string;
  updated_at?: string;

  // ---- plugin metadata: what makes loading deterministic and failures explainable ----
  /** Class named by the .cs3 manifest; the host instantiates it and calls load(). */
  plugin_class_name?: string | null;
  /** Stable id inside the repo (`internalName` in CloudStream's plugins.json). */
  internal_name?: string | null;
  /** FMHub plugin-contract version; a mismatch is reported instead of silently failing. */
  api_version?: number | null;
  /** CloudStream's own apiVersion from plugins.json (repo format / host API level). */
  cs_api_version?: number | null;
  /** SHA-256 of the package; the app verifies the download against it. */
  file_hash?: string | null;
  /** File name inside storage — kept so the container type (.cs3/.apk/.zip) survives. */
  file_name?: string | null;
  size_bytes?: number | null;
  /** Which repo this row was imported from (null for manual uploads). */
  source_repo_url?: string | null;
  requires_resources?: boolean | null;
}

export interface ExtensionFormData {
  name: string;
  version: number;
  language: string;
  tv_types: string[];
  description: string;
  icon_url: string;
  status: ExtensionStatus;
  plugin_class_name?: string;
  internal_name?: string;
  api_version?: number | null;
  file_hash?: string;
  file_name?: string;
  size_bytes?: number | null;
  source_repo_url?: string;
  requires_resources?: boolean;
}

/** A saved extension repository (`extension_repos`). */
export interface ExtensionRepo {
  id: string;
  url: string;
  name: string;
  description?: string | null;
  enabled: boolean;
  last_synced_at?: string | null;
  created_at?: string;
  updated_at?: string;
}

/** One entry of a repo's plugins.json — the CloudStream `PluginEntry` shape. */
export interface RepoPluginEntry {
  name: string;
  url: string;
  version: number;
  internalName?: string | null;
  status?: number | null;
  language?: string | null;
  tvTypes?: string[] | null;
  iconUrl?: string | null;
  description?: string | null;
  authors?: string[] | null;
  fileSize?: number | null;
  fileHash?: string | null;
  fileHashType?: string | null;
  repositoryUrl?: string | null;
  apiVersion?: number | null;
  /** FMHub additions (present on repos built with our tooling). */
  pluginClassName?: string | null;
  requiresResources?: boolean | null;
  fmhubApiVersion?: number | null;
  cloudstreamVersion?: string | null;
}

/** repo.json as published by every CloudStream extension repo. */
export interface RepoJson {
  name?: string;
  description?: string | null;
  timestamp?: number;
  manifestVersion?: number;
  pluginLists?: string[];
  /** Some hand-made repos ship the entries inline instead of via pluginLists. */
  plugins?: RepoPluginEntry[];
  /** Alternate spelling used by a few older repos. */
  extensions?: RepoPluginEntry[];
}

export const TV_TYPES = [
  'Movie',
  'TvSeries',
  'Anime',
  'AnimeMovie',
  'OVA',
  'Live',
  'Cartoon',
  'Documentary',
  'AsianDrama',
  'NSFW',
  'Others'
] as const;

/** The plugin contract this panel (and the app build) targets. */
export const FMHUB_PLUGIN_API_VERSION = 1;
