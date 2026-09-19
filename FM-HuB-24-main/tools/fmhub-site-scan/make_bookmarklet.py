#!/usr/bin/env python3
"""Builds fmhub-scan-bookmarklet.txt from analyzer.js - the zero-install fallback.

A bookmarklet has to live in one line inside a `javascript:` URL, so comments are stripped and the
whole analyzer is wrapped in an IIFE. Kept as a generator on purpose: the bookmarklet must never
drift from analyzer.js, and re-running build.sh rewrites it.
"""
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent

WRAPPER = """(function(){var d=document,w=window;if(!w.__fmhubAnalyze){%s}
w.__fmhubScan(w,d).then(function(r){var j=JSON.stringify([r],null,0);
var ta=d.createElement('textarea');ta.value=j;
ta.style.cssText='position:fixed;left:0;right:0;bottom:0;height:45vh;z-index:2147483647;font:11px monospace;background:#0b1220;color:#e2e8f0;border:0;padding:8px';
d.body.appendChild(ta);ta.select();
var bar='position:fixed;bottom:45vh;z-index:2147483647;padding:6px 10px;font:12px system-ui;background:#132038;color:#e2e8f0;border:1px solid #22d3ee;border-radius:8px;cursor:pointer';
var mk=function(t,left,fn){var b=d.createElement('button');b.textContent=t;b.style.cssText=bar+';'+left;b.onclick=fn;return b;};
var cp=mk('Copy all','left:8px',function(){ta.focus();ta.select();try{d.execCommand('copy');cp.textContent='Copied \\u2713';}catch(e){}});
var dl=mk('Download .json','left:110px',function(){var bl=new Blob([j],{type:'application/json'});var a=d.createElement('a');a.href=URL.createObjectURL(bl);a.download='fmhub-scan-'+location.hostname+'.json';d.body.appendChild(a);a.click();setTimeout(function(){a.remove();},300);});
var cl=mk('Close','right:8px',function(){[ta,cp,dl,cl].forEach(function(n){n.remove();});});
[cp,dl,cl].forEach(function(n){d.body.appendChild(n);});
},function(e){alert('FMHub scan failed: '+e);});})();"""


def build() -> str:
    core = (HERE / 'analyzer.js').read_text()
    core = re.sub(r'[ \t]*/\*.*?\*/', '', core, flags=re.S)      # drop block comments
    core = re.sub(r'^[ \t]*//.*$', '', core, flags=re.M)          # drop line comments
    core = re.sub(r'\n\s*\n', '\n', core).strip()
    js = ' '.join((WRAPPER % core).split('\n'))
    js = re.sub(r'\s{2,}', ' ', js)
    if '</scr' in js.lower():
        sys.exit('bookmarklet contains a script end tag, which breaks it when pasted')
    return 'javascript:' + js + '\n'


if __name__ == '__main__':
    out = HERE / 'fmhub-scan-bookmarklet.txt'
    out.write_text(build())
    print(f'   ok: {out.name} ({len(out.read_text())} bytes)')
