"""Builds the RPG demo's data files (I-450) into demos/demo-rpg/core/assets.

- Copies the used images from the developer's art pack (assets/rpg-rpg, licence confirmed 2026-11-27) and the
  Press Start 2P font from the platformer demo.
- Writes the texture atlas, the animation clips, the tileset, the town and cellar maps, the prefabs, the item
  catalog, the novel cast, the input bindings and the manifest.

    python assets-src/demo-rpg/make_assets.py

After the first run the JSON and TMX files are ordinary data: edit them directly (or in Tiled); run this again only to
start over. Sounds come from sound/make_sounds.py; the Ink stories are compiled by the compileInk Gradle task.
"""
import json
import os
import random
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, '..', '..'))
PACK = os.path.join(REPO, 'assets', 'rpg-rpg')
OUT = os.path.join(REPO, 'demos', 'demo-rpg', 'core', 'assets')
PLATFORMER = os.path.join(REPO, 'demos', 'demo-platformer', 'core', 'assets')

FILES = {   # pack file -> name in the demo
    'full tilemap.png': 'maps/tiles.png',
    'Player/knight_idle_spritesheet.png': 'gfx/knight_idle.png',
    'Player/knight_run_spritesheet.png': 'gfx/knight_run.png',
    'Enemy/Goblin/goblin_idle_spritesheet.png': 'gfx/goblin_idle.png',
    'Enemy/Slime/slime_idle_spritesheet.png': 'gfx/slime_idle.png',
    'Other/door_spritesheet.png': 'gfx/door.png',
}
# The atlas cuts single images out of the tile sheet (a copy next to the atlas) and the first animation frames.
ATLAS_PAGES = {
    'tiles.png': ((144, 128), {'chest': (80, 16, 16, 16), 'key': (80, 0, 16, 16), 'tonic': (112, 0, 16, 16)}),
    'knight_idle.png': ((96, 16), {'knight': (0, 0, 16, 16)}),
    'goblin_idle.png': ((96, 16), {'goblin': (0, 0, 16, 16)}),
    'slime_idle.png': ((96, 16), {'slime': (0, 0, 16, 16)}),
}

# Tile IDs in tiles.png (9 columns of 16 px).
STONE = [42, 43, 42, 51, 43, 53]
FLOOR = [12, 12, 12, 12, 11, 20, 21, 29, 30]
PLANKS = 27
BARREL, SHELF, BANNER_G, BANNER_R, TABLE_L, TABLE_R, BONES, STAIRS = 1, 2, 3, 4, 9, 10, 13, 28
SOLID_TILES = [42, 43, 44, 51, 52, 53, BARREL, SHELF, TABLE_L, TABLE_R]


def write(path, text):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print('wrote', path)


def write_json(path, data):
    write(path, json.dumps(data, indent=1) + '\n')


def copy_files():
    for src, dst in FILES.items():
        full = os.path.join(OUT, dst)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        shutil.copyfile(os.path.join(PACK, src), full)
        print('copied', src, '->', dst)
    shutil.copyfile(os.path.join(PACK, 'full tilemap.png'), os.path.join(OUT, 'gfx', 'tiles.png'))
    for f in ('PressStart2P-Regular.ttf', 'OFL.txt'):
        os.makedirs(os.path.join(OUT, 'fonts'), exist_ok=True)
        shutil.copyfile(os.path.join(PLATFORMER, 'fonts', f), os.path.join(OUT, 'fonts', f))


def atlas():
    lines = []
    for page, (size, regions) in ATLAS_PAGES.items():
        lines += [page, 'size: %d,%d' % size, 'format: RGBA8888', 'filter: Nearest,Nearest', 'repeat: none']
        for name, (x, y, w, h) in regions.items():
            lines += [name, '  bounds: %d,%d,%d,%d' % (x, y, w, h)]
        lines.append('')
    write('gfx/rpg.atlas', '\n'.join(lines))


def clips():
    def clip(sheet, size, first, last, duration=None, mode=None):
        c = {'sheet': sheet, 'frameWidth': size, 'frameHeight': size, 'frames': [first, last]}
        if duration:
            c['frameDuration'] = duration
        if mode:
            c['mode'] = mode
        return c
    write_json('anim/rpg.clips.json', {'version': 1, 'defaults': {'frameDuration': 0.1}, 'clips': {
        'knight_idle': clip('tex.knight_idle', 16, 0, 5),
        'knight_run': clip('tex.knight_run', 16, 0, 5, 0.08),
        'goblin_idle': clip('tex.goblin_idle', 16, 0, 5, 0.12),
        'slime_idle': clip('tex.slime_idle', 16, 0, 5, 0.12),
        'door_closed': clip('tex.door', 32, 0, 0),
        'door_open': clip('tex.door', 32, 0, 13, 0.05, 'once'),
        'door_opened': clip('tex.door', 32, 13, 13)}})


def tileset():
    tiles = ''.join('\n <tile id="%d"><properties><property name="solid" type="bool" value="true"/></properties></tile>'
                    % t for t in SOLID_TILES)
    write('maps/tiles.tsx', '''<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.11.0" name="tiles" tilewidth="16" tileheight="16" tilecount="72" columns="9">
 <image source="tiles.png" width="144" height="128"/>%s
</tileset>
''' % tiles)


class Map:
    def __init__(self, title, width, height, seed, floor=None):
        self.title = title
        self.w = width
        self.h = height
        self.rnd = random.Random(seed)
        self.wall = [[False] * width for _ in range(height)]
        self.floor = [[None] * width for _ in range(height)]
        self.decor = [[0] * width for _ in range(height)]
        self.floor_tiles = floor or FLOOR
        self.objects = []

    def border(self):
        for x in range(self.w):
            for y in (0, 1, self.h - 1):
                self.wall[y][x] = True
        for y in range(self.h):
            for x in (0, self.w - 1):
                self.wall[y][x] = True

    def block(self, x0, y0, w, h, value=True):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.wall[y][x] = value

    def paint(self, x0, y0, w, h, tile):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.floor[y][x] = tile

    def put(self, col, row, tile):
        self.decor[row][col] = tile + 1

    def obj(self, name, type_, col, row, props=None, ref=None, width=None, height=None):
        """An object at the bottom centre of cell (col, row); row 0 is the top. props: plain properties."""
        self.objects.append((name, type_, col, row, props or {}, ref))

    def tmx(self):
        floor, walls, decor = [], [], []
        for y in range(self.h):
            floor.append(','.join(str(0 if self.wall[y][x] else (self.floor[y][x] if self.floor[y][x] is not None
                                                                 else self.rnd.choice(self.floor_tiles)) + 1)
                                  for x in range(self.w)))
            walls.append(','.join(str(self.rnd.choice(STONE) + 1 if self.wall[y][x] else 0) for x in range(self.w)))
            decor.append(','.join(str(self.decor[y][x]) for x in range(self.w)))
        ids = {name: i for i, (name, *_rest) in enumerate(self.objects, start=1)}
        objs = []
        for i, (name, type_, col, row, props, ref) in enumerate(self.objects, start=1):
            x = col * 16 + 8
            y = (row + 1) * 16
            p = ''.join('<property name="%s" value="%s"/>' % (k, v) for k, v in props.items()
                        if not isinstance(v, (int, float)) or isinstance(v, bool))
            p += ''.join('<property name="%s" type="%s" value="%s"/>' % (k, 'int' if isinstance(v, int) else 'float', v)
                         for k, v in props.items() if isinstance(v, (int, float)) and not isinstance(v, bool))
            if ref:
                p += '<property name="%s" type="object" value="%d"/>' % (ref[0], ids[ref[1]])
            body = ('<properties>%s</properties>' % p) if p else ''
            objs.append('  <object id="%d" name="%s" type="%s" x="%g" y="%d">%s<point/></object>'
                        % (i, name, type_, x, y, body))
        layer = lambda lid, name, rows: ''' <layer id="%d" name="%s" width="%d" height="%d">
  <properties><property name="renderLayer" value="floor"/></properties>
  <data encoding="csv">
%s
</data>
 </layer>
''' % (lid, name, self.w, self.h, ',\n'.join(rows))
        return '''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.11.0" orientation="orthogonal" renderorder="right-down" width="%d" height="%d" tilewidth="16" tileheight="16" infinite="0" nextlayerid="5" nextobjectid="%d">
 <properties>
  <property name="title" value="%s"/>
 </properties>
 <tileset firstgid="1" source="tiles.tsx"/>
%s%s%s <objectgroup id="4" name="entities">
%s
 </objectgroup>
</map>
''' % (self.w, self.h, len(self.objects) + 1, self.title, layer(1, 'floor', floor), layer(2, 'walls', walls),
       layer(3, 'decor', decor), '\n'.join(objs))


def town():
    m = Map('Millbrook', 30, 20, 3)
    m.border()
    # The old hall in the north, with the cellar door in a recess of its front wall.
    m.block(9, 2, 12, 6)
    m.block(14, 6, 2, 2, False)
    m.paint(14, 6, 2, 2, PLANKS)
    m.put(12, 7, BANNER_G)
    m.put(17, 7, BANNER_R)
    # Two cottages and the market.
    m.block(2, 3, 5, 4)
    m.block(23, 3, 5, 4)
    m.block(3, 13, 4, 4)
    m.paint(9, 12, 6, 4, PLANKS)   # the elder's porch
    m.put(9, 12, TABLE_L)
    m.put(10, 12, TABLE_R)
    m.put(13, 12, SHELF)
    for (x, y) in ((7, 4), (22, 4), (22, 5), (7, 14), (26, 15), (27, 15), (26, 16), (19, 13)):
        m.put(x, y, BARREL)
    m.obj('player', 'player', 15, 15)
    m.obj('camera', 'camera', 1, 1, ref=('CameraFollow.target', 'player'))
    m.obj('elder', 'npc/elder', 11, 14)
    m.obj('door', 'door', 14.5, 7, {'Door.flag': 'door.cellar'})
    m.obj('to_cellar', 'travel', 14.5, 6, {'Travel.area': 'cellar', 'Travel.spawn': 'from_town'})
    m.obj('start', 'marker', 15, 15)
    m.obj('from_cellar', 'marker', 14.5, 9)
    return m


def cellar():
    m = Map('The cellar', 26, 16, 9)
    m.border()
    for (x, y) in ((8, 5), (8, 10), (16, 5), (16, 10)):
        m.block(x, y, 2, 2)
    m.put(3, 13, STAIRS)
    m.put(12, 2, BONES)
    m.put(21, 3, SHELF)
    m.put(22, 3, SHELF)
    for (x, y) in ((2, 3), (3, 3), (23, 13), (24, 13)):
        m.put(x, y, BARREL)
    m.obj('player', 'player', 5, 13)
    m.obj('camera', 'camera', 1, 1, ref=('CameraFollow.target', 'player'))
    m.obj('chest', 'chest', 20, 5, {'Chest.flag': 'chest.cellar'})
    m.obj('slime', 'npc/slime', 18, 7)
    m.obj('to_town', 'travel', 3, 13, {'Travel.area': 'town', 'Travel.spawn': 'from_cellar'})
    m.obj('from_town', 'marker', 5, 13)
    return m


def prefabs():
    sprite = lambda region, ox, oy, z=1: {'regionId': region, 'origin': [ox, oy], 'z': z}
    feet = {'box': {'x': -0.3, 'y': 0, 'width': 0.6, 'height': 0.5}, 'layer': 1}
    write_json('prefabs/player.json', {'version': 1, 'components': {
        'Sprite': sprite('atlas.rpg/knight', 8, 0, 2), 'Animator': {'clipSet': 'anim.rpg', 'clip': 'knight_idle'},
        'Collider': feet, 'Actor': {}, 'TopDownBody': {'maxSpeed': 4.5}, 'Interactor': {}}})

    def npc(name, region, clip, action):
        write_json('prefabs/npc/%s.json' % name, {'version': 1, 'components': {
            'Sprite': sprite('atlas.rpg/' + region, 8, 0), 'Animator': {'clipSet': 'anim.rpg', 'clip': clip},
            'Collider': {'box': {'x': -0.4, 'y': 0, 'width': 0.8, 'height': 0.6}}, 'Solid': {},
            'Interactable': {'prompt': 'Talk', 'action': action, 'range': 1.5}}})
    npc('elder', 'goblin', 'goblin_idle', 'elder')
    npc('slime', 'slime', 'slime_idle', 'slime')
    write_json('prefabs/door.json', {'version': 1, 'components': {
        'Sprite': sprite('atlas.rpg/chest', 16, 0, 0), 'Animator': {'clipSet': 'anim.rpg', 'clip': 'door_closed'},
        'Collider': {'box': {'x': -1, 'y': 0, 'width': 2, 'height': 1}}, 'Solid': {},
        'Interactable': {'prompt': 'Open', 'action': 'cellar_door', 'range': 1.6}, 'Door': {}}})
    write_json('prefabs/chest.json', {'version': 1, 'components': {
        'Sprite': sprite('atlas.rpg/chest', 8, 0), 'Collider': {'box': {'x': -0.5, 'y': 0, 'width': 1, 'height': 0.8}},
        'Solid': {}, 'Interactable': {'prompt': 'Open', 'action': 'chest', 'range': 1.5}, 'Chest': {}}})
    write_json('prefabs/travel.json', {'version': 1, 'components': {
        'Collider': {'box': {'x': -0.8, 'y': 0.1, 'width': 1.6, 'height': 0.8}, 'trigger': True},
        'TriggerZone': {'tag': 'player'}, 'Travel': {}}})
    write_json('prefabs/marker.json', {'version': 1, 'components': {}})
    write_json('prefabs/camera.json', {'version': 1, 'components': {
        'CameraFollow': {'deadZone': {'x': 0, 'y': 0, 'width': 2, 'height': 1.5}, 'smoothing': 8}}})


def data():
    write_json('items.json', {'version': 1, 'items': {
        'key.cellar': {'name': 'Cellar key', 'icon': 'atlas.rpg/key', 'tags': ['key']},
        'tonic': {'name': 'Tonic', 'icon': 'atlas.rpg/tonic', 'stack': 9, 'tags': ['consumable']}}})
    write_json('cast.json', {'version': 1, 'characters': {
        'knight': {'name': 'Knight', 'color': '#9FD3FF', 'position': 'left',
                   'expressions': {'normal': 'atlas.rpg/knight'}},
        'slime': {'name': 'Slime', 'color': '#8BE38B', 'position': 'right',
                  'expressions': {'normal': 'atlas.rpg/slime'}}}})


def input_bindings():
    write_json('input.json', {'version': 1, 'contexts': {
        'gameplay': {
            'actions': {'interact': ['E', 'SPACE', 'ENTER'], 'pause': ['P'], 'menu': ['ESCAPE', 'BACK']},
            'axes': {'move': {'left': ['A', 'LEFT'], 'right': ['D', 'RIGHT'], 'up': ['W', 'UP'],
                              'down': ['S', 'DOWN']}}},
        'ui': {
            'actions': {'ui_back': ['ESCAPE', 'BACK', 'P'], 'ui_confirm': ['ENTER', 'SPACE', 'E']},
            'axes': {'ui_nav': {'left': ['LEFT', 'A'], 'right': ['RIGHT', 'D'], 'up': ['UP', 'W'],
                                'down': ['DOWN', 'S']}}}},
        'touchControls': {
            'opacity': 0.35,
            'joystick': {'axis': 'move', 'mode': 'floating', 'radius': 52, 'deadZone': 0.2},
            'buttons': [{'action': 'interact', 'label': 'A', 'x': 56, 'y': 64, 'radius': 32},
                        {'action': 'pause', 'label': 'II', 'x': 40, 'y': -40, 'radius': 24}]}})


def manifest():
    entries = [
        {'id': 'atlas.rpg', 'path': 'gfx/rpg.atlas', 'type': 'atlas', 'group': 'global'},
        {'id': 'anim.rpg', 'path': 'anim/rpg.clips.json', 'type': 'clips', 'group': 'global'},
        {'id': 'font.pixel', 'path': 'fonts/PressStart2P-Regular.ttf', 'type': 'freetype', 'group': 'global',
         'font': {'size': 8, 'mono': True, 'shadowOffsetX': 1, 'shadowOffsetY': 1, 'shadowColor': '#000000A0'}},
        {'id': 'input', 'path': 'input.json', 'type': 'json', 'group': 'global'},
        {'id': 'items', 'path': 'items.json', 'type': 'json', 'group': 'global'},
        {'id': 'cast', 'path': 'cast.json', 'type': 'json', 'group': 'global'},
        {'id': 'ink.town', 'path': 'ink/town.ink.json', 'type': 'json', 'group': 'global'},
        {'id': 'ink.cellar', 'path': 'ink/cellar.ink.json', 'type': 'json', 'group': 'global'},
        {'id': 'map.town', 'path': 'maps/town.tmx', 'type': 'map', 'group': 'town'},
        {'id': 'map.cellar', 'path': 'maps/cellar.tmx', 'type': 'map', 'group': 'cellar'},
    ]
    for t in ('knight_idle', 'knight_run', 'goblin_idle', 'slime_idle', 'door'):
        entries.append({'id': 'tex.' + t, 'path': 'gfx/%s.png' % t, 'type': 'texture', 'group': 'global'})
    for p in ('player', 'camera', 'npc/elder', 'npc/slime', 'door', 'chest', 'travel', 'marker'):
        entries.append({'id': p, 'path': 'prefabs/%s.json' % p, 'type': 'prefab', 'group': 'global'})
    for s in ('blip', 'door', 'chest', 'item', 'rumble', 'step'):
        entry = {'id': 'sfx.' + s, 'path': 'sfx/%s.wav' % s, 'type': 'sound', 'group': 'global'}
        if s in ('blip', 'step'):
            entry['maxInstances'] = 2
        entries.append(entry)
    entries.append({'id': 'music.town', 'path': 'music/town.wav', 'type': 'music', 'group': 'global'})
    lines = ',\n'.join('  ' + json.dumps(e) for e in entries)
    write('manifest.json', '{ "version": 1, "assets": [\n' + lines + '\n] }\n')


def credits():
    write('ART-CREDITS.txt', '''Demo art (I-450)

The images in gfx/ (the knight, goblin, slime and door sheets, and tiles.png) and maps/tiles.png are from the
developer's "rpg-rpg" asset pack (assets/rpg-rpg), copied unchanged except for file names. Use follows the pack's
licence, which the developer confirmed on 2026-11-27 allows including them in this project. gfx/rpg.atlas and
anim/rpg.clips.json only name regions of those images.

The sound effects in sfx/ and the music in music/ are original, generated by
assets-src/demo-rpg/sound/make_sounds.py. The stories in ink/ are original (sources in assets-src/demo-rpg/ink).

The font in fonts/ is Press Start 2P (SIL Open Font License, see fonts/OFL.txt).
''')


if __name__ == '__main__':
    copy_files()
    atlas()
    clips()
    tileset()
    write('maps/town.tmx', town().tmx())
    write('maps/cellar.tmx', cellar().tmx())
    prefabs()
    data()
    input_bindings()
    manifest()
    credits()
