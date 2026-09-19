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
