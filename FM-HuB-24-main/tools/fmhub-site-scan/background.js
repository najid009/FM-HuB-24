/*
 * Service worker: (1) keeps a per-tab request log (webRequest sees requests inside cross-origin
 * iframes too, which the page-side hook cannot), (2) fans a scan out to every frame of the active
 * tab, and (3) stores the collected records so they survive reloads between the 4 pages you scan.
 * No telemetry leaves the browser: nothing here fetches a remote server.
 */
'use strict';

var MAX_PER_TAB = 400;
var MEDIA_RE = /\.(m3u8|mpd|mp4|mkv|webm|ts)(\?|#|$)/i;
var net = new Map();          // tabId -> entries
var hidden = new Set();       // origins where the floating button is suppressed
var records = [];             // last scan results, keyed per origin in storage
var ready = false;

function ensure() {
  if (ready) return Promise.resolve();
  ready = true;
  return chrome.storage.local.get(['fmhub:hidden']).then(function (all) {
    (all['fmhub:hidden'] || []).forEach(function (o) { hidden.add(o); });
  }).catch(function () { });
}

function push(tabId, entry) {
  if (typeof tabId !== 'number' || tabId < 0) return;
  var list = net.get(tabId);
  if (!list) { list = []; net.set(tabId, list); }
  list.push(entry);
  if (list.length > MAX_PER_TAB) list.shift();
}

function urlBase(u) { try { return new URL(u).host + new URL(u).pathname; } catch (e) { return String(u).slice(0, 120); } }

chrome.webRequest.onBeforeRequest.addListener(function (d) {
  if (['main_frame', 'sub_frame', 'xmlhttprequest', 'media', 'object', 'websocket', 'other'].indexOf(d.type) < 0) return;
  push(d.tabId, {
    at: Math.round(d.timeStamp), type: d.type, method: d.method,
    url: (d.url || '').slice(0, 400),
    firstPartyUrl: (d.initiator || '').slice(0, 200),
    media: MEDIA_RE.test(d.url || '')
  });
}, { urls: ['<all_urls>'] }, ['type']);

chrome.webRequest.onResponseStarted.addListener(function (d) {
  var list = net.get(d.tabId);
  if (!list) return;
  var base = urlBase(d.url || '');
  for (var i = list.length - 1; i >= 0 && i > list.length - 30; i--) {
    if (urlBase(list[i].url) === base) {
      list[i].status = d.statusCode;
      list[i].finalUrl = (d.finalUrl || '').slice(0, 400);
      (d.responseHeaders || []).forEach(function (h) {
        var n = h.name.toLowerCase();
        if (n === 'content-type') list[i].contentType = h.value;
        if (n === 'content-length') list[i].contentLength = h.value;
        if (n === 'server') list[i].server = h.value;
      });
      break;
    }
  }
}, { urls: ['<all_urls>'] }, ['responseHeaders']);

chrome.tabs.onRemoved.addListener(function (tabId) { net.delete(tabId); });

function frameScan(tabId, frameId) {
  return new Promise(function (resolve) {
    var timer = setTimeout(function () {
      resolve({ frameId: frameId, error: 'no answer from this frame (content script blocked, or the frame is not an http page)' });
    }, 8000);
    try {
      chrome.tabs.sendMessage(tabId, { type: 'frameScan' }, { frameId: frameId }, function (res) {
        clearTimeout(timer);
        if (chrome.runtime.lastError) {
          resolve({ frameId: frameId, error: String(chrome.runtime.lastError.message || 'unreachable') });
          return;
        }
        resolve(Object.assign({ frameId: frameId }, res || {}));
      });
    } catch (e) {
      clearTimeout(timer);
      resolve({ frameId: frameId, error: String(e.message || e) });
    }
  });
}

function scanTab(tabId) {
  return chrome.webNavigation.getAllFrames({ tabId: tabId }).then(function (frames) {
    var list = frames || [];
    return Promise.all(list.map(function (f) { return frameScan(tabId, f.frameId); })).then(function (results) {
      // Top frame first, then frames that actually found something interesting.
      return results.sort(function (a, b) {
        if (a.isTop && !b.isTop) return -1;
        if (b.isTop && !a.isTop) return 1;
        return (b.result ? 1 : 0) - (a.result ? 1 : 0);
      });
    });
  });
}

function tabNet(tabId) {
  var list = net.get(tabId) || [];
  return list.filter(function (e) {
    return e.type !== 'main_frame' && (e.media || e.type === 'xmlhttprequest' || e.type === 'sub_frame' || e.type === 'object');
  }).slice(-120);
}

chrome.runtime.onMessage.addListener(function (msg, sender, respond) {
  if (!msg || !msg.type) return;
  ensure().then(function () {
    var tabId = (typeof msg.tabId === 'number' && msg.tabId >= 0) ? msg.tabId : (sender.tab ? sender.tab.id : -1);
    switch (msg.type) {
      case 'scanTab':
        scanTab(tabId).then(function (frames) {
          respond({ ok: true, frames: frames, net: tabNet(tabId) });
        });
        return true;
      case 'net':
        respond({ ok: true, net: tabNet(tabId) });
        return true;
      case 'hidden':
        respond({ hidden: hidden.has(msg.origin) });
        return true;
      case 'hide':
        hidden.add(msg.origin);
        chrome.storage.local.set({ 'fmhub:hidden': Array.prototype.slice.call(hidden) });
        respond({ ok: true });
        return true;
      case 'unhideAll':
        hidden.clear();
        chrome.storage.local.set({ 'fmhub:hidden': [] });
        respond({ ok: true });
        return true;
      case 'save':
        records = (msg.records || []).slice(-40);
        chrome.storage.local.set({ 'fmhub:records': records });
        setBadge(records.length);
        respond({ ok: true, count: records.length });
        return;
      case 'load':
      case 'exportAll':
        chrome.storage.local.get(['fmhub:records']).then(function (all) {
          respond({ records: all['fmhub:records'] || [] });
        });
        return;
      case 'clear':
        records = [];
        chrome.storage.local.set({ 'fmhub:records': [] });
        setBadge(0);
        respond({ ok: true });
        return;
    }
  });
  // respond() is called after ensure()/getAllFrames() resolve, so the channel must stay open.
  return true;
});

function setBadge(n) {
  try { chrome.action.setBadgeText({ text: n ? String(n) : '' }); } catch (e) { /* no action API on some builds */ }
}

chrome.runtime.onInstalled.addListener(function () {
  ensure().then(function () { setBadge(records.length); });
});
