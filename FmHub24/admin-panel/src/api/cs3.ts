/**
 * Reader for the CloudStream package format (.cs3 = plain zip with manifest.json + classes.dex).
 *
 * Why parse it in the browser: a manifest we are *told* about and a manifest we *read* are not the
 * same thing. Pulling pluginClassName / version / requiresResources out of the uploaded file is what
 * makes the "extension loaded 0 providers" class of bug impossible to hide, and it lets the app
 * verify the bytes it downloaded against the hash recorded at upload time.
 *
 * Only the zip central directory is parsed (no decompression except for manifest.json, which uses the
 * platform's DecompressionStream) — that keeps this dependency-free and synchronous-ish.
 */

export interface ZipEntry {
  name: string;
  method: number;
  compressedSize: number;
  uncompressedSize: number;
  localHeaderOffset: number;
}

export interface Cs3Manifest {
  name?: string;
  pluginClassName?: string;
  requiresResources?: boolean;
  version?: number;
  /** Our contract version, injected by extensions/tools/make_repo.py. */
  fmhubApiVersion?: number;
}

export interface Cs3Inspection {
  entries: ZipEntry[];
  manifest: Cs3Manifest | null;
  manifestRaw: string | null;
  hasDex: boolean;
  hasResources: boolean;
  /** Res ARSC + res/ folder; needs the file to be repacked STORED for them to be readable. */
  sha256: string | null;
  sizeBytes: number;
  error?: string;
}

const EOCD_SIGNATURE = 0x06054b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const LOCAL_SIGNATURE = 0x04034b50;

/** The zip magic bytes; a .cs3 that does not start with these is not a package we can load. */
export async function readMagic(bytes: Blob | File): Promise<number[]> {
  const buffer = await bytes.slice(0, 4).arrayBuffer();
  return Array.from(new Uint8Array(buffer));
}

export function isZip(magic: number[]): boolean {
  return magic[0] === 0x50 && magic[1] === 0x4b && magic[2] === 0x03 && magic[3] === 0x04;
}

export async function sha256Hex(bytes: Blob | File): Promise<string | null> {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) return null; // non-secure origins have no SubtleCrypto; caller must tolerate that
  const digest = await subtle.digest('SHA-256', await bytes.arrayBuffer());
  return Array.from(new Uint8Array(digest))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

/** All names in the archive, read from the central directory. */
export function readCentralDirectory(buffer: ArrayBuffer): ZipEntry[] {
  const view = new DataView(buffer);
  const eocd = findEocd(view);
  if (eocd < 0) throw new Error('No zip central directory (not a .cs3/.apk/.zip archive)');

  const total = view.getUint16(eocd + 10, true);
  const directoryOffset = view.getUint32(eocd + 16, true);
  const decoder = new TextDecoder('utf-8');
  const bytes = new Uint8Array(buffer);

  const entries: ZipEntry[] = [];
  let pointer = directoryOffset;
  for (let i = 0; i < total && pointer + 46 <= buffer.byteLength; i++) {
    if (view.getUint32(pointer, true) !== CENTRAL_SIGNATURE) break;
    const method = view.getUint16(pointer + 10, true);
    const compressedSize = view.getUint32(pointer + 20, true);
    const uncompressedSize = view.getUint32(pointer + 24, true);
    const nameLength = view.getUint16(pointer + 28, true);
    const extraLength = view.getUint16(pointer + 30, true);
    const commentLength = view.getUint16(pointer + 32, true);
    const localHeaderOffset = view.getUint32(pointer + 42, true);
    const name = decoder.decode(bytes.subarray(pointer + 46, pointer + 46 + nameLength));
    entries.push({ name, method, compressedSize, uncompressedSize, localHeaderOffset });
    pointer += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

function findEocd(view: DataView): number {
  // The end-of-central-directory record is the last 22..65557 bytes; scanning backwards is what
  // `unzip` does too, and it is the only way to tolerate a trailing archive comment.
  const min = Math.max(0, view.byteLength - 22 - 65535);
  for (let i = view.byteLength - 22; i >= min; i--) {
    if (view.getUint32(i, true) === EOCD_SIGNATURE) return i;
  }
  return -1;
}

async function inflateRaw(bytes: Uint8Array, expectedSize: number): Promise<Uint8Array> {
  const stream = new Blob([bytes as BlobPart])
    .stream()
    .pipeThrough(new DecompressionStream('deflate-raw'));
  const chunks: Uint8Array[] = [];
  const reader = stream.getReader();
  let written = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value as Uint8Array);
    written += (value as Uint8Array).length;
    // Some hosts lie about the uncompressed size when the entry was streamed; stop at the
    // manifest's natural end rather than looping forever.
    if (expectedSize > 0 && written > expectedSize * 64) break;
  }
  const out = new Uint8Array(written);
  let offset = 0;
  for (const chunk of chunks) {
    out.set(chunk, offset);
    offset += chunk.length;
  }
  return out;
}

/** Read one entry's bytes. Stored entries need no work; deflated ones are inflated. */
export async function readEntry(buffer: ArrayBuffer, entry: ZipEntry): Promise<Uint8Array> {
  const view = new DataView(buffer);
  if (view.getUint32(entry.localHeaderOffset, true) !== LOCAL_SIGNATURE) {
    throw new Error(`Corrupt local header for ${entry.name}`);
  }
  const nameLength = view.getUint16(entry.localHeaderOffset + 26, true);
  const extraLength = view.getUint16(entry.localHeaderOffset + 28, true);
  const start = entry.localHeaderOffset + 30 + nameLength + extraLength;
  const stored = new Uint8Array(buffer, start, entry.compressedSize);
  if (entry.method === 0) return stored;
  if (entry.method === 8) return inflateRaw(stored, entry.uncompressedSize);
  throw new Error(`Unsupported zip compression method ${entry.method} for ${entry.name}`);
}

/**
 * Everything we can learn from a package before it is offered to the app.
 * Throws only for "this is not a zip at all"; partial damage is reported in `error` so the admin
 * can still publish the file with whatever metadata is known.
 */
export async function inspectCs3(file: File): Promise<Cs3Inspection> {
  const buffer = await file.arrayBuffer();
  const entries = readCentralDirectory(buffer); // throws when it is not a zip
  const manifestEntry = entries.find(entry => entry.name === 'manifest.json');

  let manifest: Cs3Manifest | null = null;
  let manifestRaw: string | null = null;
  let error: string | undefined;

  if (manifestEntry) {
    try {
      manifestRaw = new TextDecoder().decode(await readEntry(buffer, manifestEntry));
      manifest = JSON.parse(manifestRaw) as Cs3Manifest;
    } catch (e) {
      error = `manifest.json is unreadable: ${(e as Error).message}`;
    }
  } else {
    error = 'No manifest.json in the archive — CloudStream packages must contain one';
  }

  return {
    entries,
    manifest,
    manifestRaw,
    hasDex: entries.some(entry => entry.name === 'classes.dex'),
    hasResources: entries.some(entry => entry.name === 'resources.arsc'),
    sha256: await sha256Hex(file),
    sizeBytes: file.size,
    error,
  };
}

/**
 * STORED (`method === 0`) vs DEFLATED (`method === 8`).
 *
 * CloudStream's own gradle plugin emits a STORED dex (so `DexClassLoader` can mmap it in place), but
 * the loader copies the package to a real file first, and Android happily reads a *deflated* dex out
 * of a zip on every supported API level — all 86 packages of the phisher98 community repo are deflated
 * and load fine. So this is a preference, not a requirement: callers must warn, never refuse.
 */
export function dexMethod(entries: ZipEntry[]): number | null {
  const dex = entries.find(entry => entry.name === 'classes.dex');
  return dex ? dex.method : null;
}

/** Kept for older call sites; use `dexMethod` and treat `false` as a warning. */
export function isDexStored(entries: ZipEntry[]): boolean {
  return dexMethod(entries) === 0;
}

/**
 * Types that live in CloudStream's **app** module. A host that ships `:library` + its own
 * `:plugin-api` (FMHub24 does) has never seen them, so a package that links against them dies at
 * `loadClass` — the single most common reason a community `.cs3` "does nothing".
 */
export const APP_ONLY_TYPES = [
  'Lcom/lagradost/cloudstream3/CloudStreamApp;',
  'Lcom/lagradost/cloudstream3/CommonActivity;',
  'Lcom/lagradost/cloudstream3/MainActivity;',
  'Lcom/lagradost/cloudstream3/plugins/Plugin;',
  'Lcom/lagradost/cloudstream3/utils/UIHelper;',
  'Lcom/lagradost/cloudstream3/utils/DataStoreHelper;',
] as const;

/** Provided by the library, i.e. by our host too — the entry class must be one of these two. */
export const ENTRY_BASE_TYPES = [
  'Lcom/lagradost/cloudstream3/plugins/BasePlugin;',
  'Lcom/lagradost/cloudstream3/MainAPI;',
] as const;

export interface DexAudit {
  /** 0 STORED, 8 DEFLATED, null when the archive has no classes.dex at all. */
  method: number | null;
  dexBytes: number;
  /** `CloudStreamApp`, `plugins.Plugin`, ... — present means the file was built for a full app. */
  appOnlyTypes: string[];
  extendsBasePlugin: boolean;
  implementsMainApi: boolean;
  /** null when the manifest declares no class; otherwise whether that class exists in the dex. */
  declaredClassFound: boolean | null;
}

/**
 * Reads classes.dex (inflating it when needed) and answers the only question that matters before
 * publishing: could our host actually load this? Dex string ids hold every type descriptor the
 * code references, and descriptors are pure ASCII, so a byte-level scan is exact — `windows-1252`
 * is used only because it is identity-mapped for ASCII.
 */
export async function auditDex(
  buffer: ArrayBuffer,
  entries: ZipEntry[],
  declaredClassName?: string | null,
): Promise<DexAudit | null> {
  const dex = entries.find(entry => entry.name === 'classes.dex');
  if (!dex) return null;
  const bytes = await readEntry(buffer, dex);
  const text = new TextDecoder('windows-1252').decode(bytes);

  let declaredClassFound: boolean | null = null;
  if (declaredClassName) {
    const descriptor = 'L' + declaredClassName.trim().replace(/\./g, '/') + ';';
    declaredClassFound = text.includes(descriptor);
  }

  return {
    method: dex.method,
    dexBytes: bytes.length,
    appOnlyTypes: APP_ONLY_TYPES.filter(type => text.includes(type)),
    extendsBasePlugin: text.includes(ENTRY_BASE_TYPES[0]),
    implementsMainApi: text.includes(ENTRY_BASE_TYPES[1]),
    declaredClassFound,
  };
}
