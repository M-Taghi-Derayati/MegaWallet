# -*- coding: utf-8 -*-
"""Render the generated VectorDrawables back into an HTML sheet, so the
conversion can actually be looked at instead of trusted.

Usage: python tools/preview_vectors.py <drawable-dir> <out.html>
"""
import glob
import io
import os
import re
import sys
import xml.etree.ElementTree as ET

SRC, OUT = sys.argv[1], sys.argv[2]
A = '{http://schemas.android.com/apk/res/android}'

cards = []
for f in sorted(glob.glob(os.path.join(SRC, 'ic_intro_*.xml'))):
    root = ET.parse(f).getroot()
    vw = root.get(A + 'viewportWidth')
    vh = root.get(A + 'viewportHeight')
    paths = []
    for p in root.findall('path'):
        d = p.get(A + 'pathData')
        fill = p.get(A + 'fillColor') or 'none'
        fa = p.get(A + 'fillAlpha') or '1'
        ftype = p.get(A + 'fillType')
        sc = p.get(A + 'strokeColor')
        sw = p.get(A + 'strokeWidth')
        cap = p.get(A + 'strokeLineCap')
        attrs = ['d="%s"' % d]
        attrs.append('fill="%s"' % ('none' if fill == '#00000000' else fill))
        if fa != '1':
            attrs.append('fill-opacity="%s"' % fa)
        if ftype:
            attrs.append('fill-rule="evenodd"')
        if sc:
            attrs.append('stroke="%s" stroke-width="%s"' % (sc, sw))
            if cap:
                attrs.append('stroke-linecap="%s"' % cap)
        paths.append('<path %s/>' % ' '.join(attrs))
    name = os.path.basename(f)
    cards.append(
        '<figure><svg viewBox="0 0 %s %s" preserveAspectRatio="xMidYMid meet">%s</svg>'
        '<figcaption>%s<br><small>%s x %s · %d paths</small></figcaption></figure>'
        % (vw, vh, ''.join(paths), name, vw, vh, len(paths)))

html = """<!doctype html><meta charset="utf-8"><title>intro vectors</title>
<style>
body{margin:0;font:13px system-ui;background:#eef1f5;color:#18212f}
h2{margin:16px 20px 4px}
.sheet{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:12px;padding:16px 20px}
figure{margin:0;background:#fff;border:1px solid #dfe4ea;border-radius:12px;padding:10px;text-align:center}
figure.dark{background:#111418;color:#e6e9ef;border-color:#2a3038}
svg{width:100%%;height:110px;display:block}
figcaption{margin-top:6px;font-weight:600}
small{font-weight:400;opacity:.65}
</style>
<h2>Light background</h2><div class="sheet">%s</div>
<h2>Dark background</h2><div class="sheet">%s</div>
""" % (''.join(cards), ''.join(c.replace('<figure>', '<figure class="dark">') for c in cards))

io.open(OUT, 'w', encoding='utf-8', newline='\n').write(html)
print('wrote', OUT, 'with', len(cards), 'items')
