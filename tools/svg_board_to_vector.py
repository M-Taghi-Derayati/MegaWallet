# -*- coding: utf-8 -*-
"""SVG board (browser DOM dump) -> Android VectorDrawable.

Flattens every nested transform into absolute path coordinates, so the output
carries no <group> elements and no matrices left to be misinterpreted.

Usage:
    python tools/svg_board_to_vector.py <board.html> <out-dir>
"""
import io
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

SRC = sys.argv[1]
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)

html = io.open(SRC, encoding="utf-8").read()
svg_txt = html[html.index('<svg id="scene"'):html.index('</svg>') + 6]
svg_txt = re.sub(r'<!--.*?-->', '', svg_txt, flags=re.S)
root = ET.fromstring(svg_txt)
NS = '{http://www.w3.org/2000/svg}'

# The board's CSS custom properties, resolved to literals.
#
# `yellow-pale` and `stone` were never defined in the board's <style>; every other
# token matches Tailwind (blue-alt is exactly blue-500), so these follow the same
# scale — amber-200 and stone-400. Change them here and re-run if the real values
# turn up.
COLORS = {
    'graphic-blue': '#5FABEB', 'graphic-blue-alt': '#3B82F6',
    'graphic-yellow': '#F5C84C', 'graphic-gold': '#DDAA27',
    'graphic-green': '#55C878', 'graphic-gray': '#777777',
    'graphic-yellow-pale': '#FDE68A', 'graphic-stone': '#A8A29E',
}
NAMED = {'white': '#FFFFFF', 'black': '#000000', 'none': None}

# In the light theme a detached white sparkle sits on a near-white page and simply
# disappears. Only the *detached* ones are swapped; whites that lie on top of a
# coloured shape (the chat dots, the donut hole) must stay white.
LIGHT_SPARKLE = '#D8D4D0'




NUM = r'[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?'

# --------------------------- affine matrices (SVG order) ---------------------
I = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def mul(m, n):
    a1, b1, c1, d1, e1, f1 = m
    a2, b2, c2, d2, e2, f2 = n
    return (a1 * a2 + c1 * b2, b1 * a2 + d1 * b2,
            a1 * c2 + c1 * d2, b1 * c2 + d1 * d2,
            a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1)


def translate(x, y):
    return (1.0, 0.0, 0.0, 1.0, x, y)


def scale(x, y):
    return (x, 0.0, 0.0, y, 0.0, 0.0)


def rotate(deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return (c, s, -s, c, 0.0, 0.0)


def apply(m, x, y):
    a, b, c, d, e, f = m
    return (a * x + c * y + e, b * x + d * y + f)


def avg_scale(m):
    a, b, c, d, _, _ = m
    return math.sqrt(abs(a * d - b * c)) or 1.0


def parse_transform_list(s):
    m = I
    for name, args in re.findall(r'([A-Za-z]+)\s*\(([^)]*)\)', s or ''):
        v = [float(x) for x in re.findall(NUM, args)]
        if not v:
            continue
        if name == 'matrix' and len(v) >= 6:
            m = mul(m, tuple(v[:6]))
        elif name == 'translate':
            m = mul(m, translate(v[0], v[1] if len(v) > 1 else 0.0))
        elif name == 'translateX':
            m = mul(m, translate(v[0], 0.0))
        elif name == 'translateY':
            m = mul(m, translate(0.0, v[0]))
        elif name == 'scale':
            m = mul(m, scale(v[0], v[1] if len(v) > 1 else v[0]))
        elif name == 'rotate':
            if len(v) == 3:
                m = mul(m, mul(translate(v[1], v[2]),
                               mul(rotate(v[0]), translate(-v[1], -v[2]))))
            else:
                m = mul(m, rotate(v[0]))
    return m


def elem_matrix(el):
    """A CSS `transform` (honouring transform-origin) overrides the attribute."""
    style = el.get('style') or ''
    css_t = re.search(r'(?<![-\w])transform\s*:\s*([^;]+)', style)

    origin = None
    mo = re.search(r'transform-origin\s*:\s*([^;]+)', style)
    src = mo.group(1) if mo else el.get('transform-origin')
    if src:
        nums = [float(x) for x in re.findall(NUM, src)]
        if len(nums) >= 2:
            origin = (nums[0], nums[1])

    if css_t:
        val = css_t.group(1).strip()
        if val == 'none':
            return I
        m = parse_transform_list(val)
        if origin:
            m = mul(translate(*origin), mul(m, translate(-origin[0], -origin[1])))
        return m
    return parse_transform_list(el.get('transform'))


# --------------------------- shapes -> cubic segments ------------------------
K = 0.5522847498307936


def path_to_segments(d):
    toks = re.findall(r'[MmLlHhVvCcSsQqTtAaZz]|' + NUM, d)
    out = []
    i = 0
    cmd = None
    cx = cy = sx = sy = 0.0
    prev_c2 = prev_q = None

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    while i < len(toks):
        if re.match(r'^[A-Za-z]$', toks[i]):
            cmd = toks[i]
            i += 1
            if cmd in 'Zz':
                out.append(('Z',))
                cx, cy = sx, sy
                prev_c2 = prev_q = None
                continue
        if cmd is None:
            break
        rel = cmd.islower()
        c = cmd.upper()
        if c == 'M':
            x = num(); y = num()
            if rel:
                x += cx; y += cy
            out.append(('M', x, y))
            cx, cy = x, y
            sx, sy = x, y
            cmd = 'l' if rel else 'L'
            prev_c2 = prev_q = None
        elif c == 'L':
            x = num(); y = num()
            if rel:
                x += cx; y += cy
            out.append(('L', x, y))
            cx, cy = x, y
            prev_c2 = prev_q = None
        elif c == 'H':
            x = num()
            if rel:
                x += cx
            out.append(('L', x, cy))
            cx = x
            prev_c2 = prev_q = None
        elif c == 'V':
            y = num()
            if rel:
                y += cy
            out.append(('L', cx, y))
            cy = y
            prev_c2 = prev_q = None
        elif c == 'C':
            x1 = num(); y1 = num(); x2 = num(); y2 = num(); x = num(); y = num()
            if rel:
                x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy
            out.append(('C', x1, y1, x2, y2, x, y))
            prev_c2 = (x2, y2); prev_q = None
            cx, cy = x, y
        elif c == 'S':
            x2 = num(); y2 = num(); x = num(); y = num()
            if rel:
                x2 += cx; y2 += cy; x += cx; y += cy
            if prev_c2:
                x1, y1 = 2 * cx - prev_c2[0], 2 * cy - prev_c2[1]
            else:
                x1, y1 = cx, cy
            out.append(('C', x1, y1, x2, y2, x, y))
            prev_c2 = (x2, y2); prev_q = None
            cx, cy = x, y
        elif c == 'Q':
            qx = num(); qy = num(); x = num(); y = num()
            if rel:
                qx += cx; qy += cy; x += cx; y += cy
            out.append(('C', cx + 2 / 3 * (qx - cx), cy + 2 / 3 * (qy - cy),
                        x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y), x, y))
            prev_q = (qx, qy); prev_c2 = None
            cx, cy = x, y
        elif c == 'T':
            x = num(); y = num()
            if rel:
                x += cx; y += cy
            if prev_q:
                qx, qy = 2 * cx - prev_q[0], 2 * cy - prev_q[1]
            else:
                qx, qy = cx, cy
            out.append(('C', cx + 2 / 3 * (qx - cx), cy + 2 / 3 * (qy - cy),
                        x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y), x, y))
            prev_q = (qx, qy); prev_c2 = None
            cx, cy = x, y
        elif c == 'A':
            rx = num(); ry = num(); rot = num(); laf = num(); sf = num()
            x = num(); y = num()
            if rel:
                x += cx; y += cy
            out.extend(arc_to_cubics(cx, cy, rx, ry, rot, laf, sf, x, y))
            cx, cy = x, y
            prev_c2 = prev_q = None
        else:
            raise ValueError('unsupported path command: %r' % cmd)
    return out


def arc_to_cubics(x1, y1, rx, ry, phi, laf, sf, x2, y2):
    if rx == 0 or ry == 0:
        return [('L', x2, y2)]
    rx, ry = abs(rx), abs(ry)
    p = math.radians(phi)
    cosp, sinp = math.cos(p), math.sin(p)
    dx2, dy2 = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cosp * dx2 + sinp * dy2
    y1p = -sinp * dx2 + cosp * dy2
    lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
    if lam > 1:
        s = math.sqrt(lam)
        rx *= s
        ry *= s
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    num_ = max(rx * rx * ry * ry - den, 0.0)
    co = math.sqrt(num_ / den) if den else 0.0
    if laf == sf:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx = cosp * cxp - sinp * cyp + (x1 + x2) / 2.0
    cy = sinp * cxp + cosp * cyp + (y1 + y2) / 2.0

    def ang(ux, uy, vx, vy):
        d = math.hypot(ux, uy) * math.hypot(vx, vy)
        c = max(-1.0, min(1.0, (ux * vx + uy * vy) / d)) if d else 1.0
        a = math.acos(c)
        return -a if (ux * vy - uy * vx) < 0 else a

    ux, uy = (x1p - cxp) / rx, (y1p - cyp) / ry
    vx, vy = (-x1p - cxp) / rx, (-y1p - cyp) / ry
    th1 = ang(1, 0, ux, uy)
    dth = ang(ux, uy, vx, vy)
    if sf == 0 and dth > 0:
        dth -= 2 * math.pi
    if sf == 1 and dth < 0:
        dth += 2 * math.pi

    n = max(1, int(math.ceil(abs(dth) / (math.pi / 2))))
    delta = dth / n
    t = 4.0 / 3.0 * math.tan(delta / 4.0)
    out = []
    th = th1
    px, py = x1, y1
    for _ in range(n):
        th2 = th + delta
        c1, s1 = math.cos(th), math.sin(th)
        c2, s2 = math.cos(th2), math.sin(th2)
        ex = cosp * rx * c2 - sinp * ry * s2 + cx
        ey = sinp * rx * c2 + cosp * ry * s2 + cy
        d1x, d1y = -rx * s1, ry * c1
        d2x, d2y = -rx * s2, ry * c2
        t1x, t1y = cosp * d1x - sinp * d1y, sinp * d1x + cosp * d1y
        t2x, t2y = cosp * d2x - sinp * d2y, sinp * d2x + cosp * d2y
        out.append(('C', px + t * t1x, py + t * t1y, ex - t * t2x, ey - t * t2y, ex, ey))
        px, py = ex, ey
        th = th2
    return out


def ellipse_segments(cx, cy, rx, ry):
    return [('M', cx + rx, cy),
            ('C', cx + rx, cy + ry * K, cx + rx * K, cy + ry, cx, cy + ry),
            ('C', cx - rx * K, cy + ry, cx - rx, cy + ry * K, cx - rx, cy),
            ('C', cx - rx, cy - ry * K, cx - rx * K, cy - ry, cx, cy - ry),
            ('C', cx + rx * K, cy - ry, cx + rx, cy - ry * K, cx + rx, cy),
            ('Z',)]


def rect_segments(x, y, w, h, rx, ry):
    if rx <= 0 and ry <= 0:
        return [('M', x, y), ('L', x + w, y), ('L', x + w, y + h), ('L', x, y + h), ('Z',)]
    rx = min(rx or ry, w / 2.0)
    ry = min(ry or rx, h / 2.0)
    return [('M', x + rx, y),
            ('L', x + w - rx, y),
            ('C', x + w - rx + rx * K, y, x + w, y + ry - ry * K, x + w, y + ry),
            ('L', x + w, y + h - ry),
            ('C', x + w, y + h - ry + ry * K, x + w - rx + rx * K, y + h, x + w - rx, y + h),
            ('L', x + rx, y + h),
            ('C', x + rx - rx * K, y + h, x, y + h - ry + ry * K, x, y + h - ry),
            ('L', x, y + ry),
            ('C', x, y + ry - ry * K, x + rx - rx * K, y, x + rx, y),
            ('Z',)]


def xform(segs, m):
    out = []
    for s in segs:
        if s[0] == 'Z':
            out.append(s)
            continue
        pts = []
        for i in range(1, len(s), 2):
            pts.extend(apply(m, s[i], s[i + 1]))
        out.append((s[0],) + tuple(pts))
    return out


def color_of(v, default=None):
    if not v:
        return default
    v = v.strip()
    mv = re.match(r'var\(\s*--([\w-]+)', v)
    if mv:
        return COLORS.get(mv.group(1), '#777777')
    if v.lower() in NAMED:
        return NAMED[v.lower()]
    if v.startswith('#'):
        return '#' + ''.join(c * 2 for c in v[1:]) if len(v) == 4 else v.upper()
    return default


def collect(el, m, out):
    m = mul(m, elem_matrix(el))
    tag = el.tag.replace(NS, '')
    if tag in ('g', 'svg'):
        for ch in el:
            collect(ch, m, out)
        return

    segs = None
    if tag == 'path' and el.get('d'):
        segs = path_to_segments(el.get('d'))
    elif tag == 'rect':
        segs = rect_segments(float(el.get('x', 0) or 0), float(el.get('y', 0) or 0),
                             float(el.get('width', 0) or 0), float(el.get('height', 0) or 0),
                             float(el.get('rx', 0) or 0), float(el.get('ry', 0) or 0))
    elif tag in ('circle', 'ellipse'):
        cx = float(el.get('cx', 0) or 0)
        cy = float(el.get('cy', 0) or 0)
        if tag == 'circle':
            rx = ry = float(el.get('r', 0) or 0)
        else:
            rx = float(el.get('rx', 0) or 0)
            ry = float(el.get('ry', 0) or 0)
        segs = ellipse_segments(cx, cy, rx, ry)
    if segs is None:
        return

    out.append(dict(
        segs=xform(segs, m),
        fill=color_of(el.get('fill'), '#000000'),
        stroke=color_of(el.get('stroke'), None),
        sw=float(el.get('stroke-width', 0) or 0) * avg_scale(m),
        fill_alpha=float(el.get('fill-opacity') or 1.0),
        stroke_alpha=float(el.get('stroke-opacity') or 1.0),
        evenodd=(el.get('fill-rule') == 'evenodd'),
        cap=el.get('stroke-linecap'),
    ))


def bbox(shapes):
    xs, ys = [], []
    for sh in shapes:
        pad = sh['sw'] / 2.0 if sh['stroke'] else 0.0
        for s in sh['segs']:
            for i in range(1, len(s), 2):
                xs += [s[i] - pad, s[i] + pad]
                ys += [s[i + 1] - pad, s[i + 1] + pad]
    return min(xs), min(ys), max(xs), max(ys)


def flatten(segs, steps=10):
    """Cubic segments -> closed polylines, for containment tests."""
    polys = []
    cur = []
    px = py = 0.0
    for s in segs:
        if s[0] == 'M':
            if len(cur) > 2:
                polys.append(cur)
            cur = [(s[1], s[2])]
            px, py = s[1], s[2]
        elif s[0] == 'L':
            cur.append((s[1], s[2]))
            px, py = s[1], s[2]
        elif s[0] == 'C':
            x1, y1, x2, y2, x, y = s[1:]
            for k in range(1, steps + 1):
                t = k / float(steps)
                u = 1 - t
                cur.append((u*u*u*px + 3*u*u*t*x1 + 3*u*t*t*x2 + t*t*t*x,
                            u*u*u*py + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y))
            px, py = x, y
        elif s[0] == 'Z':
            if len(cur) > 2:
                polys.append(cur)
                cur = [cur[0]]
    if len(cur) > 2:
        polys.append(cur)
    return polys


def inside(pt, polys):
    """Even-odd crossing count across every subpath, so holes count as outside."""
    x, y = pt
    crossings = 0
    for poly in polys:
        n = len(poly)
        for i in range(n):
            x1, y1 = poly[i]
            x2, y2 = poly[(i + 1) % n]
            if (y1 > y) != (y2 > y):
                xi = x1 + (y - y1) * (x2 - x1) / (y2 - y1)
                if xi > x:
                    crossings += 1
    return crossings % 2 == 1


def centroid(polys):
    pts = [p for poly in polys for p in poly]
    return (sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))


def mark_detached_whites(shapes):
    """Flag white paths that sit beside the artwork rather than on it.

    Neither colour nor size separates the two cases — the chat bubble's dots and
    the gear's sparkles are both small opaque whites. What separates them is
    whether they land on painted ink, so the test is the white shape's centroid
    against the *actual* filled geometry. A bounding box is not enough: the gear
    is round, and its sparkles sit in a corner that its box covers but its ink
    does not.
    """
    coloured = [flatten(s['segs']) for s in shapes if s['fill'] != '#FFFFFF']
    for sh in shapes:
        sh['detached'] = False
        # Only opaque whites are candidates. The translucent ones are highlights
        # painted over a shape; where they miss the ink they are invisible anyway,
        # and the gear's highlight happens to centre on its hole.
        if sh['fill'] != '#FFFFFF' or sh['fill_alpha'] != 1.0:
            continue
        c = centroid(flatten(sh['segs']))
        sh['detached'] = not any(inside(c, polys) for polys in coloured)


def fmt(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return s if s not in ('', '-') else '0'


def path_data(segs, ox, oy):
    parts = []
    for s in segs:
        if s[0] == 'Z':
            parts.append('Z')
            continue
        nums = []
        for i in range(1, len(s), 2):
            nums += [fmt(s[i] - ox), fmt(s[i + 1] - oy)]
        parts.append(s[0] + ','.join(nums))
    return ' '.join(parts)


def render(shapes, x0, y0, w, h, light):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/svg_board_to_vector.py — do not hand-edit.',
        '     Every nested transform is baked into the path coordinates. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="%sdp"' % fmt(w),
        '    android:height="%sdp"' % fmt(h),
        '    android:viewportWidth="%s"' % fmt(w),
        '    android:viewportHeight="%s">' % fmt(h),
    ]
    for sh in shapes:
        fill = sh['fill']
        if light and sh['detached'] and fill == '#FFFFFF':
            fill = LIGHT_SPARKLE
        lines.append('    <path')
        lines.append('        android:pathData="%s"' % path_data(sh['segs'], x0, y0))
        if fill:
            lines.append('        android:fillColor="%s"' % fill)
            if sh['fill_alpha'] != 1.0:
                lines.append('        android:fillAlpha="%s"' % fmt(sh['fill_alpha']))
            if sh['evenodd']:
                lines.append('        android:fillType="evenOdd"')
        else:
            lines.append('        android:fillColor="#00000000"')
        if sh['stroke']:
            lines.append('        android:strokeColor="%s"' % sh['stroke'])
            lines.append('        android:strokeWidth="%s"' % fmt(sh['sw']))
            if sh['cap']:
                lines.append('        android:strokeLineCap="%s"' % sh['cap'])
            if sh['stroke_alpha'] != 1.0:
                lines.append('        android:strokeAlpha="%s"' % fmt(sh['stroke_alpha']))
        lines.append('        />')
    lines.append('</vector>')
    return '\n'.join(lines) + '\n'


items = root.findall('.//' + NS + "g[@class='item']")
print('items found:', len(items))
report = []
for el in items:
    n = int(el.get('data-item'))
    shapes = []
    collect(el, I, shapes)
    if not shapes:
        print('item %d produced nothing' % n)
        continue
    mark_detached_whites(shapes)
    x0, y0, x1, y1 = bbox(shapes)
    w, h = x1 - x0, y1 - y0

    io.open(os.path.join(OUT, 'ic_intro_%02d.xml' % n), 'w',
            encoding='utf-8', newline='\n').write(render(shapes, x0, y0, w, h, False))

    sparkles = sum(1 for s in shapes if s['detached'] and s['fill'] == '#FFFFFF')
    if sparkles:
        io.open(os.path.join(OUT, 'ic_intro_%02d_light.xml' % n), 'w',
                encoding='utf-8', newline='\n').write(render(shapes, x0, y0, w, h, True))
    report.append((n, round(w, 1), round(h, 1), len(shapes), sparkles))

print()
for n, w, h, c, sp in sorted(report):
    extra = '   +light variant (%d sparkles)' % sp if sp else ''
    print('item %2d   %7s x %-7s   paths=%d   aspect=%.2f%s' % (n, w, h, c, w / h, extra))
