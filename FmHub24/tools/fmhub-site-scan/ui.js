/*
 * Isolated-world half: (1) answers per-frame scan requests coming from the background worker, so
 * every iframe - including the player iframe - gets inspected, and (2) renders the panel in the top
 * frame only. Nothing here reads page globals; that is main.js's job.
 */
(function () {
  'use strict';
  if (window.__fmhubUiReady) return;
  window.__fmhubUiReady = true;

  var IS_TOP = window.top === window;
  var pending = new Map();
  var seq = 0;

  function toMain(cmd, timeoutMs) {
    return new Promise(function (resolve, reject) {
      var id = 'f' + (++seq);
      var timer = setTimeout(function () {
        pending.delete(id);
        reject(new Error('page-world timeout'));
      }, timeoutMs || 5000);
      pending.set(id, function (msg) {
        clearTimeout(timer);
        pending.delete(id);
        if (msg.ok) resolve(msg.result); else reject(new Error(msg.error || 'page-world error'));
      });
      try { window.postMessage({ __fmhub: 'req', cmd: cmd, id: id }, '*'); }
      catch (e) { clearTimeout(timer); pending.delete(id); reject(e); }
    });
  }

  window.addEventListener('message', function (e) {
    var d = e.data;
    if (!d || d.__fmhub !== 'res' || !d.id) return;
    var fn = pending.get(d.id);
    if (fn) fn(d);
  });

  function scanThisFrame() {
    return toMain('scan', 8000)
      .then(function (result) { return { frameUrl: location.href, isTop: IS_TOP, result: result }; })
      .catch(function (err) { return { frameUrl: location.href, isTop: IS_TOP, error: String(err.message || err) }; });
  }

  if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.onMessage) {
    chrome.runtime.onMessage.addListener(function (msg, sender, respond) {
      if (msg && msg.type === 'frameScan') { scanThisFrame().then(respond); return true; }
      if (msg && msg.type === 'framePing') {
        toMain('ping', 2500).then(function (r) { respond({ ok: true, frameUrl: location.href, result: r }); },
          function (e) { respond({ ok: false, frameUrl: location.href, error: String(e.message || e) }); });
        return true;
      }
    });
  }

  if (!IS_TOP) return;

  var STYLE_ID = 'fmhub-style';
  var PANEL_ID = 'fmhub-panel';
  var BTN_ID = 'fmhub-btn';

  function css() {
    if (document.getElementById(STYLE_ID)) return;
    var s = document.createElement('style');
    s.id = STYLE_ID;
    s.textContent = `
#fmhub-btn{position:fixed;right:14px;bottom:14px;z-index:2147483646;background:#0f172a;color:#e2e8f0;border:1px solid #22d3ee;border-radius:999px;padding:8px 12px;font:12px/1.2 system-ui,sans-serif;cursor:pointer;box-shadow:0 6px 20px rgba(0,0,0,.45)}
#fmhub-btn:hover{background:#132038}
#fmhub-panel{position:fixed;right:14px;bottom:60px;z-index:2147483647;width:min(560px,94vw);max-height:76vh;overflow:auto;background:#0b1220;color:#e2e8f0;border:1px solid #1f2b45;border-radius:12px;font:12px/1.45 ui-monospace,SFMono-Regular,Menlo,monospace;box-shadow:0 16px 40px rgba(0,0,0,.6)}
#fmhub-panel *{box-sizing:border-box;font-family:inherit}
#fmhub-panel header{display:flex;gap:6px;align-items:center;flex-wrap:wrap;padding:10px 12px;border-bottom:1px solid #1f2b45;position:sticky;top:0;background:#0b1220}
#fmhub-panel h4{margin:0;font-size:12px;color:#22d3ee;letter-spacing:.3px;flex:1 1 100%}
#fmhub-panel .row{padding:10px 12px;border-bottom:1px solid #16203a}
#fmhub-panel .k{color:#94a3b8}
#fmhub-panel button{background:#132038;color:#e2e8f0;border:1px solid #2a3c5f;border-radius:8px;padding:5px 9px;cursor:pointer;font-size:11px}
#fmhub-panel button:hover{border-color:#22d3ee}
#fmhub-panel pre{white-space:pre-wrap;word-break:break-all;margin:6px 0 0;max-height:220px;overflow:auto;background:#070d18;border-radius:8px;padding:8px}
#fmhub-panel .tag{display:inline-block;background:#132038;border:1px solid #2a3c5f;border-radius:6px;padding:1px 6px;margin:2px 3px 0 0;font-size:11px}
#fmhub-panel .warn{color:#fbbf24;border-color:#7c5c10}
#fmhub-panel textarea{position:absolute;left:-9999px;width:1px;height:0;border:0;padding:0;opacity:0}
`;
    (document.head || document.documentElement).appendChild(s);
  }

  function el(tag, attrs, kids) {
    var n = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (k === 'text') n.textContent = attrs[k]; else if (k === 'html') n.innerHTML = attrs[k]; else n.setAttribute(k, attrs[k]);
    });
    (kids || []).forEach(function (c) { if (c) n.appendChild(c); });
    return n;
  }

  function fmt(n) { return n === null || n === undefined ? '-' : String(n); }

  function summary(rec) {
    var frames = rec.frames || [];
    var counts = { groups: 0, episodes: 0, frames_player: 0, media: 0, hooked: 0, err: 0 };
    frames.forEach(function (f) {
      var r = f.result;
      if (!r) { counts.err++; return; }
      counts.groups += (r.itemGroups || []).length;
      counts.episodes += (r.episodes || []).reduce(function (a, e) { return a + e.count; }, 0);
      counts.media += (r.perf && r.perf.media ? r.perf.media.length : 0);
      counts.hooked += (r.hookedRequests || []).length;
      if (r.player && r.player.frames && r.player.frames.length) counts.frames_player++;
    });
    return counts;
  }

  function store() {
    try {
      chrome.runtime.sendMessage({ type: 'load' }, function (r) {
        var records = (r && r.records) || [];
        render(records);
      });
    } catch (e) { render([]); }
  }

  function recordNode(rec, index) {
    var c = summary(rec);
    var head = el('div', { class: 'row' }, [
      el('b', { text: '#' + (index + 1) + '  ' + fmt(rec.pageType) + '  ' + fmt(rec.origin || rec.url) }),
      el('div', {}, [
        el('span', { class: 'tag', text: 'frames: ' + (rec.frames || []).length }),
        el('span', { class: 'tag', text: 'card-groups: ' + c.groups }),
        el('span', { class: 'tag', text: 'episode links: ' + c.episodes }),
        el('span', { class: 'tag', text: 'player iframes: ' + c.frames_player }),
        el('span', { class: 'tag', text: 'media urls: ' + c.media }),
        el('span', { class: 'tag', text: 'ajax captured: ' + c.hooked }),
        rec.net && rec.net.length ? el('span', { class: 'tag', text: 'requests logged: ' + rec.net.length }) : null,
        c.err ? el('span', { class: 'tag warn', text: 'frames not readable: ' + c.err }) : null
      ]),
      el('div', {}, [
        el('button', { text: 'Copy', 'data-action': 'copy', 'data-i': String(index) }),
        el('button', { text: 'Delete', 'data-action': 'del', 'data-i': String(index) }),
        el('button', { text: 'Show JSON', 'data-action': 'show', 'data-i': String(index) })
      ]),
      el('pre', { style: 'display:none', id: 'fmhub-pre-' + index, text: JSON.stringify(rec, null, 1) })
    ]);
    head.querySelectorAll('button').forEach(function (b) {
      b.addEventListener('click', function () {
        var i = Number(b.getAttribute('data-i'));
        var action = b.getAttribute('data-action');
        var node = document.getElementById('fmhub-pre-' + i);
        if (action === 'show') {
          node.style.display = node.style.display === 'none' ? 'block' : 'none';
        } else if (action === 'copy') {
          copyText(JSON.stringify(allRecords[i], null, 1), b);
        } else if (action === 'del') {
          allRecords.splice(i, 1);
          chrome.runtime.sendMessage({ type: 'save', records: allRecords }, store);
        }
      });
    });
    return head;
  }

  var allRecords = [];

  function copyText(text, btn) {
    function done() { if (btn) { var o = btn.textContent; btn.textContent = 'Copied ✓'; setTimeout(function () { btn.textContent = o; }, 1200); } }
    function fallback() {
      var ta = el('textarea', {});
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); done(); } catch (e) { alert('Copy failed - open "Show JSON" and select it manually.'); }
      ta.remove();
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, fallback);
    } else fallback();
  }

  function download(text) {
    var name = 'fmhub-scan-' + location.hostname + '-' + allRecords.length + '.json';
    var blob = new Blob([text], { type: 'application/json' });
    var a = el('a', { href: URL.createObjectURL(blob), download: name });
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 500);
  }

  function render(records) {
    allRecords = records || [];
    var panel = document.getElementById(PANEL_ID);
    if (!panel) return;
    panel.innerHTML = '';
    panel.appendChild(el('header', {}, [
      el('h4', { text: 'FMHub Site Scanner' }),
      el('button', { text: 'Scan this page', id: 'fmhub-scan' }),
      el('button', { text: 'Copy all', id: 'fmhub-copy' }),
      el('button', { text: 'Download .json', id: 'fmhub-dl' }),
      el('button', { text: 'Clear', id: 'fmhub-clear' }),
      el('button', { text: '×', id: 'fmhub-close', style: 'font-size:14px' })
    ]));
    panel.appendChild(el('div', { class: 'row' }, [
      el('div', { class: 'k', text: 'Run this on 4 page types, then send me Download .json: home, search results, one series, one episode (start the video first!). Alt+Shift+S scans, Alt+Shift+H hides this site.' })
    ]));
    if (!allRecords.length) {
      panel.appendChild(el('div', { class: 'row', text: 'No scans yet.' }));
    } else {
      allRecords.forEach(function (rec, i) { panel.appendChild(recordNode(rec, i)); });
    }
    var s = document.getElementById('fmhub-scan');
    if (s) s.addEventListener('click', function () { doScan(s); });
    var cp = document.getElementById('fmhub-copy');
    if (cp) cp.addEventListener('click', function () { copyText(JSON.stringify(allRecords, null, 1), cp); });
    var dl = document.getElementById('fmhub-dl');
    if (dl) dl.addEventListener('click', function () { download(JSON.stringify(allRecords, null, 1)); });
    var cl = document.getElementById('fmhub-clear');
    if (cl) cl.addEventListener('click', function () {
      chrome.runtime.sendMessage({ type: 'save', records: [] }, store);
    });
    var x = document.getElementById('fmhub-close');
    if (x) x.addEventListener('click', function () { panel.style.display = 'none'; });
  }

  function doScan(btn) {
    var old = btn.textContent;
    btn.textContent = 'Scanning…';
    chrome.runtime.sendMessage({ type: 'scanTab', net: true }, function (res) {
      btn.textContent = old;
      if (chrome.runtime.lastError) {
        // No background (e.g. loaded oddly) - still scan the top frame.
        scanThisFrame().then(function (f) {
          allRecords.push({ at: new Date().toISOString(), url: location.href, origin: location.origin, pageType: (f.result || {}).pageType, frames: [f] });
          render(allRecords);
        });
        return;
      }
      var rec = {
        at: new Date().toISOString(),
        url: location.href,
        origin: location.origin,
        title: document.title,
        pageType: ((res.frames && res.frames[0] && res.frames[0].result) || {}).pageType || 'unknown',
        frames: (res && res.frames) || [],
        net: (res && res.net) || [],
        userAgent: navigator.userAgent
      };
      allRecords.push(rec);
      chrome.runtime.sendMessage({ type: 'save', records: allRecords }, store);
    });
  }

  function toggle(force) {
    css();
    var panel = document.getElementById(PANEL_ID);
    if (!panel) {
      panel = el('div', { id: PANEL_ID });
      document.body.appendChild(panel);
      store();
    }
    var show = force === undefined ? panel.style.display === 'none' || !panel.style.display : force;
    panel.style.display = show ? 'block' : 'none';
    if (show) store();
  }

  function mountButton() {
    css();
    if (document.getElementById(BTN_ID)) return;
    var b = el('button', { id: BTN_ID, text: '🔎 FMHub scan' });
    b.addEventListener('click', function () { toggle(true); setTimeout(function () { doScan(b); }, 60); });
    document.body.appendChild(b);
  }

  document.addEventListener('keydown', function (e) {
    if (!e.altKey) return;
    if (e.shiftKey && (e.code === 'KeyS')) { e.preventDefault(); toggle(true); var btn = document.getElementById(BTN_ID) || document.getElementById('fmhub-scan'); doScan(btn || el('span', {})); }
    if (e.shiftKey && (e.code === 'KeyH')) {
      e.preventDefault();
      try { chrome.runtime.sendMessage({ type: 'hide', origin: location.origin }, function () { location.reload(); }); } catch (err) { }
    }
  });

  function boot() {
    if (!document.body) { setTimeout(boot, 300); return; }
    if (!/^https?:$/.test(location.protocol)) return;
    try {
      chrome.runtime.sendMessage({ type: 'hidden', origin: location.origin }, function (r) {
        if (chrome.runtime.lastError || !r || !r.hidden) mountButton();
      });
    } catch (e) { mountButton(); }
  }
  boot();
})();
