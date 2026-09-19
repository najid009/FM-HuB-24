# FMHub Site Scanner (Chrome extension + userscript)

Read-only site inspector for building FMHub/CloudStream providers. Instead of guessing selectors by
hand, it dumps everything a provider needs from a page: the repeated card container, the
title/link/poster selectors, episode list shape, player iframes and their `data-*` attributes, page
config globals, `performance` media URLs (`.m3u8`/`.mp4`) and the AJAX calls that produced them.

It scans **every frame**, including the player iframe — that is the whole point, since the video
URL is never in the outer page.

**Nothing is uploaded.** No remote endpoint exists in this code; the report is only copied or
downloaded to your own disk, and everything is stored in `chrome.storage.local` / `localStorage`.

## Install — Chrome extension (recommended)

1. Unzip `fmhub-site-scan-chrome.zip` into a folder (keep the folder, Chrome loads from it).
2. Open `chrome://extensions` (Edge: `edge://extensions`).
3. Turn on **Developer mode** (top right).
4. **Load unpacked** → pick the unzipped folder.
5. Open the site. A small **🔎 FMHub scan** button appears bottom-right; the toolbar icon shows how
   many scans are stored.

Chrome cannot install a `.zip` directly — that is why the step is *Load unpacked*, not *drag the zip*.
Needs Chrome/Edge 111+ (the `world: MAIN` content script, so page globals stay visible).

## Install — bookmarklet (no install at all)

`fmhub-scan-bookmarklet.txt` is one line. Bookmark anything → edit it → replace the URL with that
whole line → save → click it on the site. It scans only the frame you are in (no iframe fan-out, no
request log), so prefer the extension when you can. Chrome also lets you just paste it into the
address bar once, but the console blocks `javascript:` bookmarks unless you type them yourself.

## Install — userscript (if you already use Tampermonkey)

`fmhub-scan.user.js` in this folder **has the `// ==UserScript==` metadata block**, so Tampermonkey
will save it (that was the "no metadata block" error). Tampermonkey → Create a new script → paste
the whole file → Ctrl+S. Limitation vs. the extension: no `webRequest` request log (only the
fetch/XHR hook), and iframe scanning depends on the manager injecting into iframes.

## How to use it (5 minutes, 4 pages)

Do this on the site, once per page type:

| # | Page | Why |
| --- | --- | --- |
| 1 | Home | menu/genre links, pagination style |
| 2 | A search results page (`…/search?q=naruto` or `…/?s=naruto`) | the card grid → `search` selectors |
| 3 | One series/details page | title, poster, description, episode list |
| 4 | One episode/watch page — **click the server button and let the video start** | player iframe + real media URL |

On each page: click **🔎 FMHub scan** (or press <kbd>Alt</kbd>+<kbd>Shift</kbd>+<kbd>S</kbd>), wait
for the counts, then at the end press **Download .json** and attach that single file. Scans accumulate in one list (older sites stay visible too, which is fine - the
download contains them all), and they survive reloads and tab switches.

Useful extras: **Copy all** (paste into chat), **Clear**, **Hide this site** (removes the button for
that host — the toolbar popup has **Un-hide sites**), <kbd>Alt</kbd>+<kbd>Shift</kbd>+<kbd>H</kbd>.

## Reading the report

| Field | What it tells you |
| --- | --- |
| `pageType` | `search` / `details` / `episode` / `listing` / `home` |
| `itemGroups[].container` | the CSS selector of the result-card grid, biggest group first |
| `itemGroups[].items[]` | sample `href`, `title`, `poster` + the child selectors to use |
| `episodes[]` | episode link lists grouped by their `<ul>`/`<div>` selector, plus any `data-episode`/`data-id` |
| `player.frames[]` | every iframe `src` — usually the embed host, the thing a provider must call |
| `player.buttons[]` | `data-embed`/`data-video`/`data-src` attributes: how the site swaps servers |
| `globals` | `player_id`, `AJAX`, jw-config style objects that carry the real video URL |
| `perf.media` | `.m3u8`/`.mp4` actually requested — the strongest proof of what plays |
| `hookedRequests` | fetch/XHR with method, POST body and a 500-char response preview |
| `searchProbe` | which of `/search?q=`, `/?s=`, … answers with a result page (status + found image links) |
| `hints` | what is still missing and why |

## Troubleshooting

- **No button on a site** — you hid it (toolbar popup → Un-hide sites), or the page is not `http(s)`
  (Chrome Web Store, `chrome://` etc. are untouchable by design).
- **“page-world timeout”** in a frame result — that frame is sandboxed/`about:blank`; the top frame
  result is still valid. For cross-origin embed hosts the browser blocks reading the inner document:
  copy the iframe URL and its Network-tab lines instead.
- **Video already loaded before installing** — the page-side hook only records calls made *after* the
  script loaded, so reload the episode page and press play again.
- **`manifest.json` errors on load** — run `bash tools/fmhub-site-scan/build.sh` to re-validate.

## Layout

```
manifest.json      MV3 manifest (MAIN-world analyzer + isolated UI + service worker)
analyzer.js        the DOM/network analyzer (shared: extension and userscript both use it)
main.js            MAIN-world glue: console helpers + postMessage protocol
ui.js              isolated content script: per-frame scan responder + floating panel
background.js      webRequest log, frame fan-out, storage, badge
popup.html/js      toolbar popup (scan, download, clear, un-hide)
fmhub-scan.user.js          generated userscript build — do not edit, edit analyzer.js + userscript.*.js
fmhub-scan-bookmarklet.txt  generated one-liner, for the no-install path
make_bookmarklet.py         the generator (strips comments, wraps analyzer.js)
build.sh                    rebuilds both generated files, syntax-checks, runs the jsdom tests, --zip to package
test/test-analyzer.js       18 checks of the analyzer against fake WordPress/episode pages
test/test-ui.js             MAIN<->ISOLATED postMessage protocol + full userscript boot, in jsdom
                              run with:  NODE_PATH=/path/to/node_modules bash build.sh   (needs `npm i jsdom`)
```
