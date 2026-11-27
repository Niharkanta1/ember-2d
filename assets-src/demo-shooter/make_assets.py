"""Builds the shooter demo's data files (I-340) into demos/demo-shooter/core/assets.

- Copies the used sheets from the developer's art pack (assets/shooter-tiny, licence confirmed 2026-11-27) and the
  Press Start 2P font from the platformer demo.
- Writes the texture atlas that cuts the sheets into regions, the dungeon tileset, the two maps, the prefabs, the
  bullet types, the bullet patterns, the weapon, the input bindings and the manifest.

    python assets-src/demo-shooter/make_assets.py

After the first run the JSON and TMX files are ordinary data: edit them directly (or in Tiled); run this again only to
start over. Sounds come from make_sounds.py.
"""
import json
import os
import random
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, '..', '..'))
PACK = os.path.join(REPO, 'assets', 'shooter-tiny')
OUT = os.path.join(REPO, 'demos', 'demo-shooter', 'core', 'assets')
PLATFORMER = os.path.join(REPO, 'demos', 'demo-platformer', 'core', 'assets')

SHEETS = {   # pack file -> name in the demo
    'Characters/Characters.png': 'gfx/characters.png',
    'Characters/Guns.png': 'gfx/guns.png',
    'Characters/Player_Bullets.png': 'gfx/player_bullets.png',
    'Enemies/Enemies.png': 'gfx/enemies.png',
    'Enemies/Enemy Bullets.png': 'gfx/enemy_bullets.png',
    'Enemies/BossEye.png': 'gfx/boss.png',
    'Enemies/Splatter.png': 'gfx/splatter.png',
    'Pickups/Pickups.png': 'gfx/pickups.png',
    'Map/full tilemap.png': 'maps/dungeon.png',
}

# atlas page -> (size, {region: (x, y, w, h)})
ATLAS = {
    'characters.png': ((64, 64), {'player': (0, 0, 16, 16)}),
    'guns.png': ((64, 128), {'gun.smg': (2, 106, 21, 20)}),
    'player_bullets.png': ((32, 32), {'bullet.player': (4, 10, 10, 5)}),
    'enemy_bullets.png': ((16, 16), {'orb.magenta': (0, 0, 8, 8), 'orb.red': (8, 0, 8, 8), 'orb.blue': (0, 8, 8, 8),
                                     'orb.green': (8, 8, 8, 8)}),
    'enemies.png': ((64, 64), {'skeleton': (16, 0, 16, 16), 'flame': (32, 0, 16, 16), 'slime': (48, 0, 16, 16)}),
    'boss.png': ((64, 64), {'boss': (0, 0, 48, 48), 'boss.shadow': (0, 52, 48, 10)}),
    'splatter.png': ((64, 64), {'splat': (0, 0, 16, 16), 'splat2': (16, 0, 16, 16)}),
    'pickups.png': ((64, 64), {'coin': (0, 0, 16, 16), 'heart': (16, 0, 16, 16)}),
}

SOLID_TILES = [42, 43, 44, 51, 52, 53]   # stone bricks (tileset ids)
FLOOR = [12, 12, 12, 12, 11, 20, 21, 29, 30]
WALL = [42, 43, 42, 51, 43, 53]


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
    write('gfx/shooter.atlas', '\n'.join(lines))


def tileset():
    tiles = ''.join('\n <tile id="%d"><properties><property name="solid" type="bool" value="true"/></properties></tile>'
                    % t for t in SOLID_TILES)
    write('maps/dungeon.tsx', '''<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.11.0" name="dungeon" tilewidth="16" tileheight="16" tilecount="72" columns="9">
 <image source="dungeon.png" width="144" height="128"/>%s
</tileset>
''' % tiles)


class Map:
    def __init__(self, title, width, height, seed):
        self.title = title
        self.w = width
        self.h = height
        self.rnd = random.Random(seed)
        self.wall = [[False] * width for _ in range(height)]
        self.objects = []

    def border(self):
        for x in range(self.w):
            for y in (0, 1, self.h - 1):
                self.wall[y][x] = True
        for y in range(self.h):
            for x in (0, self.w - 1):
                self.wall[y][x] = True

    def block(self, x0, y0, w, h):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.wall[y][x] = True

    def obj(self, name, type_, col, row, props=None, ref=None):
        """An object at the bottom centre of cell (col, row); row 0 is the top. props: plain properties."""
        self.objects.append((name, type_, col, row, props or {}, ref))

    def tmx(self):
        floor = []
        walls = []
        for y in range(self.h):
            floor.append(','.join(str(0 if self.wall[y][x] else self.rnd.choice(FLOOR) + 1) for x in range(self.w)))
            walls.append(','.join(str(self.rnd.choice(WALL) + 1 if self.wall[y][x] else 0) for x in range(self.w)))
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
            objs.append('  <object id="%d" name="%s" type="%s" x="%d" y="%d">%s<point/></object>'
                        % (i, name, type_, x, y, body))
        rows_f = ',\n'.join(floor)
        rows_w = ',\n'.join(walls)
        return '''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.11.0" orientation="orthogonal" renderorder="right-down" width="%d" height="%d" tilewidth="16" tileheight="16" infinite="0" nextlayerid="4" nextobjectid="%d">
 <properties>
  <property name="title" value="%s"/>
 </properties>
 <tileset firstgid="1" source="dungeon.tsx"/>
 <layer id="1" name="floor" width="%d" height="%d">
  <properties><property name="renderLayer" value="floor"/></properties>
  <data encoding="csv">
%s
</data>
 </layer>
 <layer id="2" name="walls" width="%d" height="%d">
  <properties><property name="renderLayer" value="floor"/></properties>
  <data encoding="csv">
%s
</data>
 </layer>
 <objectgroup id="3" name="entities">
%s
 </objectgroup>
</map>
''' % (self.w, self.h, len(self.objects) + 1, self.title, self.w, self.h, rows_f, self.w, self.h, rows_w,
       '\n'.join(objs))


def room():
    m = Map('The crypt', 34, 22, 7)
    m.border()
    for (x, y) in ((8, 6), (24, 6), (8, 15), (24, 15), (16, 10)):
        m.block(x, y, 2, 2)
    m.obj('player', 'player', 17, 18)
    m.obj('camera', 'camera', 1, 1, ref=('CameraFollow.target', 'player'))
    m.obj('exit', 'level/exit', 17, 2)
    waves = [   # name, cell, prefab, delay, total, interval
        ('wave1_a', (3, 4), 'enemy/slime', 1.0, 3, 1.5), ('wave1_b', (30, 4), 'enemy/slime', 1.5, 3, 1.5),
        ('wave2_a', (3, 19), 'enemy/skeleton', 12.0, 2, 2.5), ('wave2_b', (30, 19), 'enemy/skeleton', 12.5, 2, 2.5),
        ('wave3_a', (16, 4), 'enemy/slime', 24.0, 4, 1.0), ('wave3_b', (3, 11), 'enemy/skeleton', 25.0, 2, 3.0),
        ('wave3_c', (30, 11), 'enemy/flame', 25.5, 3, 2.0),
    ]
    for name, (x, y), prefab, delay, total, interval in waves:
        m.obj(name, 'spawner', x, y, {'Spawner.prefab': prefab, 'Spawner.delay': delay, 'Spawner.total': total,
                                      'Spawner.interval': interval, 'Spawner.maxAlive': total})
    return m


def boss():
    m = Map('The eye', 24, 15, 11)
    m.border()
    m.obj('player', 'player', 12, 12)
    m.obj('camera', 'camera', 1, 1, ref=('CameraFollow.target', 'player'))
    m.obj('boss', 'boss/eye', 12, 7)
    return m


def prefabs():
    sprite = lambda region, ox, oy, z=1: {'regionId': 'atlas.shooter/' + region, 'origin': [ox, oy], 'z': z}
    # Actors are on collision layer 1: wall bullets raycast layer 0 only (tiles), so actors never count as walls.
    feet = {'box': {'x': -0.35, 'y': 0, 'width': 0.7, 'height': 0.6}, 'layer': 1}
    write_json('prefabs/player.json', {'version': 1, 'components': {
        'Sprite': sprite('player', 8, 0, 2), 'Collider': feet, 'Actor': {},
        'TopDownBody': {'maxSpeed': 6}, 'Aim': {}, 'Weapon': {'weapon': 'weapon.smg', 'fireAction': 'fire',
                                                              'reloadAction': 'reload'},
        'Health': {'max': 5, 'invulnerableSeconds': 1.0}, 'Team': {'id': 1},
        'BulletTarget': {'radius': 0.18, 'grazeRadius': 0.9, 'offsetY': 0.45}, 'Hurtbox': {}}})

    def enemy(name, region, speed, hp, score, extra=None):
        c = {'Sprite': sprite(region, 8, 0), 'Collider': feet, 'Actor': {}, 'TopDownBody': {'maxSpeed': speed},
             'Steering': {'mode': 'FOLLOW_PATH', 'sight': 30}, 'Health': {'max': hp, 'invulnerableSeconds': 0.05},
             'Team': {'id': 2}, 'BulletTarget': {'radius': 0.45, 'offsetY': 0.45},
             'Hitbox': {'box': {'x': -0.4, 'y': 0, 'width': 0.8, 'height': 0.8}, 'damage': 1, 'knockback': 8},
             'Hurtbox': {}, 'Enemy': {'score': score}}
        c.update(extra or {})
        write_json('prefabs/enemy/%s.json' % name, {'version': 1, 'components': c})

    enemy('slime', 'slime', 2.6, 3, 10)
    enemy('skeleton', 'skeleton', 1.8, 4, 25, {'Gunner': {'pattern': 'pattern.skeleton', 'interval': 2.5}})
    enemy('flame', 'flame', 3.4, 2, 15)
    write_json('prefabs/boss/eye.json', {'version': 1, 'components': {
        'Sprite': sprite('boss', 24, 0, 1), 'Health': {'max': 300, 'invulnerableSeconds': 0}, 'Team': {'id': 2},
        'BulletTarget': {'radius': 1.2, 'offsetY': 1.5},
        'Hitbox': {'box': {'x': -1.2, 'y': 0.3, 'width': 2.4, 'height': 2.4}, 'damage': 1, 'knockback': 10},
        'Boss': {}}})
    write_json('prefabs/level/exit.json', {'version': 1, 'components': {
        'Sprite': {'regionId': 'atlas.shooter/coin', 'origin': [8, 0], 'z': 0},
        'Collider': {'box': {'x': -1, 'y': 0, 'width': 2, 'height': 1}, 'trigger': True},
        'TriggerZone': {'tag': 'player'}, 'Exit': {}}})
    write_json('prefabs/spawner.json', {'version': 1, 'components': {'Spawner': {}}})
    write_json('prefabs/camera.json', {'version': 1, 'components': {
        'CameraFollow': {'deadZone': {'x': 0, 'y': 0, 'width': 3, 'height': 2}, 'smoothing': 8}}})
    pickup = lambda region, score, heal: {'version': 1, 'components': {
        'Sprite': sprite(region, 8, 0, 0), 'Collider': {'box': {'x': -0.4, 'y': 0, 'width': 0.8, 'height': 0.8},
                                                        'trigger': True},
        'TriggerZone': {'tag': 'player', 'once': True}, 'Pickup': {'score': score, 'heal': heal},
        'Lifetime': {'seconds': 10}}}
    write_json('prefabs/pickup/coin.json', pickup('coin', 5, 0))
    write_json('prefabs/pickup/heart.json', pickup('heart', 0, 1))
    write_json('prefabs/fx/splat.json', {'version': 1, 'components': {
        'Sprite': sprite('splat', 8, 4, 0), 'Lifetime': {'seconds': 0.8}}})


def bullets():
    a = 'atlas.shooter/'
    write_json('bullets.json', {'version': 1, 'types': {
        'player': {'region': a + 'bullet.player', 'radius': 0.12, 'damage': 1, 'lifetime': 1.5, 'walls': True,
                   'rotate': True},
        'pellet': {'region': a + 'orb.red', 'radius': 0.16, 'lifetime': 6, 'walls': True},
        'orb_magenta': {'region': a + 'orb.magenta', 'radius': 0.16, 'lifetime': 9},
        'orb_red': {'region': a + 'orb.red', 'radius': 0.16, 'lifetime': 9},
        'orb_blue': {'region': a + 'orb.blue', 'radius': 0.16, 'lifetime': 9},
        'orb_green': {'region': a + 'orb.green', 'radius': 0.16, 'lifetime': 9},
        'stress': {'region': a + 'orb.blue', 'radius': 0.16, 'lifetime': 6, 'graze': False}}})
    write_json('patterns/skeleton.json', {'version': 1, 'emit': {
        'type': 'aimed', 'bullet': 'pellet', 'count': 3, 'spread': 30, 'speed': 5}})
    write_json('patterns/boss1.json', {'version': 1, 'emit': [
        {'type': 'spiral', 'bullet': 'orb_magenta', 'count': 4, 'speed': 3.5, 'repeat': 80, 'interval': 0.12,
         'angleStep': 13},
        {'type': 'aimed', 'bullet': 'orb_red', 'count': 5, 'spread': 40, 'speed': 5, 'repeat': 6, 'interval': 1.5,
         'delay': 0.6}]})
    write_json('patterns/boss2.json', {'version': 1, 'emit': [
        {'type': 'ring', 'bullet': 'orb_blue', 'count': 24, 'speed': 3, 'repeat': 6, 'interval': 1.6},
        {'type': 'wave', 'bullet': 'orb_green', 'count': 3, 'spread': 60, 'speed': 4, 'repeat': 50, 'interval': 0.18,
         'amplitude': 35, 'frequency': 12}]})
    write_json('patterns/boss3.json', {'version': 1, 'emit': [
        {'type': 'random', 'bullet': 'orb_red', 'count': 6, 'spread': 140, 'speed': 4, 'repeat': 60,
         'interval': 0.15},
        {'type': 'aimed', 'bullet': 'orb_magenta', 'speed': 2.5, 'repeat': 6, 'interval': 1.4, 'delay': 0.5,
         'child': {'type': 'ring', 'bullet': 'orb_blue', 'count': 16, 'speed': 3}, 'childAfter': 1.2}]})
    write_json('patterns/stress.json', {'version': 1, 'emit': {
        'type': 'spiral', 'bullet': 'stress', 'count': 30, 'speed': 1.2, 'repeat': 100000, 'interval': 0.06,
        'angleStep': 7}})
    write_json('weapons/smg.json', {'version': 1, 'fireRate': 10, 'auto': True, 'magazine': 24, 'reload': 1.0,
                                    'muzzle': 0.6, 'emit': {'type': 'random', 'bullet': 'player', 'spread': 6,
                                                            'speed': 14}})


def input_bindings():
    write_json('input.json', {'version': 1, 'contexts': {
        'gameplay': {
            'actions': {'fire': ['MOUSE_LEFT', 'SPACE'], 'reload': ['R'], 'pause': ['P'],
                        'menu': ['ESCAPE', 'BACK']},
            'axes': {'move': {'left': ['A'], 'right': ['D'], 'up': ['W'], 'down': ['S']},
                     'aim': {'left': ['LEFT'], 'right': ['RIGHT'], 'up': ['UP'], 'down': ['DOWN']}}},
        'ui': {
            'actions': {'ui_back': ['ESCAPE', 'BACK', 'P'], 'ui_confirm': ['ENTER', 'SPACE']},
            'axes': {'ui_nav': {'left': ['LEFT', 'A'], 'right': ['RIGHT', 'D'], 'up': ['UP', 'W'],
                                'down': ['DOWN', 'S']}}}},
        'touchControls': {
            'opacity': 0.35,
            'joystick': {'axis': 'move', 'mode': 'floating', 'radius': 52, 'deadZone': 0.2},
            'aimStick': {'axis': 'aim', 'x': 150, 'y': 110, 'radius': 52, 'deadZone': 0.15, 'fire': 'fire',
                         'fireAt': 0.5},
            'buttons': [{'action': 'reload', 'label': 'R', 'x': 48, 'y': 150, 'radius': 28},
                        {'action': 'pause', 'label': 'II', 'x': 48, 'y': -48, 'radius': 24}]}})


def manifest():
    entries = [
        {'id': 'atlas.shooter', 'path': 'gfx/shooter.atlas', 'type': 'atlas', 'group': 'global'},
        {'id': 'font.pixel', 'path': 'fonts/PressStart2P-Regular.ttf', 'type': 'freetype', 'group': 'global',
         'font': {'size': 8, 'mono': True, 'shadowOffsetX': 1, 'shadowOffsetY': 1, 'shadowColor': '#000000A0'}},
        {'id': 'input', 'path': 'input.json', 'type': 'json', 'group': 'global'},
        {'id': 'bullets', 'path': 'bullets.json', 'type': 'json', 'group': 'global'},
        {'id': 'weapon.smg', 'path': 'weapons/smg.json', 'type': 'json', 'group': 'global'},
        {'id': 'map.room', 'path': 'maps/room.tmx', 'type': 'map', 'group': 'room'},
        {'id': 'map.boss', 'path': 'maps/boss.tmx', 'type': 'map', 'group': 'boss'},
    ]
    for p in ('skeleton', 'boss1', 'boss2', 'boss3', 'stress'):
        entries.append({'id': 'pattern.' + p, 'path': 'patterns/%s.json' % p, 'type': 'json', 'group': 'global'})
    for p in ('player', 'camera', 'spawner', 'enemy/slime', 'enemy/skeleton', 'enemy/flame', 'boss/eye',
              'level/exit', 'pickup/coin', 'pickup/heart', 'fx/splat'):
        entries.append({'id': p, 'path': 'prefabs/%s.json' % p, 'type': 'prefab', 'group': 'global'})
    for s in ('shoot', 'hit', 'kill', 'hurt', 'pickup', 'reload', 'boss_phase', 'graze'):
        entry = {'id': 'sfx.' + s, 'path': 'sfx/%s.wav' % s, 'type': 'sound', 'group': 'global'}
        if s in ('shoot', 'hit', 'graze'):
            entry['maxInstances'] = 3
        entries.append(entry)
    entries.append({'id': 'music.boss', 'path': 'music/boss.wav', 'type': 'music', 'group': 'global'})
    lines = ',\n'.join('  ' + json.dumps(e) for e in entries)
    write('manifest.json', '{ "version": 1, "assets": [\n' + lines + '\n] }\n')


def credits():
    write('ART-CREDITS.txt', '''Demo art (I-340)

The images in gfx/ (characters, guns, bullets, enemies, the eye boss, splatter, pickups) and maps/dungeon.png are
from the developer's "shooter-tiny" asset pack (assets/shooter-tiny), copied unchanged except for file names.
Use follows the pack's licence, which the developer confirmed on 2026-11-27 allows including them in this project.
gfx/shooter.atlas only names regions of those images.

The sound effects in sfx/ and the music in music/ are original, generated by
assets-src/demo-shooter/sound/make_sounds.py.

The font in fonts/ is Press Start 2P (SIL Open Font License, see fonts/OFL.txt).
''')


if __name__ == '__main__':
    copy_files()
    atlas()
    tileset()
    write('maps/room.tmx', room().tmx())
    write('maps/boss.tmx', boss().tmx())
    prefabs()
    bullets()
    input_bindings()
    manifest()
    credits()
