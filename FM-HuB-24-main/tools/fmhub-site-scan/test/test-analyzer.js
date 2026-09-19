/* Smoke test for tools/fmhub-site-scan/analyzer.js using jsdom (run from /tmp/domtest). */
'use strict';
const fs = require('fs');
const { JSDOM } = require('jsdom');
const analyzer = fs.readFileSync(require('path').join(__dirname, '..', 'analyzer.js'), 'utf8');

const HOST = 'https://watchanimeworld.one';

function grid(n, cls) {
  let out = '';
  for (let i = 1; i <= n; i++) {
    out += `<div class="${cls}"><a href="/series/show-${i}/" title="Show ${i}">
      <img src="https://cdn.example/poster${i}.jpg" class="poster">
      <div class="title">Show ${i} <span>20${String(i).padStart(2, '0')}</span></div>
    </a></div>`;
  }
  return out;
}

const pages = {
  search: {
    url: HOST + '/?s=naruto',
    html: `<html><head><meta name="generator" content="WordPress 6.6"><title>You searched for naruto</title></head>
    <body>
      <div class="search-results"><div class="row">${grid(8, 'thumbray')}</div></div>
      <aside class="sidebar">${grid(3, 'widget')}</aside>
      <nav><a href="/series/">Series</a><a href="/movies/">Movies</a></nav>
      <form role="search" action="${HOST}/" method="get"><input name="s"></form>
    </body></html>`
  },
  details: {
    url: HOST + '/series/naruto-shippuden/',
    html: `<html><head><title>Naruto Shippuden</title></head><body>
      <div class="series-summary"><img class="poster" src="https://cdn.example/big.jpg">
      <h1>Naruto Shippuden</h1>
      <p class="desc">${'Follows Naruto as he returns to the village and hunts down his former friend. '.repeat(6)}</p>
      <a href="/genre/action/">Action</a><a href="/category/anime/">Anime</a></div>
      <ul class="list-episode">${Array.from({ length: 24 }, (_, i) =>
        `<li><a href="/episode/naruto-shippuden-1x${i + 1}/" data-episode="${i + 1}">Episode ${i + 1}</a></li>`).join('')}</ul>
      <a href="?page=2">next</a>
    </body></html>`
  },
  episode: {
    url: HOST + '/episode/naruto-shippuden-1x1/',
    html: `<html><head><title>Watch Naruto Shippuden Episode 1</title></head><body>
      <div id="player-wrapper">
        <div id="player-frame"><iframe id="embed-player" src="https://vidstreaming.io/embed/?js=1&amp;id=MTIz" width="100%" height="500"></iframe></div>
        <div class="servers">
          <button class="server" data-embed="https://vidcloud1.com/streaming.php?id=MTIz" data-id="1">Vidcloud</button>
          <button class="server" data-embed="https://rapidgo.top/e/abc" data-id="2">Rapidgo</button>
        </div>
      </div>
      <script>var player_id = {id: "MTIz", title: "Naruto 1", file: "https://cdn.example/master.m3u8"};</script>
      <ul class="list-episode">${Array.from({ length: 24 }, (_, i) =>
        `<li><a href="/episode/naruto-shippuden-1x${i + 1}/">Episode ${i + 1}</a></li>`).join('')}</ul>
    </body></html>`
  }
};

let failures = 0;
function check(label, cond, extra) {
  console.log(`${cond ? 'ok  ' : 'FAIL'}  ${label}${cond ? '' : '  <<< ' + JSON.stringify(extra)}`);
  if (!cond) failures++;
}

const results = {};
for (const [name, page] of Object.entries(pages)) {
  const dom = new JSDOM(page.html, { url: page.url, runScripts: 'dangerously', pretendToBeVisual: true });
  dom.window.eval(analyzer);
  const out = dom.window.__fmhubAnalyze(dom.window.document, dom.window);
  results[name] = out;
}

const s = results.search;
check('search: pageType detected', s.pageType === 'search', s.pageType);
check('search: biggest card group is the grid, not the sidebar',
  s.itemGroups[0].container.includes('row') && s.itemGroups[0].items.length === 6,
  s.itemGroups.map(g => [g.container, g.items.length]));
check('search: item carries href + title + poster',
  !!s.itemGroups[0].items[0].href && /Show 1/.test(s.itemGroups[0].items[0].title) &&
  /poster1\.jpg/.test(s.itemGroups[0].items[0].poster), s.itemGroups[0].items[0]);
check('search: title/poster selectors look usable',
  /\.title/.test(s.itemGroups[0].items[0].titleSelector) && /\.poster/.test(s.itemGroups[0].items[0].posterSelector || ''),
  s.itemGroups[0].items[0]);
check('search: generator + search form captured',
  /WordPress/.test(s.generator || '') && s.forms.some(f => (f.inputs || []).includes('s')), [s.generator, s.forms]);
check('search: nav links listed', s.nav.length >= 2, s.nav);

const d = results.details;
check('details: pageType detected', d.pageType === 'details', d.pageType);
check('details: h1 + poster + description',
  /Naruto Shippuden/.test((d.detail.heading[0] || '')) && /big\.jpg/.test(d.detail.poster || '') &&
  /Follows Naruto/.test((d.detail.description || {}).text || ''), d.detail);
check('details: episode list found with 8 samples',
  d.episodes.length >= 1 && d.episodes[0].count === 8 && d.episodes[0].listSelector.includes('list-episode'),
  d.episodes);
check('details: pagination href seen', d.pagination.length >= 1, d.pagination);
check('details: genre links seen', d.detail.genres.length >= 2, d.detail.genres);

const e = results.episode;
check('episode: pageType detected', e.pageType === 'episode', e.pageType);
check('episode: iframe src found',
  /vidstreaming\.io\/embed/.test((e.player.frames[0] || {}).src || ''), e.player.frames);
check('episode: server buttons with data-embed found',
  e.player.buttons.some(b => b.attrs.some(a => a.startsWith('data-embed=') && /vidcloud1/.test(a))), e.player.buttons);
check('episode: player_id global captured',
  e.globals.some(g => g.name === 'player_id' && /master\.m3u8/.test(g.value || '')), e.globals);
check('episode: inline player script captured',
  e.player.scripts.some(x => /player_id/.test(x.snippet)), e.player.scripts.map(x => x.snippet.slice(0, 40)));

// hook(): XHR log must record a call made after install
const dom = new JSDOM('<html><body><p id=x>hi</p></body></html>', { url: HOST + '/', runScripts: 'dangerously' });
dom.window.eval(analyzer);
dom.window.__fmhubHook(dom.window);
const xhr = new dom.window.XMLHttpRequest();
xhr.open('GET', HOST + '/wp-json/wp/v2/search?search=naruto');
xhr.addEventListener('loadend', () => setTimeout(() => {
  const log = dom.window.__fmhubNetLog || [];
  check('hook: XHR recorded after install', log.some(l => /wp-json/.test(l.url || '') && l.via === 'xhr'), log);
  const out = dom.window.__fmhubAnalyze(dom.window.document, dom.window);
  check('analyze: hookedRequests surfaced in report', Array.isArray(out.hookedRequests), out.hookedRequests);
  console.log(failures ? `\n${failures} FAILURE(S)` : '\nall good');
  process.exit(failures ? 1 : 0);
}, 30));
try { xhr.send(); } catch (err) {
  console.log('SKIP  xhr unavailable in this jsdom build:', String(err).slice(0, 80));
  console.log(failures ? `\n${failures} FAILURE(S)` : '\nall good (minus skipped)');
  process.exit(failures ? 1 : 0);
}
