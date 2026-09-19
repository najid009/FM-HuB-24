#!/usr/bin/env bash
# Regenerates the userscript build and validates everything a Chrome loader would choke on.
# Run from anywhere:  bash tools/fmhub-site-scan/build.sh
set -euo pipefail
cd "$(dirname "$0")"

echo "→ building fmhub-scan.user.js (header + analyzer + UI)"
cat userscript.head.js analyzer.js userscript.tail.js > fmhub-scan.user.js

echo "→ syntax check"
for f in analyzer.js main.js ui.js background.js popup.js fmhub-scan.user.js; do
  node --check "$f"
  echo "   ok: $f"
done

echo "→ building fmhub-scan-bookmarklet.txt (zero-install fallback)"
python3 make_bookmarklet.py
sed 's/^javascript://' fmhub-scan-bookmarklet.txt > /tmp/fmhub-bm.js
node --check /tmp/fmhub-bm.js && echo "   ok: bookmarklet parses"

if node -e "require.resolve('jsdom')" >/dev/null 2>&1 || [ -d /tmp/domtest/node_modules/jsdom ]; then
  echo "→ analyzer tests (jsdom)"
  export NODE_PATH="${NODE_PATH:-/tmp/domtest/node_modules}"
  node test/test-analyzer.js | tail -3
  node test/test-ui.js | tail -3
else
  echo "→ analyzer tests skipped (npm i jsdom to enable: NODE_PATH=/path/to/node_modules bash build.sh)"
fi

echo "→ manifest check"
python3 - <<'PY'
import json, os
m = json.load(open('manifest.json'))
assert m['manifest_version'] == 3, 'manifest_version must be 3'
for cs in m['content_scripts']:
    for js in cs['js']:
        open(js).read()
    assert cs['matches'], 'empty matches'
assert 'icons' not in m or all(os.path.getsize(p) > 0 for p in m['icons'].values()), 'missing icon file'
print('   ok: manifest.json (%d permissions, %d content scripts)' % (len(m['permissions']), len(m['content_scripts'])))
PY

if [ "${1:-}" = "--zip" ]; then
  echo "→ packaging"
  out=../../fmhub-site-scan-chrome.zip
  rm -f "$out"
  zip -qr "$out" manifest.json analyzer.js main.js ui.js background.js popup.html popup.js \
      fmhub-scan.user.js fmhub-scan-bookmarklet.txt icons README.md
  echo "   wrote $out ($(du -h "$out" | cut -f1))"
fi
echo "done."
