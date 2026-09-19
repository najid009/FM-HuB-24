'use strict';

var statusEl = document.getElementById('status');
var listEl = document.getElementById('list');

function say(text, warn) {
  statusEl.textContent = text;
  statusEl.className = warn ? 'warn' : '';
}

function activeTab() {
  return chrome.tabs.query({ active: true, currentWindow: true }).then(function (tabs) {
    return tabs && tabs[0];
  });
}

function counts(rec) {
  var c = { frames: (rec.frames || []).length, groups: 0, eps: 0, iframes: 0, media: 0, ajax: 0, errs: 0 };
  (rec.frames || []).forEach(function (f) {
    var r = f.result;
    if (!r) { c.errs++; return; }
    c.groups += (r.itemGroups || []).length;
    c.eps += (r.episodes || []).reduce(function (a, e) { return a + (e.count || 0); }, 0);
    c.iframes += (r.player && r.player.frames ? r.player.frames.length : 0);
    c.media += (r.perf && r.perf.media ? r.perf.media.length : 0);
    c.ajax += (r.hookedRequests || []).length;
  });
  return c;
}

function render(records) {
  listEl.innerHTML = '';
  if (!records.length) {
    var p = document.createElement('div');
    p.className = 'k';
    p.textContent = 'No scans stored yet.';
    listEl.appendChild(p);
    return;
  }
  records.forEach(function (rec, i) {
    var c = counts(rec);
    var d = document.createElement('div');
    d.className = 'item';
    var t = document.createElement('div');
    t.textContent = '#' + (i + 1) + ' ' + (rec.pageType || '?') + '  ' + (rec.url || '');
    d.appendChild(t);
    var tags = document.createElement('div');
    [['frames', c.frames], ['card groups', c.groups], ['episodes', c.eps],
     ['player iframes', c.iframes], ['media urls', c.media], ['ajax', c.ajax],
     ['requests logged', (rec.net || []).length]].forEach(function (pair) {
      var s = document.createElement('span');
      s.className = 'tag';
      s.textContent = pair[0] + ': ' + pair[1];
      tags.appendChild(s);
    });
    if (c.errs) {
      var e = document.createElement('span');
      e.className = 'tag';
      e.style.color = '#fbbf24';
      e.textContent = 'unreadable frames: ' + c.errs;
      tags.appendChild(e);
    }
    d.appendChild(tags);
    listEl.appendChild(d);
  });
}

function load() {
  return chrome.runtime.sendMessage({ type: 'load' }).then(function (r) {
    render((r && r.records) || []);
    return (r && r.records) || [];
  });
}

document.getElementById('scan').addEventListener('click', function () {
  say('Scanning every frame…');
  activeTab().then(function (tab) {
    if (!tab) { say('No active tab', true); return; }
    return chrome.runtime.sendMessage({ type: 'scanTab', tabId: tab.id }).then(function (res) {
      var frames = (res && res.frames) || [];
      var first = frames.length && frames[0].result ? frames[0].result : {};
      var rec = {
        at: new Date().toISOString(), url: tab.url, origin: first.origin || '',
        title: tab.title, pageType: first.pageType || 'unknown',
        frames: frames, net: (res && res.net) || [], userAgent: navigator.userAgent
      };
      return load().then(function (records) {
        records.push(rec);
        return chrome.runtime.sendMessage({ type: 'save', records: records });
      });
    }).then(function () {
      return load();
    }).then(function (records) {
      var last = records[records.length - 1] || {};
      var c = counts(last);
      say('Saved scan #' + records.length + ' (frames: ' + c.frames + ', card groups: ' + c.groups +
        ', episodes: ' + c.eps + ', media: ' + c.media + ')');
    });
  }).catch(function (e) { say('Failed: ' + e.message, true); });
});

document.getElementById('dl').addEventListener('click', function () {
  load().then(function (records) {
    if (!records.length) { say('Nothing to download yet', true); return; }
    var blob = new Blob([JSON.stringify(records, null, 1)], { type: 'application/json' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'fmhub-scan-' + new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-') + '.json';
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 400);
    say('Downloaded ' + records.length + ' scan(s). Attach that file in the chat.');
  });
});

document.getElementById('copy').addEventListener('click', function () {
  load().then(function (records) {
    if (!records.length) { say('Nothing to copy yet', true); return; }
    return navigator.clipboard.writeText(JSON.stringify(records, null, 1)).then(function () {
      say('Copied ' + records.length + ' scan(s) - paste into the chat');
    });
  }).catch(function (e) { say('Copy failed: ' + e.message, true); });
});

document.getElementById('clear').addEventListener('click', function () {
  chrome.runtime.sendMessage({ type: 'clear' }).then(load).then(function () { say('Cleared'); });
});

document.getElementById('unhide').addEventListener('click', function () {
  chrome.runtime.sendMessage({ type: 'unhideAll' }).then(function () {
    say('Floating button enabled again on all sites');
  });
});

load();
