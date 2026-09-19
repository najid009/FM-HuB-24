#!/usr/bin/env python3
"""Turn the community catalogue JSON into a read-only TypeScript module for the admin panel.

The panel's repo-import screen can then say what we already measured about each published binary
(verdict, why, version, whether it extends CloudStream's app-only `Plugin` class) instead of letting
an admin find out on a phone.

    python3 extensions/tools/community_catalogue.py \
        https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/master/plugins.json \
        --out-md docs/community/phisher98-catalogue.md \
        --out-json docs/community/phisher98-catalogue.json --cache /tmp/fmhub-catalogue-cache
    python3 extensions/tools/catalogue_to_panel.py
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

HEADER = '''/**
 * Verdicts for the {n} providers of `{repo}`, produced by `extensions/tools/community_catalogue.py`
 * (source: `docs/community/{basename}.json`). Do not edit by hand - regenerate:
 *
 *   python3 extensions/tools/community_catalogue.py <raw plugins.json url> \\
 *     --out-json docs/community/{basename}.json && python3 extensions/tools/catalogue_to_panel.py
 *
 * Verdicts: READY / READY-DOWN / RESOURCES / EMBED-REFS / LOADS-PARTIAL (needs an embed extractor the
 * host lacks) / HOST-COMPAT (loads, but calls types from CloudStream's *app* module) /
 * NEEDS-ENTRY-SHIM (entry class extends the app-only `plugins.Plugin`, so it can never load here) /
 * UNKNOWN / UNREADABLE / FETCH-FAIL.
 */

export interface CommunityVerdict {{
  provider: string;
  verdict: string | null;
  why: string;
  version: number | null;
  /** CloudStream's own label: 0 down, 1 ok, 2 slow, 3 beta. */
  repoStatus: number | null;
  language: string | null;
  pluginClassName: string | null;
  dex: string | null;
  extendsPlugin: boolean;
  appOnly: string[];
  embeds: string[];
  hosts: string[];
  upstream: string | null;
  /** sha256 hex of the published file as of this scan (the panel stores the same value). */
  fileHash: string | null;
}}

export const COMMUNITY_VERDICTS: Record<string, CommunityVerdict> = '''

FOOTER = '''
/** Case- and punctuation-insensitive lookup: `SFlix`, `sflix`, `sflixprovider` all match one row. */
export function communityNote(name?: string | null, internalName?: string | null): CommunityVerdict | null {
  for (const key of [internalName, name]) {
    if (!key) continue;
    const norm = key.toLowerCase().replace(/[^a-z0-9]/g, '');
    const hit = COMMUNITY_VERDICTS[norm] ?? COMMUNITY_VERDICTS[norm + 'provider'];
    if (hit) return hit;
  }
  return null;
}

/** One sentence for a table cell: verdict + the reason, trimmed. */
export function communityNoteLabel(row: CommunityVerdict): string {
  const status = row.repoStatus === 0 ? ' (repo says down)' : '';
  return `${row.verdict ?? 'UNKNOWN'}${status} — ${row.why || 'no note'}`;
}
'''


def _str(value) -> str | None:
    """The catalogue JSON is a dump of a Python dict, so anything can be None/dict/list in here."""
    if value is None or isinstance(value, str):
        return value or None
    if isinstance(value, (int, float, bool)):
        return str(value)
    if isinstance(value, (list, tuple)):
        return ", ".join(str(v) for v in value if v) or None
    if isinstance(value, dict):
        return "; ".join(f"{k}={v}" for k, v in value.items() if v not in (None, "", [], {})) or None
    return str(value)


def _int(value) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _strlist(value) -> list[str]:
    if not isinstance(value, (list, tuple)):
        return []
    return [str(v) for v in value if v]


def convert(catalogue: Path, out: Path) -> int:
    doc = json.loads(catalogue.read_text(encoding="utf-8"))
    rows = doc.get("rows") or []
    source = (doc.get("source") or {}) if isinstance(doc.get("source"), dict) else {}
    repo = source.get("repo") or "phisher98/cloudstream-extensions-phisher"

    entries: dict[str, dict] = {}
    for row in rows:
        key = (row.get("internalName") or row.get("provider") or "").lower()
        key = "".join(ch for ch in key if ch.isalnum())
        if not key:
            continue
        upstream = row.get("upstream")
        if isinstance(upstream, dict) and upstream:
            # The probe writes {found, repo, path, sha}; flatten it into one clickable label.
            if upstream.get("found"):
                up_repo = upstream.get("repo") or ""
                up_path = upstream.get("path") or ""
                up_sha = (upstream.get("sha") or "")[:8]
                upstream = f"{up_repo}/{up_path}" + (f"@{up_sha}" if up_sha else "")
            else:
                upstream = None
        entries[key] = {
            "provider": _str(row.get("provider")),
            "verdict": _str(row.get("verdict")),
            "why": (_str(row.get("why")) or "")[:300],
            "version": _int(row.get("version")),
            "repoStatus": _int(row.get("repoStatus")),
            "language": _str(row.get("language")),
            "pluginClassName": _str(row.get("pluginClassName")),
            "dex": _str(row.get("dexMethod")),
            "extendsPlugin": bool(row.get("extendsPlugin")),
            "appOnly": [str(ref).rsplit(".", 1)[-1] for ref in _strlist(row.get("appOnlyRefs"))],
            "embeds": _strlist(row.get("embedRefs")),
            "hosts": _strlist(row.get("hosts"))[:4],
            "upstream": _str(upstream),
            "fileHash": (_str(row.get("fileHash")) or "").replace("sha256-", "") or None,
        }

    body = json.dumps(entries, ensure_ascii=False, indent=1)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        HEADER.format(n=len(entries), repo=repo, basename=catalogue.stem) + body + "\n" + FOOTER,
        encoding="utf-8",
    )
    print(f"{out}: {len(entries)} entries, {out.stat().st_size} bytes")
    return 0


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--catalogue", type=Path, default=root / "docs/community/phisher98-catalogue.json")
    ap.add_argument("--out", type=Path, default=root / "admin-panel/src/data/communityCatalogue.ts")
    args = ap.parse_args()
    if not args.catalogue.is_file():
        print(f"missing catalogue: {args.catalogue}", file=sys.stderr)
        return 2
    return convert(args.catalogue, args.out)


if __name__ == "__main__":
    raise SystemExit(main())
