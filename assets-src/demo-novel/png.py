"""Minimal PNG reading and writing (standard library only) for composing the novel demo backgrounds (I-470)."""
import zlib, struct
def read(path):
    d = open(path, 'rb').read(); pos = 8; idat = b''; w = h = 0; ct = 0; pal = None; trns = None
    while pos < len(d):
        n, t = struct.unpack('>I4s', d[pos:pos+8]); c = d[pos+8:pos+8+n]; pos += 12 + n
        if t == b'IHDR': w, h, bd, ct = struct.unpack('>IIBB', c[:10])
        elif t == b'PLTE': pal = [tuple(c[i:i+3]) for i in range(0, len(c), 3)]
        elif t == b'tRNS': trns = c
        elif t == b'IDAT': idat += c
    raw = zlib.decompress(idat); bpp = {6: 4, 2: 3, 3: 1, 4: 2, 0: 1}[ct]; stride = w * bpp
    rows = []; prev = bytearray(stride); i = 0
    for y in range(h):
        f = raw[i]; line = bytearray(raw[i+1:i+1+stride]); i += 1 + stride
        for x in range(stride):
            a = line[x-bpp] if x >= bpp else 0; b = prev[x]; cc = prev[x-bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - cc; pa, pb, pc = abs(p-a), abs(p-b), abs(p-cc)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else cc)) & 255
        rows.append(line); prev = line
    px = []
    for line in rows:
        r = []
        for x in range(w):
            if ct == 6: r.append(tuple(line[x*4:x*4+4]))
            elif ct == 2: r.append(tuple(line[x*3:x*3+3]) + (255,))
            elif ct == 3: k = line[x]; r.append(pal[k] + ((trns[k] if trns and k < len(trns) else 255),))
            elif ct == 4: r.append((line[x*2],)*3 + (line[x*2+1],))
            else: r.append((line[x],)*3 + (255,))
        px.append(r)
    return w, h, px
def write(path, w, h, px):
    raw = b''.join(b'\0' + bytes(v for p in row for v in p) for row in px)
    ch = lambda t, c: struct.pack('>I', len(c)) + t + c + struct.pack('>I', zlib.crc32(t + c))
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + ch(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + ch(b'IDAT', zlib.compress(raw)) + ch(b'IEND', b''))
