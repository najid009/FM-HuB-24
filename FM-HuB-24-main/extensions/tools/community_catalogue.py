#!/usr/bin/env python3
"""Inspect a CloudStream extension repository and write a rebuild catalogue.

Give it any `repo.json` (or `plugins.json`) URL and it answers, per provider, the only questions
that matter when the extensions are not yours:

  * which site(s) does it actually scrape          -> host strings inside classes.dex
  * will FMHub's host be able to load it           -> extends BasePlugin, or the app-module `Plugin`?
  * what will fail even if it loads                -> references to CloudStream's app-only classes
  * can we rebuild it against :plugin-api           -> does the upstream repo contain .kt sources?

Everything is read from the published `.cs3` (zip central directory + dex strings); nothing is
executed, and no site is contacted. GitHub access goes through the `gh` CLI so it uses your existing
authentication (unauthenticated API calls would hit the 60/hour limit long before 86 files).

  python3 tools/community_catalogue.py \
      https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json \
      --out-md ../docs/community/phisher98-catalogue.md \
      --out-json ../docs/community/phisher98-catalogue.json

Blobs are cached under /tmp so a second run is free (--cache to move it).
"""
from __future__ import annotations

import argparse
import base64
import concurrent.futures as cf
import hashlib
import io
import json
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile
from collections import Counter

# ---------------------------------------------------------------- GitHub helpers

RAW_RE = re.compile(r'https://raw\.githubusercontent\.com/([^/]+)/([^/]+)/refs/heads/([^/]+)/(.+)')
RAW_RE2 = re.compile(r'https://raw\.githubusercontent\.com/([^/]+)/([^/]+)/([^/]+)/(.+)')


def gh_api(path: str, *args: str) -> str:
    cmd = ['gh', 'api', path, *args]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
    if r.returncode != 0:
        raise RuntimeError((r.stderr or r.stdout).strip()[:400])
    return r.stdout


def gh_json(path: str):
    return json.loads(gh_api(path))


def parse_raw(url: str):
    m = RAW_RE.match(url) or RAW_RE2.match(url)
    if not m:
        raise SystemExit(f'expected a raw.githubusercontent.com URL, got: {url}')
    owner, repo, ref, path = m.groups()
    if ref == 'refs':                     # .../refs/heads/<branch>/...
        owner, repo, _, ref, path = owner, repo, ref, path.split('/')[0], '/'.join(path.split('/')[1:])
    return owner, repo, ref, path


def fetch_text_file(owner: str, repo: str, ref: str, path: str) -> str:
    try:
        data = gh_json(f'repos/{owner}/{repo}/contents/{path}?ref={ref}')
    except Exception:
        # Branch names are not portable (`main` vs `master`), and a 404 on the manifest otherwise
        # kills the whole run. Ask GitHub what the default branch is and retry once.
        default = (gh_json(f'repos/{owner}/{repo}').get('default_branch') or '').strip()
        if not default or default == ref:
            raise
        print(f'  ref {ref!r} not found, using default branch {default!r}')
        data = gh_json(f'repos/{owner}/{repo}/contents/{path}?ref={default}')
        globals()['DEFAULT_REF_FIX'] = (owner, repo, default)
    return base64.b64decode(data['content']).decode('utf-8', 'replace')


def blob_for_url(url: str, cache: pathlib.Path) -> bytes:
    owner, repo, ref, path = parse_raw(url)
    key = cache / f'{owner}_{repo}_{re.sub(r"[^A-Za-z0-9._-]", "_", path)}'
    if key.exists():
        return key.read_bytes()
    try:
        data = gh_json(f'repos/{owner}/{repo}/contents/{path}?ref={ref}')
        # Large files answer with encoding "none" and an empty content, without any error - so the
        # size has to be checked here, or the catalogue would quietly read a zero-byte package.
        if data.get('encoding') != 'base64' or not data.get('content') or data.get('truncated'):
            raise RuntimeError(f'contents API will not inline this file ({data.get("size")} bytes), using the blob API')
        blob = base64.b64decode(data['content'])
        if data.get('size') and len(blob) != data['size']:
            raise RuntimeError(f'contents API returned {len(blob)} of {data["size"]} bytes')
    except Exception:
        # Files over ~1 MB are refused by the contents API; go through the blob API instead.
        tree = gh_json(f'repos/{owner}/{repo}/git/trees/{ref}?recursive=1')
        sha = next((t['sha'] for t in tree.get('tree', []) if t['path'] == path), None)
        if not sha:
            raise
        # The JSON blob endpoint caps the base64 payload, so ask for the raw body instead.
        raw = subprocess.run(['gh', 'api', f'repos/{owner}/{repo}/git/blobs/{sha}',
                              '-H', 'Accept: application/vnd.github.raw'],
                             capture_output=True, timeout=180)
        if raw.returncode != 0 or not raw.stdout:
            data = gh_json(f'repos/{owner}/{repo}/git/blobs/{sha}')
            blob = base64.b64decode(data['content'])
        else:
            blob = raw.stdout
    key.write_bytes(blob)
    return blob


# ---------------------------------------------------------------- dex analysis

# Kept deliberately in sync with android-app .../plugins/PluginLoader.kt, with one addition: a type
# is only "unresolved" if THIS host does not provide it. Everything in com.github.recloudstream.
# cloudstream:library:v4.8.0 resolves (it is in the app dex), and the two shims under
# :plugin-api/src/main/kotlin resolve too (com.lagradost.cloudstream3.utils.DataStore and its
# PreferenceDelegate). Types under app/src/main/java/... resolve in nothing but the CloudStream app.
APP_ONLY_EXACT = {
    'com.lagradost.cloudstream3.plugins.Plugin':
        'base class of *bundled* extensions; a .cs3 must extend BasePlugin instead',
    'com.lagradost.cloudstream3.CloudStreamApp':
        'the app Application object (context, activity, image loader)',
    'com.lagradost.cloudstream3.CommonActivity':
        'app activity plumbing (toasts, dialogs, webview callbacks)',
    'com.lagradost.cloudstream3.MainActivity':
        'the app player activity (note: MainActivityKt is a library file facade, that one is fine)',
}
APP_ONLY_PREFIX = {
    'com.lagradost.cloudstream3.utils.DataStoreHelper': 'app prefs helper (our host exposes utils.DataStore)',
    'com.lagradost.cloudstream3.utils.DataStoreFileHelper': 'app prefs file helper',
    'com.lagradost.cloudstream3.utils.UIHelper': 'app UI helpers',
    'com.lagradost.cloudstream3.ui.': 'app UI package',
    'com.lagradost.cloudstream3.database.': 'app Room database (watch history)',
    'com.lagradost.cloudstream3.actions.': 'app video click actions',
    'com.lagradost.cloudstream3.syncproviders.AccountManager': 'app sync/trakt layer',
}
# Types a *rebuild* fixes by themselves: community modules keep their own Extractors.kt next to the
# provider, and those compile into the extension dex - only refs to the app's copies are a problem.
# v4.8.0 moved the bundled embed extractors from the app module into the library, so a reference to
# `com.lagradost.cloudstream3.extractors.StreamTape` resolves in THIS host as well — 111 of them do.
# Verified against the tag's tree (library/src/commonMain/kotlin/com/lagradost/cloudstream3/extractors/),
# which is why an `extractors.X` ref is only a problem when X is missing from this set.
EMBED_PREFIX = 'com.lagradost.cloudstream3.extractors.'
LIBRARY_EXTRACTORS = frozenset({
    'Acefile',
    'AesHelper',
    'AsianEmbedHelper',
    'Blogger',
    'ByseSX',
    'Cda',
    'CineMMRedirect',
    'CloudMailRuExtractor',
    'ContentXExtractor',
    'CryptoJSHelper',
    'Dailymotion',
    'DoodExtractor',
    'Embedgram',
    'EmturbovidExtractor',
    'Evolaod',
    'Fastream',
    'Filegram',
    'Filemoon',
    'Filesim',
    'Firestream',
    'Flyfile',
    'GDMirrorbot',
    'GUpload',
    'GamoVideo',
    'Gdriveplayer',
    'GenericM3U8',
    'Gofile',
    'GogoHelper',
    'GoodstreamExtractor',
    'HDMomPlayerExtractor',
    'HDPlayerSystemExtractor',
    'HDStreamAbleExtractor',
    'HotlingerExtractor',
    'HubCloud',
    'Hxfile',
    'InternetArchive',
    'JWPlayer',
    'JWPlayerHelper',
    'Jeniusplay',
    'Krakenfiles',
    'Linkbox',
    'LuluStream',
    'M3u8Manifest',
    'MailRuExtractor',
    'Maxstream',
    'Mediafire',
    'MixDrop',
    'Moviehab',
    'Mp4Upload',
    'Mvidoo',
    'NineAnimeHelper',
    'OdnoklassnikiExtractor',
    'OkRuExtractor',
    'PeaceMakerstExtractor',
    'PixelDrainExtractor',
    'PlayLtXyz',
    'PlayerVoxzer',
    'Rabbitstream',
    'RapidVidExtractor',
    'SBPlay',
    'SecvideoOnline',
    'Sendvid',
    'SibNetExtractor',
    'SobreatsesuypExtractor',
    'StreamEmbed',
    'StreamSB',
    'StreamSilk',
    'StreamTape',
    'StreamWishExtractor',
    'Streamcash',
    'Streamhub',
    'Streamlare',
    'StreamoUpload',
    'Streamplay',
    'Supervideo',
    'TRsTXExtractor',
    'Tantifilm',
    'TauVideoExtractor',
    'Up4Stream',
    'UpstreamExtractor',
    'Uqload',
    'Userload',
    'Userscloud',
    'Uservideo',
    'Vicloud',
    'VidHidePro',
    'VidMoxyExtractor',
    'VidStack',
    'Vidara',
    'Videa',
    'VideoSeyredExtractor',
    'VidhideExtractor',
    'Vidmoly',
    'Vido',
    'Vidoza',
    'Vids',
    'Vidsonic',
    'Vinovo',
    'VkExtractor',
    'Voe',
    'VstreamhubHelper',
    'Vtbe',
    'WatchSB',
    'WcoHelper',
    'Wibufile',
    'XStreamCdn',
    'YourUpload',
    'YoutubeExtractor',
    'YoutubeExtractor.jvmCommon',
    'Zplayer',
})

HOST_PROVIDED_PREFIX = ('com.lagradost.cloudstream3.utils.DataStore',)   # our :plugin-api shim
# library file facades (XxxKt) that live in :library and therefore already resolve
LIBRARY_FACADES = ('com.lagradost.cloudstream3.MainActivityKt', 'com.lagradost.cloudstream3.MainAPIKt',
                   'com.lagradost.cloudstream3.ParCollectionsKt', 'com.lagradost.cloudstream3.AppConfigKt',
                   'com.lagradost.cloudstream3.mvvm.', 'com.lagradost.cloudstream3.utils.AppUtils',
                   'com.lagradost.cloudstream3.utils.ExtractorApi')

HOST_RE = re.compile(rb'https?://([A-Za-z0-9][A-Za-z0-9._-]*\.[A-Za-z]{2,})')
CLASS_REF_RE = re.compile(rb'L(com/lagradost/cloudstream3[A-Za-z0-9_$/-]*);')
NOISE = ('github.com', 'githubusercontent.com', 'googleapis.com', 'google.com', 'w3.org',
         'schema.org', 'json-schema.org', 'android.com', 'jetbrains.com', 'gradle.org',
         'buymeacoffee.com', 'paypal.me', 'patreon.com', 'discord.gg', 't.me', 'telegram.me',
         'wikipedia.org', 'imdb.com', 'themoviedb.org', 'tmdb.org', 'image.tmdb.org',
         'anilist.co', 'kitsu.app', 'trakt.tv', 'jikan.moe', 'ani.zip', 'metahub.space',
         'fanart.tv', 'theaudiodb.com', 'musixmatch.com', 'googleusercontent.com')


def analyze_blob(blob: bytes) -> dict:
    out = {'readable': False}
    try:
        z = zipfile.ZipFile(io.BytesIO(blob))
    except Exception as exc:
        out['error'] = f'not a zip: {exc}'
        return out
    names = z.namelist()
    out['entries'] = names
    if 'classes.dex' not in names:
        out['error'] = 'no classes.dex'
        return out
    out['readable'] = True
    out['dexMethod'] = 'STORED' if z.getinfo('classes.dex').compress_type == 0 else 'DEFLATED'
    dex = z.read('classes.dex')
    out['dexSize'] = len(dex)
    try:
        out['manifest'] = json.loads(z.read('manifest.json').decode('utf-8', 'replace'))
    except Exception as exc:
        out['manifest'] = None
        out['manifestError'] = str(exc)

    out['extendsPlugin'] = b'Lcom/lagradost/cloudstream3/plugins/Plugin;' in dex
    out['shimDataStore'] = False
    out['extendsBasePlugin'] = b'Lcom/lagradost/cloudstream3/plugins/BasePlugin;' in dex
    out['usesExtractorApi'] = b'Lcom/lagradost/cloudstream3/utils/ExtractorApi;' in dex

    refs = Counter(c.decode().replace('/', '.') for c in CLASS_REF_RE.findall(dex))
    unresolved, embeds, provided = [], [], []
    for cls in sorted({c for c in refs}):
        if any(cls.startswith(p) for p in LIBRARY_FACADES):
            continue
        if cls.startswith(HOST_PROVIDED_PREFIX):
            provided.append(cls)
            continue
        if cls in APP_ONLY_EXACT or any(cls.startswith(p) for p in APP_ONLY_PREFIX):
            unresolved.append(cls)
        elif cls.startswith(EMBED_PREFIX):
            embeds.append(cls)
    out['appOnlyRefs'] = unresolved
    out['embedRefs'] = embeds
    out['hostProvidedRefs'] = provided
    out['appOnlyWhy'] = {c: APP_ONLY_EXACT.get(c) or APP_ONLY_PREFIX.get(
        next((p for p in APP_ONLY_PREFIX if c.startswith(p)), ''), '') for c in unresolved}
    out['cloudstreamSymbols'] = len(refs)

    hosts = Counter(h.decode().lower() for h in HOST_RE.findall(dex))
    clean = [(h, n) for h, n in hosts.most_common() if not any(x in h for x in NOISE)]
    out['hosts'] = [h for h, _ in clean[:6]]
    out['hostCount'] = len(clean)
    out['mediaHints'] = sorted({m.decode() for m in re.findall(rb'\.(m3u8|mpd|mp4|webm|ts)\b', dex)})[:6]
    return out


def verdict(info: dict, entry: dict) -> tuple[str, str]:
    """(code, explanation) - mirrors what THIS host can actually resolve at runtime."""
    if not info.get('readable'):
        return 'UNREADABLE', info.get('error', 'could not open the package')
    manifest = info.get('manifest') or {}
    unresolved = info.get('appOnlyRefs', [])
    embeds = info.get('embedRefs', [])
    if info.get('extendsPlugin') and not info.get('extendsBasePlugin'):
        return 'NEEDS-ENTRY-SHIM', ('the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin '
                                   "(app module), so loadClass fails with \"Didn't find class\" before anything runs")
    hard = [c for c in unresolved if c in APP_ONLY_EXACT]
    if hard:
        return 'HOST-COMPAT', ('loads, but its code calls app-only types (' + ', '.join(c.rsplit('.', 1)[-1] for c in hard[:4]) +
                               ') - those paths throw unless the host provides a matching type')
    if unresolved:
        return 'LOADS-PARTIAL', 'touches app types on some paths: ' + ', '.join(c.rsplit('.', 1)[-1] for c in unresolved[:3])
    missing_embeds = [e for e in embeds if e.rsplit('.', 1)[-1] not in LIBRARY_EXTRACTORS]
    if missing_embeds:
        return 'EMBED-REFS', ('references embed extractors this host does not ship (' +
                             ', '.join(e.rsplit('.', 1)[-1] for e in missing_embeds[:3]) +
                             ') - those embed links throw NoSuchMethod/ClassNotFound; a rebuild fixes it by keeping the extractor sources in the module')
    if embeds:
        return 'READY', ('extends BasePlugin and its embed extractors (' + ', '.join(e.rsplit('.', 1)[-1] for e in embeds[:3]) +
                         ') come from the library, so they resolve here too')
    if manifest.get('requiresResources'):
        return 'RESOURCES', 'requiresResources=true: needs the AssetManager resource injection path'
    if not info.get('extendsBasePlugin'):
        return 'UNKNOWN', 'no BasePlugin/Plugin reference found in the dex'
    if entry.get('status') == 0:
        return 'READY-DOWN', 'loadable, but the repo marks the provider status=0 (Down)'
    return 'READY', 'extends BasePlugin, resolves against the library + our shims only'


# ---------------------------------------------------------------- source availability probe

def source_probe(repo_url: str) -> dict:
    m = re.match(r'https://github\.com/([^/]+)/([^/]+?)(?:\.git)?(?:/.*)?$', repo_url or '')
    if not m:
        return {'probed': False}
    owner, repo = m.groups()
    try:
        meta = gh_json(f'repos/{owner}/{repo}')
        branch = meta.get('default_branch') or 'main'
        tree = gh_json(f'repos/{owner}/{repo}/git/trees/{branch}?recursive=1')
        kt = [t['path'] for t in tree.get('tree', []) if t['path'].endswith(('.kt', '.kts'))]
        return {
            'probed': True, 'repo': f'{owner}/{repo}', 'defaultBranch': branch,
            'kotlinFiles': len(kt), 'license': (meta.get('license') or {}).get('spdx_id'),
            'archived': bool(meta.get('archived')),
            'buildFiles': sum(1 for p in kt if p.endswith('build.gradle.kts')),
            'sample': sorted(kt)[:2],
        }
    except Exception as exc:
        return {'probed': True, 'repo': f'{owner}/{repo}', 'error': str(exc)[:120]}


# ---------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('url', help='repo.json or plugins.json raw URL of a community repository')
    ap.add_argument('--out-md', default=None)
    ap.add_argument('--out-json', default=None)
    ap.add_argument('--cache', default='/tmp/fmhub-catalogue-cache')
    ap.add_argument('--workers', type=int, default=10)
    ap.add_argument('--limit', type=int, default=0)
    ap.add_argument('--no-probe', action='store_true', help='skip the upstream-source probe')
    args = ap.parse_args()

    if shutil.which('gh') is None:
        sys.exit('needs the GitHub CLI (gh) authenticated: `gh auth status`')

    cache = pathlib.Path(args.cache)
    cache.mkdir(parents=True, exist_ok=True)

    owner, repo, ref, path = parse_raw(args.url)
    text = fetch_text_file(owner, repo, ref, path)
    entries_url = None
    if path.endswith('repo.json'):
        meta = json.loads(text)
        lists = meta.get('pluginLists') or []
        if not lists:
            sys.exit('repo.json has no pluginLists')
        entries_url = lists[0]
        repo_name = meta.get('name') or repo
    else:
        repo_name = repo
    if entries_url:
        eo, er, ef, ep = parse_raw(entries_url)
        entries = json.loads(fetch_text_file(eo, er, ef, ep))
    else:
        entries = json.loads(text)
    if args.limit:
        entries = entries[:args.limit]
    print(f'{repo_name}: {len(entries)} entries', file=sys.stderr)

    probes: dict[str, dict] = {}
    if not args.no_probe:
        urls = sorted({e.get('repositoryUrl') or '' for e in entries if e.get('repositoryUrl')})
        with cf.ThreadPoolExecutor(max_workers=6) as pool:
            for url, res in zip(urls, pool.map(source_probe, urls)):
                probes[url] = res

    def work(entry):
        try:
            blob = blob_for_url(entry['url'], cache)
        except Exception as exc:
            return entry, {'readable': False, 'error': f'fetch failed: {exc}'}, ('FETCH-FAIL', str(exc)[:160])
        info = analyze_blob(blob)
        code, why = verdict(info, entry)
        return entry, info, (code, why)

    rows = []
    with cf.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for i, (entry, info, (code, why)) in enumerate(pool.map(work, entries), 1):
            manifest = info.get('manifest') or {}
            pr = probes.get(entry.get('repositoryUrl') or '', {})
            rows.append({
                'provider': entry.get('name') or entry.get('internalName'),
                'internalName': entry.get('internalName'),
                'language': entry.get('language'),
                'tvTypes': entry.get('tvTypes') or [],
                'version': entry.get('version'),
                'repoStatus': entry.get('status'),
                'requiresResources': manifest.get('requiresResources'),
                'pluginClassName': manifest.get('pluginClassName'),
                'dexMethod': info.get('dexMethod'),
                'dexSize': info.get('dexSize'),
                'fileSize': entry.get('fileSize'),
                'fileHash': entry.get('fileHash'),
                'hosts': info.get('hosts', []),
                'hostCount': info.get('hostCount', 0),
                'extendsPlugin': info.get('extendsPlugin'),
                'extendsBasePlugin': info.get('extendsBasePlugin'),
                'appOnlyRefs': info.get('appOnlyRefs', []),
            'appOnlyWhy': info.get('appOnlyWhy', {}),
            'embedRefs': info.get('embedRefs', []),
            'hostProvidedRefs': info.get('hostProvidedRefs', []),
                'mediaHints': info.get('mediaHints', []),
                'verdict': code,
                'why': why,
                'repositoryUrl': entry.get('repositoryUrl'),
                'upstream': pr,
                'url': entry.get('url'),
            })
            if i % 20 == 0:
                print(f'  {i}/{len(entries)} analysed', file=sys.stderr)

    counts = Counter(r['verdict'] for r in rows)
    unlock = Counter()
    for r in rows:
        for cls in set(r['appOnlyRefs']) | set(r['embedRefs']):
            unlock[cls] += 1
    embed_only = [r for r in rows if r['verdict'] == 'EMBED-REFS']
    rebuildable = [r for r in rows if r['upstream'].get('kotlinFiles', 0) > 0 and r['verdict'] in ('READY', 'RESOURCES', 'READY-DOWN')]

    if args.out_json:
        pathlib.Path(args.out_json).parent.mkdir(parents=True, exist_ok=True)
        pathlib.Path(args.out_json).write_text(json.dumps({
            'source': {'repo': args.url, 'entries': len(entries)},
            'summary': dict(counts), 'rebuildableCount': len(rebuildable), 'rows': rows,
        }, indent=1))
        print(f'wrote {args.out_json}', file=sys.stderr)

    if args.out_md:
        lines = [f'# Community extension catalogue - {repo_name}', '']
        lines += [f'Generated by `extensions/tools/community_catalogue.py` from `{args.url}`.',
                  'Read-only analysis of the published `.cs3` files (zip + dex strings). **Do not edit by hand.**', '']
        lines += ['## Summary', '', '| verdict | providers | meaning |', '| --- | --- | --- |']
        meanings = {
            'READY': 'extends `BasePlugin`, resolves against the library + our shims only - loads as is',
            'READY-DOWN': 'loads, but the repo marks the provider `status=0` (Down)',
            'RESOURCES': 'needs `requiresResources` (resources injected via AssetManager)',
            'EMBED-REFS': 'loads; references embed extractors this host does not ship, so those hosts fail until rebuilt',
            'LOADS-PARTIAL': 'loads; some code paths touch app-module types and throw there',
            'HOST-COMPAT': 'loads, but calls app-only types (`CloudStreamApp`, `CommonActivity`, ...) - those code paths throw until the host provides a matching type',
            'NEEDS-ENTRY-SHIM': 'entry class extends the app-module `Plugin` - `loadClass` fails outright',
            'UNKNOWN': 'no plugin base class found in the dex',
            'UNREADABLE': 'package could not be opened',
            'FETCH-FAIL': 'file could not be downloaded',
        }
        for code in ('READY', 'READY-DOWN', 'RESOURCES', 'EMBED-REFS', 'LOADS-PARTIAL', 'HOST-COMPAT',
                     'NEEDS-ENTRY-SHIM', 'UNKNOWN', 'UNREADABLE', 'FETCH-FAIL'):
            if counts.get(code):
                lines.append(f'| `{code}` | {counts[code]} | {meanings[code]} |')
        lines.append(f'| **total** | **{len(rows)}** | |')
        lines.append('')
        lines += ['## Host work list - biggest unlock first', '',
                  'Each row is one CloudStream app-module type and how many providers stop failing if the host',
                  'provides it (a shim in `:plugin-api`, or the class vendored into the app).', '']
        lines.append('| missing type | providers | what it is |')
        lines.append('| --- | --- | --- |')
        for cls, n in unlock.most_common(12):
            why = next((r['appOnlyWhy'].get(cls) for r in rows if r['appOnlyWhy'].get(cls)), '')
            if not why and cls.startswith('com.lagradost.cloudstream3.extractors.'):
                why = 'bundled extractor - copy the extractor source into the module instead'
            lines.append(f'| `{cls.rsplit(".", 1)[-1]}` | {n} | {why or "-"} |')
        lines += ['', f'*providers whose only problem is the app\'s bundled extractors: {len(embed_only)}*', '']
        by_repo = {}
        for r in rows:
            key = r['upstream'].get('repo') or (r['repositoryUrl'] or 'unknown')
            by_repo.setdefault(key, []).append(r)
        lines += ['## Upstream source availability', '',
                  '`rebuild against :plugin-api` is only possible where the source is published.', '']
        lines.append('| repo | providers here | .kt files upstream | license |')
        lines.append('| --- | --- | --- | --- |')
        for key, rs in sorted(by_repo.items(), key=lambda kv: -len(kv[1])):
            pr = rs[0]['upstream']
            kt = 'not probed' if not pr.get('probed') else (str(pr.get('kotlinFiles')) if not pr.get('error') else 'probe failed')
            lines.append(f'| `{key}` | {len(rs)} | {kt} | {pr.get("license") or "none stated"} |')
        withsrc = [r for r in rows if r['upstream'].get('kotlinFiles')]
        lines += ['', f'* `repositoryUrl` of {len(withsrc)}/{len(rows)} entries points at a repo that publishes `.kt` sources.*',
                  ' A source-less `repositoryUrl` does not mean the provider is unrebuildable - the same modules',
                  ' are often published in public mirrors/forks; find one with',
                  ' `gh api "search/code?q=<ProviderName>+extension:kt"` and read its `src/main/kotlin` tree.', '']
        lines += ['## Providers', '',
                  '| provider | lang | verdict | main site(s) | app-module refs | dex | ver | res | pluginClassName |',
                  '| --- | --- | --- | --- | --- | --- | --- | --- | --- |']
        order = {'READY': 0, 'READY-DOWN': 1, 'RESOURCES': 2, 'LOADS-PARTIAL': 3, 'NEEDS-SHIM': 4, 'UNKNOWN': 5, 'UNREADABLE': 6, 'FETCH-FAIL': 7}
        for r in sorted(rows, key=lambda x: (order.get(x['verdict'], 9), x['provider'] or '')):
            hosts = ', '.join(f'`{h}`' for h in r['hosts'][:3]) or '-'
            app = ', '.join(f'`{c.split(".")[-1]}`' for c in r['appOnlyRefs'][:3]) or '-'
            res = 'yes' if r.get('requiresResources') else ''
            name = r['provider'] or r['internalName']
            link = f'[{name}]({r["repositoryUrl"]})' if r.get('repositoryUrl') else name
            lines.append(f"| {link} | {r['language'] or '-'} | `{r['verdict']}` | {hosts} | {app} | "
                         f"{r['dexMethod'] or '-'} | {r['version']} | {res} | `{r['pluginClassName'] or '-'}` |")
        lines += ['', '## Why these verdicts look the way they do', '',
                  '* `NEEDS-ENTRY-SHIM` - the dex *does* contain the provider class, but its superclass is '
                  '`com.lagradost.cloudstream3.plugins.Plugin`, which lives in CloudStream\'s **app module**. '
                  'A host that ships only the library then reports `Didn\'t find class <the entry class>` - '
                  'the message is about the class hierarchy, not a corrupt or renamed file. This is what a '
                  'community `.cs3` that "refuses to load" usually means.',
                  '* `EMBED-REFS` / `LOADS-PARTIAL` - the provider appears in the app and searching works; only '
                  'the code path that touches an app-module type (usually a specific embed host) throws.',
                  '* dex entries here are `DEFLATED` in practice, and CloudStream loads them, so a compressed '
                  '`classes.dex` is a warning, not a blocker (`STORED` is only faster to mmap).',
                  '* `fileHash` in real repositories is written as `sha256-<hex>`, so any verifier must strip '
                  'the `sha256-` prefix before comparing, and `status` means `0=Down, 1=Ok, 2=Slow, 3=Beta`.', '',
                  '## To rebuild one of these against our library', '',
                  '1. Clone the upstream source repo and copy its provider `.kt` files into '
                  '`extensions/<provider>/src/main/java/<package>/`.',
                  '2. Copy `extensions/fmhub-archive/build.gradle.kts` (root build already wires '
                  '`compileOnly` `com.fmhub24.pluginapi:plugin-api` + `library`, and `tools/make_repo.py`).',
                  '3. `include(":<provider>")` in `extensions/settings.gradle.kts`; drop the app-module imports '
                  '(`Plugin`, `CommonActivity`, `utils.UIHelper`, `MainActivity`) and extend `FMHubBasePlugin` / `FMHubProvider` instead. '
                  'Embed extractors under `com.lagradost.cloudstream3.extractors.*` are NOT a problem: v4.8.0 ships them in the library.',
                  '4. `./gradlew :<provider>:make` locally, or push - CI builds every module, packs `repo.json` and can publish it.',
                  '5. Import the published `repo.json` in the admin panel and tick the row.', '']
        pathlib.Path(args.out_md).parent.mkdir(parents=True, exist_ok=True)
        pathlib.Path(args.out_md).write_text('\n'.join(lines))
        print(f'wrote {args.out_md}', file=sys.stderr)

    print(json.dumps({'entries': len(rows), 'summary': dict(counts), 'rebuildable': len(rebuildable)}))
    return 0


if __name__ == '__main__':
    sys.exit(main())
