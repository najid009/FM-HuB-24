/*
 * MAIN-world glue. The analyzer must run in the page's own world, otherwise page globals
 * (`player_id`, `AJAX`, jw config …) are invisible and the embed-detection half of the report is
 * worthless. Extension APIs are NOT available here, so the isolated content script (ui.js) talks
 * to this file through window.postMessage.
 */
(function () {
  'use strict';
  var w = window;
  if (w.__fmhubMainReady) return;
  w.__fmhubMainReady = true;

  try { w.__fmhubHook(w); } catch (e) { /* oldest browsers only */ }

  // Console helpers, so the tool is usable even without the panel: __fmhubPrint() then copy().
  w.__fmhub = {
    version: '1.0.0',
    scan: function () { return w.__fmhubScan(w, document); },
    net: function () { return (w.__fmhubNetLog || []).slice(-40); }
  };
  w.__fmhubPrint = function () {
    return w.__fmhub.scan().then(function (r) {
      var text = JSON.stringify(r, null, 1);
      console.log(text);
      console.log('%cFMHub: full JSON above - open DevTools console, right-click the object or run copy(__fmhub.last)', 'color:#22d3ee');
      w.__fmhub.last = r;
      return r;
    });
  };

  window.addEventListener('message', function (e) {
    var d = e.data;
    if (!d || d.__fmhub !== 'req' || !d.id) return;
    function reply(payload) {
      payload.__fmhub = 'res';
      payload.id = d.id;
      try { window.postMessage(payload, '*'); } catch (err) { /* ignore */ }
    }
    if (d.cmd === 'ping') {
      reply({ ok: true, result: { pong: true, url: location.href, isTop: window.top === window, captured: (w.__fmhubNetLog || []).length } });
    } else if (d.cmd === 'scan') {
      w.__fmhubScan(w, document).then(function (r) {
        reply({ ok: true, result: r });
      }, function (err) {
        reply({ ok: false, error: String((err && err.message) || err) });
      });
    } else if (d.cmd === 'net') {
      reply({ ok: true, result: (w.__fmhubNetLog || []).slice(-40) });
    }
  });

  try {
    window.postMessage({ __fmhub: 'hello', url: location.href, isTop: window.top === window }, '*');
  } catch (e) { /* ignore */ }
})();
