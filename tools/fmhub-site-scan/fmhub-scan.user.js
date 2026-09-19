// ==UserScript==
// @name         FMHub Site Scanner
// @namespace    fmhub24.local
// @version      1.0.0
// @description  Read-only page inspector for building FMHub/CloudStream providers: dumps list/detail selectors, episode links, player iframes, media (.m3u8/.mp4) URLs and the AJAX calls behind them. Runs in every frame, stays entirely in your browser, uploads nothing.
// @author       FMHub24
// @match        *://*/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

/*
 * Userscript build - for Tampermonkey / Violentmonkey / Greasemonkey users who would rather not
 * load an unpacked extension. Same analyzer as the Chrome extension (analyzer.js is concatenated
 * into this file by tools/fmhub-site-scan/build.sh), but with two honest limits:
 *   - `@grant none` means it runs in the page context, so page globals ARE visible (good), but
 *     the extension's webRequest request log is not available (only the fetch/XHR hook is);
 *   - iframe scanning depends on the manager injecting into iframes at all (Tampermonkey does;
 *     some mobile browsers / strict managers do not).
 * Regenerate with:  bash tools/fmhub-site-scan/build.sh
 */
(function () {
  'use strict';
  var w = window;
  if (w.__fmhubUsReady) return;
  w.__fmhubUsReady = true;
  var IS_TOP = w.top === w;
/*
 * FMHub Site Scanner - shared analyzer.
 *
 * Deliberately framework free and side-effect free: it only reads the DOM, it never clicks,
 * submits, scrolls or posts anything. It is loaded twice on purpose -
 *   1. as a MAIN-world content script of the extension (so page globals like `player_id`
 *      or `AJAX` are visible, which an isolated content script cannot see), and
 *   2. inside the userscript build (tools/fmhub-site-scan/fmhub-scan.user.js).
 *
 * The output is the exact shape needed to write a config-driven CloudStream/FMHub provider:
 * which repeated container holds the result cards, which child is title/link/poster, how the
 * episode list is shaped, where the player iframe comes from and which URL finally carries
 * the .m3u8/.mp4.
 */
(function (global) {
  'use strict';
  if (global.__fmhubAnalyze) return;

  var MEDIA_RE = /\.(m3u8|mpd|mp4|mkv|webm|avi|f4m|chunk_list)(\?|#|$)/i;
  var API_RE = /(\/api\/|ajax|graphql|\.json|search|embed|source|player|stream|video|load)/i;
  var EP_RE = /(episode|ep[-_/ ]?\d|\/ep\b|=ep|watch|পর্ব|অধ্যায়|কিস্তি)/i;
  var PAGER_RE = /(page|p)=/i;
  var MAXN = 12;

  function clip(value, n) {
    if (value === null || value === undefined) return null;
    var s = String(value).replace(/\s+/g, ' ').trim();
    n = n || 160;
    return s.length > n ? s.slice(0, n) + '…' : s;
  }

  function qall(root, selector) {
    try { return Array.prototype.slice.call(root.querySelectorAll(selector)); }
    catch (e) { return []; }
  }

  function classesOf(el) {
    if (!el || !el.classList) return [];
    return Array.prototype.slice.call(el.classList).slice(0, 3);
  }

  // Short, readable CSS path: up to 4 ancestors, tag + id/class names only. Enough to write
  // a jsoup selector from, without the noise of nth-child chains.
  function cssPath(el, depth) {
    var parts = [];
    var cur = el;
    var limit = depth || 4;
    while (cur && cur.nodeType === 1 && parts.length < limit) {
      var name = cur.tagName.toLowerCase();
      if (cur.id) name += '#' + cur.id;
      var cls = classesOf(cur).map(function (c) { return '.' + c.replace(/[^A-Za-z0-9_-]/g, ''); });
      if (cls.length) name += cls.join('');
      parts.unshift(name);
      cur = cur.parentElement;
    }
    return parts.join(' > ');
  }

  function absUrl(win, href) {
    try { return new URL(href, win.location.href).href; } catch (e) { return null; }
  }

  function pageType(win, doc) {
    var u = String(win.location.pathname || '') + '?' + String(win.location.search || '');
    if (/\/(search|find|results?)\b|[?&](s|q|query|searchterm|keywords)=/i.test(u)) return 'search';
    if (/\/(series|anime|movie|film|tv|detail|title|dorama|show)\b/i.test(win.location.pathname)) return 'details';
    if (/\/(episode|watch|video|ep[-_]|\/ep\/)/i.test(win.location.pathname)) return 'episode';
    if (/\/(category|genre|list|page|movies?|animes?|tv-shows?)\b/i.test(win.location.pathname)) return 'listing';
    if (win.location.pathname.replace(/\/$/, '') === '') return 'home';
    return 'other';
  }

  function textLen(el) {
    return (el && el.textContent ? el.textContent : '').trim().length;
  }

  /* Group the anchors that look like result cards by their parent container. The biggest group
   * is almost always the search/list grid, so its selector is what a provider needs. */
  function itemGroups(doc, win) {
    var groups = new Map();
    qall(doc, 'a[href]').forEach(function (a) {
      var img = a.querySelector('img');
      var href = a.getAttribute('href') || '';
      if (!href || href.charAt(0) === '#') return;
      if (!img && !a.textContent.trim()) return;
      var parent = a.parentElement;
      if (!parent) return;
      var key = cssPath(parent, 3);
      var entry = groups.get(key);
      if (!entry) { entry = { container: key, items: [] }; groups.set(key, entry); }
      if (entry.items.length >= 6) return;
      var titleEl = a.querySelector('h1,h2,h3,h4,.title,.name,.movie-title,.post-title,b,strong') || a;
      var src = img ? (img.getAttribute('data-src') || img.getAttribute('data-original') ||
        img.getAttribute('data-lazy-src') || img.getAttribute('src')) : null;
      entry.items.push({
        href: clip(href, 140),
        abs: clip(absUrl(win, href), 200),
        title: clip(a.getAttribute('title') || titleEl.textContent, 70),
        poster: clip(src, 160),
        titleSelector: cssPath(titleEl, 2),
        posterSelector: img ? cssPath(img, 3) : null,
        linkSelector: a === titleEl ? 'a' : 'a[href]',
        year: clip((a.textContent.match(/\b(19|20)\d{2}\b/) || [null])[0], 10)
      });
    });
    return Array.from(groups.values())
      .filter(function (g) { return g.items.length >= 2; })
      .sort(function (x, y) { return y.items.length - x.items.length; })
      .slice(0, 3);
  }

  function episodeLinks(doc, win) {
    var byParent = new Map();
    qall(doc, 'a[href]').forEach(function (a) {
      var hay = (a.getAttribute('href') || '') + ' ' + clip(a.textContent, 60);
      if (!EP_RE.test(hay)) return;
      var parent = a.closest('ul,ol,div,section,table') || a.parentElement;
      var key = cssPath(parent, 3);
      var list = byParent.get(key);
      if (!list) { list = []; byParent.set(key, list); }
      if (list.length < 8) {
        list.push({ text: clip(a.textContent, 44), href: clip(a.getAttribute('href'), 140), ep: clip(a.getAttribute('data-episode') || a.getAttribute('data-id') || a.getAttribute('data-slug'), 60) });
      }
    });
    return Array.from(byParent.keys())
      .map(function (k) { return { listSelector: k, count: byParent.get(k).length, sample: byParent.get(k) }; })
      .sort(function (a, b) { return b.count - a.count; })
      .slice(0, 3);
  }

  function playerInfo(doc, win) {
    var out = { frames: [], videos: [], buttons: [], sources: [], srcdoc: [], scripts: [] };
    qall(doc, 'iframe,frame').forEach(function (f) {
      if (out.frames.length >= MAXN) return;
      out.frames.push({
        src: clip(f.src || f.getAttribute('data-src') || f.getAttribute('data-embed') || f.getAttribute('data-url'), 320),
        id: f.id || null, name: f.name || null, parent: cssPath(f.parentElement, 3),
        w: f.getAttribute('width'), h: f.getAttribute('height')
      });
    });
    qall(doc, 'video').forEach(function (v) {
      if (out.videos.length >= 6) return;
      out.videos.push({
        src: clip(v.currentSrc || v.src, 320), poster: clip(v.poster, 160),
        parent: cssPath(v.parentElement, 3),
        children: qall(v, 'source').map(function (s) { return clip(s.src || s.getAttribute('src'), 260); }).slice(0, 6)
      });
    });
    qall(doc, 'source').forEach(function (s) {
      if (out.sources.length >= 8) return;
      out.sources.push({ src: clip(s.src || s.getAttribute('src'), 300), type: s.getAttribute('type') });
    });
    // Every attribute that smells like "here is the embed" - these are the ones a provider
    // usually has to click through before the real iframe appears.
    qall(doc, '[data-embed],[data-video],[data-src],[data-file],[data-jw],[data-iframe],[data-iframe-src],[data-hash],[data-id],[data-link],[data-url]').forEach(function (el) {
      if (out.buttons.length >= MAXN) return;
      out.buttons.push({
        tag: el.tagName, text: clip(el.textContent, 40), id: el.id || null,
        attrs: Array.prototype.slice.call(el.attributes)
          .filter(function (a) { return /^(data-|id$|value$|href$)/.test(a.name); })
          .slice(0, 8).map(function (a) { return a.name + '=' + clip(a.value, 180); }),
        parent: cssPath(el.parentElement, 3)
      });
    });
    qall(doc, 'iframe').forEach(function (f) {
      if (out.srcdoc.length >= 2) return;
      if (f.srcdoc) out.srcdoc.push(clip(f.srcdoc, 600));
    });
    qall(doc, 'script:not([src])').forEach(function (s) {
      if (out.scripts.length >= 6) return;
      var t = s.textContent || '';
      if (/embed|iframe|player|sources?|m3u8|mp4|video_url|jwplayer|jw\/setup/i.test(t) && t.length < 6000) {
        out.scripts.push({ snippet: clip(t, 700), parent: cssPath(s.parentElement, 2) });
      }
    });
    return out;
  }

  function pageGlobals(win) {
    var found = [];
    var names = [];
    try { names = Object.keys(win); } catch (e) { return found; }
    for (var i = 0; i < names.length && found.length < 14; i++) {
      var k = names[i];
      if (!/^(player|video|embed|ajax|watch|source|stream|jw|hash|anime|episode|site|template|data|config|settings|lang)/i.test(k)) continue;
      var v;
      try { v = win[k]; } catch (e) { continue; }
      if (v === null || v === undefined || typeof v === 'function' || v === win) continue;
      var serialised;
      try { serialised = JSON.stringify(v); } catch (e) { serialised = String(v); }
      if (!serialised || serialised === '{}' || serialised === '[]') continue;
      found.push({ name: k, type: Object.prototype.toString.call(v), value: clip(serialised, 400) });
    }
    return found;
  }

  function mediaFromPerformance(win) {
    var media = [];
    var api = [];
    try {
      win.performance.getEntriesByType('resource').forEach(function (r) {
        var u = r.name || '';
        if (MEDIA_RE.test(u)) {
          if (media.length < 20) media.push({ url: clip(u, 300), kind: r.initiatorType || null, size: r.transferSize || null });
        } else if (API_RE.test(u) && !/\.(png|jpe?g|webp|gif|svg|css|woff2?|ico|js)(\?|$)/i.test(u)) {
          if (api.length < 20) api.push(clip(u, 260));
        }
      });
    } catch (e) { /* ignore */ }
    return { media: media, apiRequests: api };
  }

  function metaInfo(doc) {
    var out = {};
    qall(doc, 'meta').forEach(function (m) {
      var key = m.getAttribute('property') || m.getAttribute('name');
      if (!key || out[key] !== undefined) return;
      if (/^(og:|twitter:|description|keywords|generator|title|type|image|url)$/i.test(key) || /og:/i.test(key)) {
        out[clip(key, 24)] = clip(m.getAttribute('content'), 200);
      }
    });
    return out;
  }

  function describeEl(doc) {
    var best = null;
    qall(doc, 'p,div,section,article,span').forEach(function (el) {
      var len = textLen(el);
      if (len < 140 || len > 2200) return;
      if (el.children.length > 6) return;
      if (!best || len < textLen(best)) best = el;
    });
    if (!best) return null;
    return { selector: cssPath(best, 3), text: clip(best.textContent, 300) };
  }

  function analyze(doc, win) {
    var out = {
      app: 'FMHub Site Scanner',
      version: '1.0.0',
      at: new Date().toISOString(),
      url: win.location.href,
      origin: win.location.origin,
      path: win.location.pathname,
      pageType: pageType(win, doc),
      title: clip(doc.title, 120),
      isTopFrame: true,
      generator: null,
      meta: metaInfo(doc),
      forms: [],
      itemGroups: itemGroups(doc, win),
      episodes: episodeLinks(doc, win),
      pagination: [],
      detail: null,
      player: null,
      globals: pageGlobals(win),
      perf: mediaFromPerformance(win),
      storage: { localKeys: [], sessionKeys: [], cookieNames: [] },
      nav: [],
      hints: []
    };
    out.generator = clip((doc.querySelector('meta[name=generator]') || {}).content, 80);

    qall(doc, 'form').slice(0, 4).forEach(function (f) {
      out.forms.push({
        action: clip(f.getAttribute('action') || f.action, 160), method: (f.method || 'GET').toUpperCase(),
        inputs: Array.prototype.slice.call(f.elements).map(function (i) { return i.name || i.type; })
          .filter(Boolean).slice(0, 8)
      });
    });

    qall(doc, 'a[href]').forEach(function (a) {
      if (out.pagination.length >= 5) return;
      var href = a.getAttribute('href') || '';
      if (PAGER_RE.test(href)) out.pagination.push(clip(href, 140));
    });

    out.detail = {
      heading: qall(doc, 'h1').map(function (h) { return clip(h.textContent, 90); }).slice(0, 2),
      headingSelector: doc.querySelector('h1') ? cssPath(doc.querySelector('h1'), 2) : null,
      poster: (function () {
        var img = doc.querySelector('img[class*=poster],img[id*=poster],img[class*=thumb],meta[property="og:image"]');
        if (img && img.tagName === 'META') return clip(img.getAttribute('content'), 200);
        if (img) return clip(img.src, 200);
        return null;
      })(),
      description: describeEl(doc),
      meta: out.meta,
      genres: qall(doc, 'a[href*="genre" i],a[href*="category" i]')
        .slice(0, 10).map(function (a) { return clip(a.textContent, 30) + ' -> ' + clip(a.getAttribute('href'), 90); })
    };

    out.player = playerInfo(doc, win);

    try {
      for (var i = 0; i < Math.min(doc.defaultView.localStorage.length, 20); i++) {
        out.storage.localKeys.push(doc.defaultView.localStorage.key(i));
      }
      for (var j = 0; j < Math.min(doc.defaultView.sessionStorage.length, 20); j++) {
        out.storage.sessionKeys.push(doc.defaultView.sessionStorage.key(j));
      }
    } catch (e) { out.storage = 'blocked'; }
    out.storage.cookieNames = String(doc.cookie || '').split(';')
      .map(function (c) { return c.split('=')[0].trim(); }).filter(Boolean).slice(0, 12);

    qall(doc, 'nav a[href],header a[href]').slice(0, 14).forEach(function (a) {
      out.nav.push(clip(a.textContent, 26) + ' -> ' + clip(a.getAttribute('href'), 70));
    });

    var hookLog = win.__fmhubNetLog || [];
    out.hookedRequests = hookLog.slice(-25);
    out.hints.push(hookLog.length === 0
      ? 'No fetch/XHR captured yet: click the server button in the player, let the video start, then scan again.'
      : hookLog.length + ' AJAX call(s) captured since page load.');
    var iframeOnly = out.player.frames.length > 0 && !out.player.videos.length && !out.perf.media.length;
    if (iframeOnly) out.hints.push('Player is an iframe - the video URL lives inside it. This scanner runs in every frame, so its scan is included below if the iframe is same-site; for a cross-origin host the browser blocks reading it, so send me the Network tab lines for that iframe too.');
    return out;
  }

  /* A few harmless GETs to discover which search endpoint the site really answers, so nobody has
   * to guess between /search?q=, /?s= and /page/search. */
  function probeSearch(win, doc) {
    var term = 'naruto';
    var paths = ['/search?q=', '/?s=', '/search/', '/index.php?search=', '/anime/search?q='];
    if (typeof win.fetch !== 'function') return Promise.resolve([{ skipped: 'no fetch() on this page' }]);
    return paths.map(function (p) { return p + term; }).map(function (path) {
      var url = absUrl(win, path);
      if (!url) return Promise.resolve({ url: path, status: 'bad-url' });
      var started;
      try { started = win.fetch(url, { credentials: 'include', redirect: 'follow' }); }
      catch (e) { return Promise.resolve({ url: clip(url, 200), status: 'blocked', error: clip(e, 80) }); }
      return Promise.resolve(started).then(function (r) {
        return r.text().then(function (t) {
          var cards = 0;
          try {
            var d = new DOMParser().parseFromString(t, 'text/html');
            cards = qall(d, 'a[href] img').length;
          } catch (e) { /* ignore */ }
          return { url: clip(url, 200), status: r.status, finalUrl: clip(r.url, 200), bytes: t.length, imageLinksFound: cards };
        });
      }, function (err) { return { url: clip(url, 200), status: 'error', error: clip(err, 80) }; });
    }).reduce(function (chain, p) {
      return chain.then(function (acc) {
        return p.then(function (res) { acc.push(res); return acc; });
      });
    }, Promise.resolve([]));
  }

  /* Network sniffing in page context: catches what fetch/XHR asked *and* what it got back, which
   * the extension's webRequest layer cannot see for response bodies. */
  function hook(win) {
    if (win.__fmhubHooked) return;
    win.__fmhubHooked = true;
    win.__fmhubNetLog = [];
    function push(entry) {
      win.__fmhubNetLog.push(entry);
      if (win.__fmhubNetLog.length > 80) win.__fmhubNetLog.shift();
    }
    var origFetch = win.fetch;
    if (origFetch) {
      win.fetch = function () {
        var a0 = arguments[0], a1 = arguments[1];
        var url = (a0 && a0.url) || String(a0);
        var entry = { via: 'fetch', method: ((a0 && a0.method) || (a1 && a1.method) || 'GET'), url: clip(url, 260), body: clip(a0 && a0.body || a1 && a1.body, 200) };
        return origFetch.apply(this, arguments).then(function (res) {
          entry.status = res.status;
          entry.contentType = res.headers && res.headers.get('content-type');
          try {
            res.clone().text().then(function (t) { entry.response = clip(t, 500); push(entry); }, function () { push(entry); });
          } catch (e) { push(entry); }
          return res;
        }, function (err) {
          entry.error = clip(err, 100); push(entry); throw err;
        });
      };
    }
    var O = win.XMLHttpRequest && win.XMLHttpRequest.prototype.open;
    var S = win.XMLHttpRequest && win.XMLHttpRequest.prototype.send;
    if (O && S) {
      win.XMLHttpRequest.prototype.open = function (method, url) {
        this.__fmhub = { via: 'xhr', method: method, url: clip(url, 260) };
        return O.apply(this, arguments);
      };
      win.XMLHttpRequest.prototype.send = function (body) {
        var xhr = this;
        if (xhr.__fmhub) {
          xhr.__fmhub.body = clip(body, 200);
          xhr.addEventListener('loadend', function () {
            xhr.__fmhub.status = xhr.status;
            try { xhr.__fmhub.response = clip(xhr.responseText, 500); } catch (e) { }
            push(xhr.__fmhub);
          });
        }
        return S.apply(this, arguments);
      };
    }
  }

  function scan(win, doc) {
    hook(win);
    var probes;
    try { probes = probeSearch(win, doc); } catch (e) { probes = Promise.resolve([{ error: String(e && e.message || e) }]); }
    return Promise.resolve(probes).then(function (probes2) {
      var out = analyze(doc, win);
      out.searchProbe = probes2;
      return out;
    }, function () {
      var out = analyze(doc, win);
      out.searchProbe = 'failed';
      return out;
    });
  }

  global.__fmhubAnalyze = analyze;
  global.__fmhubScan = scan;
  global.__fmhubHook = hook;
})(typeof window !== 'undefined' ? window : globalThis);
  /* ---- userscript half: frame protocol, panel, storage ------------------------------------- */
  var KEY = 'fmhubScans:v1';
  var HIDE = 'fmhubHiddenHosts:v1';
  var NET = [];

  try { w.__fmhubHook(w); } catch (e) { /* ignore */ }

  function store(k, v) { try { localStorage.setItem(k, v); } catch (e) { } }
  function read(k, dflt) {
    try { var v = localStorage.getItem(k); return v ? JSON.parse(v) : dflt; } catch (e) { return dflt; }
  }

  function scanSelf() {
    return w.__fmhubScan(w, document).then(function (r) {
      return { frameUrl: w.location.href, isTop: IS_TOP, result: r };
    }, function (e) {
      return { frameUrl: w.location.href, isTop: IS_TOP, error: String((e && e.message) || e) };
    });
  }

  // Children answer by posting to their parent, so nested player iframes reach the top frame.
  w.addEventListener('message', function (e) {
    var d = e.data;
    if (!d || d.__fmhubUs !== 'scanall' || !d.id) return;
    scanSelf().then(function (frame) {
      var target = IS_TOP ? w : w.parent;
      try { target.postMessage({ __fmhubUs: 'res', id: d.id, frame: frame }, '*'); } catch (err) { }
    });
  });

  function scanAll() {
    var id = 'us' + Date.now();
    var got = [];
    function onRes(e) {
      var d = e.data;
      if (d && d.__fmhubUs === 'res' && d.id === id) got.push(d.frame);
    }
    w.addEventListener('message', onRes);
    try {
      Array.prototype.slice.call(document.querySelectorAll('iframe,frame')).forEach(function (f) {
        try { f.contentWindow.postMessage({ __fmhubUs: 'scanall', id: id }, '*'); } catch (e) { }
      });
    } catch (e) { }
    var own = scanSelf();
    return new Promise(function (resolve) {
      setTimeout(function () {
        w.removeEventListener('message', onRes);
        own.then(function (topFrame) {
          var frames = [topFrame].concat(got);
          var rec = {
            at: new Date().toISOString(), url: w.location.href, origin: w.location.origin,
            title: document.title, pageType: (topFrame.result || {}).pageType || 'unknown',
            frames: frames, hookedRequests: (w.__fmhubNetLog || []).slice(-30),
            userAgent: navigator.userAgent, source: 'userscript'
          };
          var all = read(KEY, []);
          all.push(rec);
          store(KEY, JSON.stringify(all.slice(-40)));
          resolve(rec);
        });
      }, 2500);
    });
  }

  w.__fmhubScanAll = scanAll;
  w.__fmhubAll = function () { return read(KEY, []); };

  function style() {
    if (document.getElementById('fmhub-us-style')) return;
    var s = document.createElement('style');
    s.id = 'fmhub-us-style';
    s.textContent = `
.fmhub-us-btn{position:fixed;right:14px;bottom:14px;z-index:2147483646;background:#0f172a;color:#e2e8f0;border:1px solid #22d3ee;border-radius:999px;padding:8px 12px;font:12px system-ui,sans-serif;cursor:pointer}
.fmhub-us-panel{position:fixed;right:14px;bottom:60px;z-index:2147483647;width:min(520px,94vw);max-height:74vh;overflow:auto;background:#0b1220;color:#e2e8f0;border:1px solid #1f2b45;border-radius:12px;padding:10px 12px;font:12px/1.45 ui-monospace,Menlo,monospace}
.fmhub-us-panel button{background:#132038;color:#e2e8f0;border:1px solid #2a3c5f;border-radius:8px;padding:5px 9px;cursor:pointer;font:inherit;margin:0 4px 4px 0}
.fmhub-us-panel .row{padding:8px 0;border-top:1px solid #16203a;word-break:break-all}
.fmhub-us-panel pre{white-space:pre-wrap;word-break:break-all;max-height:200px;overflow:auto;background:#070d18;border-radius:8px;padding:6px;margin:6px 0 0}
`;
    (document.head || document.documentElement).appendChild(s);
  }

  function render(panel) {
    var all = read(KEY, []);
    panel.innerHTML = '';
    function btn(text, fn) {
      var b = document.createElement('button');
      b.textContent = text;
      b.addEventListener('click', fn);
      return b;
    }
    var status = document.createElement('div');
    function scan() {
      status.textContent = 'Scanning all frames…';
      scanAll().then(function (rec) {
        status.textContent = 'frames: ' + rec.frames.length + ', media: ' +
          rec.frames.reduce(function (a, f) { return a + ((f.result && f.result.perf && f.result.perf.media) || []).length; }, 0);
        render(panel);
      });
    }
    panel.appendChild(btn('Scan this page', scan));
    panel.appendChild(btn('Copy all', function () {
      var text = JSON.stringify(all, null, 1);
      var ta = document.createElement('textarea');
      ta.value = text; document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); status.textContent = 'Copied ' + all.length + ' scan(s)'; }
      catch (e) { status.textContent = 'Copy blocked - use Download'; }
      ta.remove();
    }));
    panel.appendChild(btn('Download .json', function () {
      var blob = new Blob([JSON.stringify(all, null, 1)], { type: 'application/json' });
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'fmhub-scan-' + w.location.hostname + '.json';
      document.body.appendChild(a); a.click();
      setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 400);
    }));
    panel.appendChild(btn('Clear', function () { store(KEY, '[]'); render(panel); }));
    panel.appendChild(btn('Hide this site', function () {
      var hosts = read(HIDE, []); hosts.push(w.location.hostname); store(HIDE, JSON.stringify(hosts));
      panel.remove();
      var x = document.querySelector('.fmhub-us-btn'); if (x) x.remove();
    }));
    status.appendChild(document.createTextNode(''));
    panel.appendChild(status);
    if (!all.length) {
      var none = document.createElement('div');
      none.className = 'row';
      none.textContent = 'No scans yet. Scan home, search results, a series page and an episode page (play the video first).';
      panel.appendChild(none);
    }
    all.slice().reverse().forEach(function (rec, i) {
      var n = document.createElement('div');
      n.className = 'row';
      var media = 0, groups = 0, eps = 0, iframes = 0;
      (rec.frames || []).forEach(function (f) {
        var r = f.result || {};
        groups += (r.itemGroups || []).length;
        eps += (r.episodes || []).reduce(function (a, e) { return a + (e.count || 0); }, 0);
        iframes += (r.player && r.player.frames ? r.player.frames.length : 0);
        media += (r.perf && r.perf.media ? r.perf.media.length : 0);
      });
      n.textContent = '#' + (all.length - i) + ' ' + (rec.pageType || '?') + ' ' + (rec.url || '') +
        '  → frames ' + (rec.frames || []).length + ', groups ' + groups + ', episodes ' + eps +
        ', player iframes ' + iframes + ', media ' + media;
      var pre = document.createElement('pre');
      pre.style.display = 'none';
      pre.textContent = JSON.stringify(rec, null, 1);
      var show = btn('JSON', function () { pre.style.display = pre.style.display === 'none' ? 'block' : 'none'; });
      n.appendChild(show);
      n.appendChild(pre);
      panel.appendChild(n);
    });
  }

  function boot() {
    if (!document.body) { setTimeout(boot, 400); return; }
    if (!IS_TOP) return;
    if (!/^https?:$/.test(w.location.protocol)) return;
    var hosts = read(HIDE, []);
    if (hosts.indexOf(w.location.hostname) >= 0) return;
    style();
    var b = document.createElement('button');
    b.className = 'fmhub-us-btn';
    b.textContent = '🔎 FMHub scan';
    var panel = null;
    b.addEventListener('click', function () {
      if (panel && panel.parentNode) { render(panel); return; }
      panel = document.createElement('div');
      panel.className = 'fmhub-us-panel';
      document.body.appendChild(panel);
      render(panel);
    });
    document.addEventListener('keydown', function (e) {
      if (e.altKey && e.shiftKey && e.code === 'KeyS') {
        e.preventDefault();
        b.click();
        scanAll().then(function () { render(panel); });
      }
    });
    document.body.appendChild(b);
  }
  boot();
})();
