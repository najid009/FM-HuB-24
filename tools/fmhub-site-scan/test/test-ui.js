/* Exercises the parts that do not need Chrome: the MAIN<->ISOLATED postMessage protocol and the
 * full userscript build (boot -> panel -> scan -> localStorage). */
'use strict';
const fs = require('fs');
const { JSDOM } = require('jsdom');
const dir = require('path').join(__dirname, '..');
const analyzer = fs.readFileSync(dir + '/analyzer.js', 'utf8');
const main = fs.readFileSync(dir + '/main.js', 'utf8');
const userscript = fs.readFileSync(dir + '/fmhub-scan.user.js', 'utf8');

let failures = 0;
const check = (label, cond, extra) => {
  console.log(`${cond ? 'ok  ' : 'FAIL'}  ${label}${cond ? '' : '  <<< ' + JSON.stringify(extra)}`);
  if (!cond) failures++;
};

const html = `<html><head><title>Naruto</title></head><body>
  <div class="row"><div class="thumb"><a href="/series/a/" title="A"><img src="/a.jpg"><div class="title">A</div></a></div>
  <div class="thumb"><a href="/series/b/" title="B"><img src="/b.jpg"><div class="title">B</div></a></div>
  <div class="thumb"><a href="/series/c/" title="C"><img src="/c.jpg"><div class="title">C</div></a></div></div>
  <iframe src="https://embed.example/e/1"></iframe></body></html>`;

/* --- 1. MAIN world glue: ping + scan over window.postMessage ------------------------------- */
const dom = new JSDOM(html, { url: 'https://watchanimeworld.one/?s=naruto', runScripts: 'dangerously', pretendToBeVisual: true });
const w = dom.window;
w.eval(analyzer);
w.eval(main);

check('main.js: window.__fmhub exposed', w.__fmhub && w.__fmhub.version === '1.0.0', typeof w.__fmhub);

const seen = [];
w.addEventListener('message', (e) => { if (e.data && e.data.__fmhub === 'hello') seen.push(e.data); });
w.dispatchEvent(Object.assign(new w.MessageEvent('message'), {}));

new Promise((resolve) => {
  const handler = (ev) => {
    const d = ev.data;
    if (d && d.__fmhub === 'res' && d.id === 'ping-1') { w.removeEventListener('message', handler); resolve(d); }
  };
  w.addEventListener('message', handler);
  w.postMessage({ __fmhub: 'req', cmd: 'ping', id: 'ping-1' }, '*');
  setTimeout(() => resolve({ timeout: true }), 1500);
}).then((ping) => {
  check('protocol: ping answered with pong', ping && ping.ok === true && ping.result && ping.result.pong === true, ping);
  return new Promise((resolve) => {
    const handler = (ev) => {
      const d = ev.data;
      if (d && d.__fmhub === 'res' && d.id === 'scan-1') { w.removeEventListener('message', handler); resolve(d); }
    };
    w.addEventListener('message', handler);
    w.postMessage({ __fmhub: 'req', cmd: 'scan', id: 'scan-1' }, '*');
    setTimeout(() => resolve({ timeout: true }), 2500);
  });
}).then((scan) => {
  const r = scan && scan.result;
  check('protocol: scan returned a report', scan && scan.ok === true && !!r, { ok: scan && scan.ok, err: scan && scan.error });
  check('protocol: report carries the card grid + iframe',
    r && r.itemGroups.length >= 1 && r.itemGroups[0].items.length >= 3 &&
    /embed\.example/.test((r.player.frames[0] || {}).src || ''),
    r && { groups: r.itemGroups.length, frames: r.player.frames });
  check('protocol: searchProbe present and never throws', Array.isArray(r && r.searchProbe) || (r && r.searchProbe === 'failed'), r && r.searchProbe);

  /* --- 2. userscript build: boot, button, scan, storage ------------------------------------ */
  const dom2 = new JSDOM(html, { url: 'https://watchanimeworld.one/episode/x-1/', runScripts: 'dangerously', pretendToBeVisual: true });
  const w2 = dom2.window;
  let evalError = null;
  try { w2.eval(userscript); } catch (e) { evalError = String(e); }
  check('userscript: metadata block first line is ==UserScript==',
    userscript.split('\n')[0].trim() === '// ==UserScript==', userscript.split('\n')[0]);
  check('userscript: evaluates without throwing', evalError === null, evalError);
  const btn = w2.document.querySelector('.fmhub-us-btn');
  check('userscript: floating button mounted', !!btn, btn && btn.textContent);
  check('userscript: scan helpers exposed', typeof w2.__fmhubScanAll === 'function' && typeof w2.__fmhubAll === 'function');
  return w2.__fmhubScanAll().then((rec) => {
    check('userscript: scan returns frames', Array.isArray(rec.frames) && rec.frames.length >= 1, rec.frames);
    check('userscript: pageType detected from URL', rec.pageType === 'episode', rec.pageType);
    const stored = JSON.parse(w2.localStorage.getItem('fmhubScans:v1') || '[]');
    check('userscript: scan persisted to localStorage', stored.length === 1, stored.length);
    console.log(failures ? `\n${failures} FAILURE(S)` : '\nall good');
    process.exit(failures ? 1 : 0);
  });
}).catch((e) => { console.log('harness error', e); process.exit(1); });
