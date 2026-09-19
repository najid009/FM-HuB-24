#!/usr/bin/env python3
"""Build FMHub24's extension repo metadata from the .cs3 files Gradle produced.

What it does, per `*/build/*.cs3`:

1. inject `fmhubApiVersion` into the zip's `manifest.json` (the host refuses a plugin whose
   declared contract version differs, instead of failing with the useless "no MainAPI provider"),
2. compute SHA-256 + byte size (the app verifies the hash after download),
3. write `plugins.json` (the CloudStream `PluginEntry` shape) and `repo.json` (the shape a
   CloudStream client / the FMHub admin panel reads when you paste a repo link),
4. copy the finished packages next to the JSON so a `builds` branch is self-contained.

Typical use:

    cd extensions
    ./gradlew :fmhub-archive:make :fmhub-archive:writeCacheEntry makePluginsJson
    python3 tools/make_repo.py --base-url https://raw.githubusercontent.com/OWNER/REPO/builds
    # -> dist/repo.json, dist/plugins.json, dist/*.cs3   (publish `dist/` to your `builds` branch)
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

# CloudStream's own repo.json/manifest.json contract version. 1 is what every client speaks.
REPO_MANIFEST_VERSION = 1
STATUS_OK = 0


def read_props(root: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for name in ("gradle.properties", "../android-app/gradle.properties"):
        path = (root / name).resolve()
        if not path.is_file():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            props.setdefault(key.strip(), value.strip())
    return props


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 16), b""):
            digest.update(chunk)
    return digest.hexdigest()


def patch_manifest(cs3: Path, api_version: int) -> dict:
    """Rewrite manifest.json inside the .cs3 so it carries fmhubApiVersion. Returns the manifest."""
    with zipfile.ZipFile(cs3) as archive:
        names = archive.namelist()
        if "manifest.json" not in names:
            raise SystemExit(
                f"{cs3}: no manifest.json inside — this is not a CloudStream package. "
                "Build it with `./gradlew :<module>:make` (the cloudstream gradle plugin writes "
                "the manifest); a hand-zipped jar will never load."
            )
        payload = {n: archive.read(n) for n in names}
        raw = payload["manifest.json"].decode("utf-8")

    manifest = json.loads(raw)
    manifest["fmhubApiVersion"] = api_version
    new_bytes = json.dumps(manifest, indent=None, sort_keys=False).encode("utf-8")

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as out:
        for name, data in payload.items():
            # classes.dex must stay STORED for the same reason Android's zipalign keeps it
            # uncompressed: the runtime mmaps it.
            # classes.dex is stored uncompressed: ART mmaps it, and CloudStream's own
            # packaging does the same, so the host never has to inflate a dex at load time.
            compress = zipfile.ZIP_STORED if name.endswith(".dex") else zipfile.ZIP_DEFLATED
            out.writestr(zipfile.ZipInfo(name), data if name != "manifest.json" else new_bytes,
                         compress_type=compress)
    cs3.write_bytes(buffer.getvalue())
    return manifest


def load_gradle_entry(cs3: Path) -> dict:
    """`build/plugin-entry.json` (written by writeCacheEntry) has the repo metadata already."""
    candidate = cs3.parent / "plugin-entry.json"
    if candidate.is_file():
        try:
            return json.loads(candidate.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            pass
    return {}


TV_TYPE_RE = re.compile(r"^com\.lagradost\.cloudstream3\.TvType\$(\w+)$")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=".", help="extensions project root (default: cwd)")
    parser.add_argument("--out", default="dist", help="output directory (default: dist)")
    parser.add_argument("--name", default="FMHub24 Extensions", help="repo display name")
    parser.add_argument("--description", default="FMHub24 self-hosted extension repository")
    parser.add_argument(
        "--base-url",
        required=False,
        default=None,
        help="public URL prefix where the .cs3 files will live, e.g. "
             "https://raw.githubusercontent.com/OWNER/REPO/builds",
    )
    parser.add_argument("--dry-run", action="store_true", help="report only, write nothing")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    out_dir = (root / args.out).resolve()
    props = read_props(root)
    api_version = int(props.get("PLUGIN_API_VERSION", "1"))
    cs_version = props.get("CLOUDSTREAM_VERSION", "?")

    packages = sorted(root.glob("*/build/*.cs3"))
    if not packages:
        print(f"no .cs3 found under {root}/*/build/ — run ./gradlew :<module>:make first", file=sys.stderr)
        return 1

    entries: list[dict] = []
    for cs3 in packages:
        module = cs3.parent.parent.name
        if args.dry_run:
            with zipfile.ZipFile(cs3) as archive:
                manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
        else:
            manifest = patch_manifest(cs3, api_version)

        gradle_entry = load_gradle_entry(cs3)
        digest = sha256_of(cs3)
        size = cs3.stat().st_size

        entry = {
            "name": gradle_entry.get("name") or manifest.get("name") or module,
            "url": f"{args.base_url.rstrip('/')}/{cs3.name}" if args.base_url else cs3.name,
            "innerUrl": cs3.name,
            "internalName": module,
            "version": int(manifest.get("version") or gradle_entry.get("version") or 1),
            "status": int(gradle_entry.get("status", STATUS_OK)),
            "description": gradle_entry.get("description") or manifest.get("description"),
            "authors": gradle_entry.get("authors") or ["unknown"],
            "language": gradle_entry.get("language") or manifest.get("language") or "en",
            "tvTypes": gradle_entry.get("tvTypes"),
            "iconUrl": gradle_entry.get("iconUrl") or manifest.get("iconUrl"),
            "repositoryUrl": gradle_entry.get("repositoryUrl"),
            "fileSize": size,
            "fileHash": digest,
            "fileHashType": "sha256",
            # FMHub extra fields — the admin panel shows these in the import preview.
            "pluginClassName": manifest.get("pluginClassName"),
            "requiresResources": bool(manifest.get("requiresResources", False)),
            "fmhubApiVersion": api_version,
            "cloudstreamVersion": cs_version,
        }
        entries.append(entry)
        print(f"  {cs3.name}: {size / 1024:.1f} KiB, sha256={digest[:12]}…, "
              f"pluginClassName={entry['pluginClassName']}")

    if args.dry_run:
        print(json.dumps(entries, indent=2)[:2000])
        return 0

    out_dir.mkdir(parents=True, exist_ok=True)
    for cs3 in packages:
        shutil.copy2(cs3, out_dir / cs3.name)

    (out_dir / "plugins.json").write_text(json.dumps(entries, indent=2) + "\n", encoding="utf-8")
    repo = {
        "name": args.name,
        "description": args.description,
        "manifestVersion": REPO_MANIFEST_VERSION,
        "pluginLists": [
            f"{args.base_url.rstrip('/')}/plugins.json" if args.base_url else "plugins.json"
        ],
    }
    (out_dir / "repo.json").write_text(json.dumps(repo, indent=2) + "\n", encoding="utf-8")

    print(f"\nwrote {out_dir}/repo.json + plugins.json + {len(entries)} package(s)")
    if not args.base_url:
        print("  (pass --base-url so the download URLs in plugins.json are absolute)")
    print(f"  plugin API v{api_version} / CloudStream {cs_version}")
    print("  Admin panel → Repos → Add repo → paste the public URL of repo.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
