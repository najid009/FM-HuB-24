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
