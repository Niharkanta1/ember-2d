"""Builds the novel demo's data files (I-470) into demos/demo-novel/core/assets.

- Copies the character and fruit sheets from the developer's "Pixel Adventure" pack (assets/platformer-pixel-adventure,
  licence confirmed for the platformer demo) and the Press Start 2P font from the platformer demo.
- Composes the backgrounds and the CG (320 x 180) from the pack's background and terrain tiles.
- Writes the atlas that names the expressions, the cast, the input bindings and the manifest.

    python assets-src/demo-novel/make_assets.py

Sounds come from sound/make_sounds.py; the Ink story is compiled by the compileInk Gradle task.
"""
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import png  # noqa: E402

REPO = os.path.normpath(os.path.join(HERE, '..', '..'))
PACK = os.path.join(REPO, 'assets', 'platformer-pixel-adventure')
OUT = os.path.join(REPO, 'demos', 'demo-novel', 'core', 'assets')
PLATFORMER = os.path.join(REPO, 'demos', 'demo-platformer', 'core', 'assets')
W, H = 320, 180

SHEETS = {   # pack file -> name in the demo (atlas pages)
    'Ninja Frog/Idle (32x32).png': 'gfx/frog_idle.png',
    'Ninja Frog/Hit (32x32).png': 'gfx/frog_hit.png',
    'Ninja Frog/Jump (32x32).png': 'gfx/frog_jump.png',
    'Enemies/Bunny/Idle (34x44).png': 'gfx/bunny_idle.png',
    'Enemies/Bunny/Hit (34x44).png': 'gfx/bunny_hit.png',
    'Enemies/Bunny/Jump.png': 'gfx/bunny_jump.png',
    'Items/Fruits/Cherries.png': 'gfx/cherries.png',
}
ATLAS = {
    'frog_idle.png': ((352, 32), {'kero_normal': (0, 0, 32, 32)}),
    'frog_hit.png': ((224, 32), {'kero_surprised': (64, 0, 32, 32)}),   # frames 0-1 are white flashes
    'frog_jump.png': ((32, 32), {'kero_happy': (0, 0, 32, 32)}),
    'bunny_idle.png': ((272, 44), {'pip_normal': (0, 0, 34, 44)}),
    'bunny_hit.png': ((170, 44), {'pip_surprised': (68, 0, 34, 44)}),
    'bunny_jump.png': ((34, 44), {'pip_happy': (0, 0, 34, 44)}),
    'cherries.png': ((544, 32), {'cherries': (0, 0, 32, 32)}),
}
# Terrain tiles (x, y of the 16 px tile): a top edge and the soil below it.
GRASS = ((112, 0), (112, 16))
AUTUMN = ((112, 64), (112, 80))
BLOSSOM = ((112, 128), (112, 144))


def write(path, text):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print('wrote', path)


def write_json(path, data):
    write(path, json.dumps(data, indent=1) + '\n')


def copy_files():
    for src, dst in SHEETS.items():
        full = os.path.join(OUT, dst)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        shutil.copyfile(os.path.join(PACK, src), full)
        print('copied', src, '->', dst)
    for f in ('PressStart2P-Regular.ttf', 'OFL.txt'):
        os.makedirs(os.path.join(OUT, 'fonts'), exist_ok=True)
        shutil.copyfile(os.path.join(PLATFORMER, 'fonts', f), os.path.join(OUT, 'fonts', f))


def atlas():
    lines = []
    for page, (size, regions) in ATLAS.items():
        lines += [page, 'size: %d,%d' % size, 'format: RGBA8888', 'filter: Nearest,Nearest', 'repeat: none']
        for name, (x, y, w, h) in regions.items():
            lines += [name, '  bounds: %d,%d,%d,%d' % (x, y, w, h)]
        lines.append('')
    write('gfx/novel.atlas', '\n'.join(lines))


class Canvas:
    def __init__(self):
        self.px = [[(0, 0, 0, 255)] * W for _ in range(H)]

    def tile(self, pattern):
        """Fills the canvas with a background tile."""
        tw, th, tpx = pattern
        for y in range(H):
            row = tpx[y % th]
            self.px[y] = [row[x % tw] for x in range(W)]

    def blit(self, sheet, sx, sy, w, h, dx, dy, scale=1, tint=None):
        _, _, spx = sheet
        for y in range(h * scale):
            ty = dy + y
            if not 0 <= ty < H:
                continue
            for x in range(w * scale):
                tx = dx + x
                if not 0 <= tx < W:
                    continue
                r, g, b, a = spx[sy + y // scale][sx + x // scale]
                if a == 0:
                    continue
                if tint:
                    r, g, b = (int(r * tint[0]), int(g * tint[1]), int(b * tint[2]))
                br, bg, bb, _ = self.px[ty][tx]
                k = a / 255
                self.px[ty][tx] = (int(r * k + br * (1 - k)), int(g * k + bg * (1 - k)), int(b * k + bb * (1 - k)), 255)

    def ground(self, terrain, tiles, rows=3):
        top, soil = tiles
        for col in range(W // 16):
            self.blit(terrain, top[0], top[1], 16, 16, col * 16, H - rows * 16)
            for r in range(1, rows):
                self.blit(terrain, soil[0], soil[1], 16, 16, col * 16, H - (rows - r) * 16)

    def darken(self, factor, blue=1.0):
        self.px = [[(int(r * factor), int(g * factor), min(255, int(b * factor * blue)), a) for (r, g, b, a) in row]
                   for row in self.px]

    def save(self, path):
        full = os.path.join(OUT, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        png.write(full, W, H, self.px)
        print('composed', path)


def backgrounds():
    tile = lambda name: png.read(os.path.join(PACK, 'Background', name + '.png'))
    terrain = png.read(os.path.join(PACK, 'Terrain', 'Terrain (16x16).png'))
    frog = png.read(os.path.join(PACK, 'Ninja Frog', 'Idle (32x32).png'))
    bunny = png.read(os.path.join(PACK, 'Enemies', 'Bunny', 'Idle (34x44).png'))
    cherries = png.read(os.path.join(PACK, 'Items', 'Fruits', 'Cherries.png'))
    c = Canvas()
    c.tile(tile('Blue'))
    c.ground(terrain, GRASS)
    c.save('bg/meadow.png')
    c = Canvas()
    c.tile(tile('Yellow'))
    c.ground(terrain, AUTUMN)
    c.darken(0.85)
    c.save('bg/dusk.png')
    c = Canvas()
    c.tile(tile('Purple'))
    c.ground(terrain, GRASS)
    c.darken(0.45, blue=1.6)
    c.save('bg/night.png')
    c = Canvas()   # the CG: the two friends with the basket of cherries at sunset
    c.tile(tile('Pink'))
    c.ground(terrain, BLOSSOM, rows=4)
    c.blit(frog, 0, 0, 32, 32, 70, H - 64 - 96, scale=3)
    c.blit(bunny, 0, 0, 34, 44, 170, H - 64 - 132, scale=3)
    for i, x in enumerate((140, 152, 146)):
        c.blit(cherries, 0, 0, 32, 32, x, H - 64 - 30 - (8 if i == 2 else 0))
    c.save('cg/sunset.png')


def data():
    write_json('cast.json', {'version': 1, 'characters': {
        'kero': {'name': 'Kero', 'color': '#8BE38B', 'position': 'left',
                 'expressions': {'normal': 'atlas.novel/kero_normal', 'surprised': 'atlas.novel/kero_surprised',
                                 'happy': 'atlas.novel/kero_happy'}, 'default': 'normal'},
        'pip': {'name': 'Pip', 'color': '#FFB3D1', 'position': 'right',
                'expressions': {'normal': 'atlas.novel/pip_normal', 'surprised': 'atlas.novel/pip_surprised',
                                'happy': 'atlas.novel/pip_happy'}, 'default': 'normal'}}})


def input_bindings():
    write_json('input.json', {'version': 1, 'contexts': {
        'gameplay': {'actions': {'menu': ['ESCAPE', 'BACK']}},
        'ui': {
            'actions': {'ui_back': ['ESCAPE', 'BACK'], 'ui_confirm': ['ENTER', 'SPACE']},
            'axes': {'ui_nav': {'left': ['LEFT', 'A'], 'right': ['RIGHT', 'D'], 'up': ['UP', 'W'],
                                'down': ['DOWN', 'S']}}}}})


def manifest():
    entries = [
        {'id': 'atlas.novel', 'path': 'gfx/novel.atlas', 'type': 'atlas', 'group': 'global'},
        {'id': 'font.pixel', 'path': 'fonts/PressStart2P-Regular.ttf', 'type': 'freetype', 'group': 'global',
         'font': {'size': 8, 'mono': True, 'shadowOffsetX': 1, 'shadowOffsetY': 1, 'shadowColor': '#000000A0'}},
        {'id': 'input', 'path': 'input.json', 'type': 'json', 'group': 'global'},
        {'id': 'cast', 'path': 'cast.json', 'type': 'json', 'group': 'global'},
        {'id': 'ink.chapter1', 'path': 'ink/chapter1.ink.json', 'type': 'json', 'group': 'global'},
    ]
    for b in ('meadow', 'dusk', 'night'):
        entries.append({'id': 'bg.' + b, 'path': 'bg/%s.png' % b, 'type': 'texture', 'group': 'global'})
    entries.append({'id': 'cg.sunset', 'path': 'cg/sunset.png', 'type': 'texture', 'group': 'global'})
    for s in ('pop', 'rustle', 'blip'):
        entries.append({'id': 'sfx.' + s, 'path': 'sfx/%s.wav' % s, 'type': 'sound', 'group': 'global'})
    for m in ('meadow', 'sunset'):
        entries.append({'id': 'music.' + m, 'path': 'music/%s.wav' % m, 'type': 'music', 'group': 'global'})
    lines = ',\n'.join('  ' + json.dumps(e) for e in entries)
    write('manifest.json', '{ "version": 1, "assets": [\n' + lines + '\n] }\n')


def credits():
    write('ART-CREDITS.txt', '''Demo art (I-470)

The images in gfx/ (Ninja Frog, Bunny and the cherries) are from the "Pixel Adventure" asset pack by Pixel Frog
(https://pixelfrog-assets.itch.io/pixel-adventure-1), copied unchanged except for file names from the developer's
copy of the pack. The backgrounds in bg/ and the picture in cg/ are composed from the same pack's background tiles,
terrain tiles and characters by assets-src/demo-novel/make_assets.py. Use follows the licence on the pack's page,
which the developer confirmed allows including them in this project (see the platformer demo). gfx/novel.atlas only
names regions of those images.

The sound effects in sfx/ and the music in music/ are original, generated by
assets-src/demo-novel/sound/make_sounds.py. The story in ink/ is original (source in assets-src/demo-novel/ink).

The font in fonts/ is Press Start 2P (SIL Open Font License, see fonts/OFL.txt).
''')


if __name__ == '__main__':
    copy_files()
    atlas()
    backgrounds()
    data()
    input_bindings()
    manifest()
    credits()
