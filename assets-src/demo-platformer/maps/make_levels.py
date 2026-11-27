"""Writes the platformer demo's levels 2 and 3 (I-250) as ordinary Tiled maps.

The layouts are built from a few helpers (ground blocks, walls, planks, objects) and saved as TMX files that use the
same tileset, prefabs and layers as level 1. After that, Tiled is the editor: run this again only to start over.

    python assets-src/demo-platformer/maps/make_levels.py

Tiles (gid = tileset id + 1): grass top left/middle/right 7/8/9, dirt left/middle/right 29/30/31, one-way plank
left/middle/right 40/41/42.
"""
import os

OUT = os.path.join(os.path.dirname(__file__), '..', '..', '..', 'demos', 'demo-platformer', 'core', 'assets', 'maps')
H = 18
TILE = 16


class Level:
    def __init__(self, title, width):
        self.title = title
        self.width = width
        self.solid = [[False] * width for _ in range(H)]
        self.plank = [[False] * width for _ in range(H)]
        self.objects = []   # (name, type, col, row, extra properties xml)

    def block(self, c0, c1, r0, r1=H - 1):
        """Solid cells in columns c0..c1 and rows r0..r1 (row 0 is the top)."""
        for r in range(r0, r1 + 1):
            for c in range(c0, c1 + 1):
                self.solid[r][c] = True

    def planks(self, c0, c1, r):
        for c in range(c0, c1 + 1):
            self.plank[r][c] = True

    def obj(self, name, type_, col, row, props=''):
        """An object standing in cell (col, row): its point is the bottom centre of the cell (a moving platform: the
        bottom left corner, so its two tiles cover columns col-1 and col)."""
        self.objects.append((name, type_, col, row, props))

    def spikes(self, c0, c1, row, prefix='spikes'):
        for c in range(c0, c1 + 1):
            self.obj('%s_%d' % (prefix, c), 'hazard/spikes', c, row)

    def gid(self, r, c):
        if self.solid[r][c]:
            top = r == 0 or not self.solid[r - 1][c]
            left = c > 0 and not self.solid[r][c - 1]
            right = c < self.width - 1 and not self.solid[r][c + 1]
            base = 7 if top else 29
            return base if left and not right else base + 2 if right and not left else base + 1
        if self.plank[r][c]:
            left = c == 0 or not self.plank[r][c - 1]
            right = c == self.width - 1 or not self.plank[r][c + 1]
            return 40 if left and not right else 42 if right and not left else 41
        return 0

    def tmx(self):
        rows = []
        for r in range(H):
            line = ','.join(str(self.gid(r, c)) for c in range(self.width))
            rows.append(line + (',' if r < H - 1 else ''))
        objects = []
        player_id = None
        for i, (name, type_, col, row, props) in enumerate(self.objects, start=1):
            if type_ == 'player':
                player_id = i
        for i, (name, type_, col, row, props) in enumerate(self.objects, start=1):
            x = col * TILE + TILE // 2
            if type_ == 'platform/moving':
                x = col * TILE   # 2 tiles wide, centred on the cell's left edge: it covers columns col-1 and col
            y = (row + 1) * TILE
            if type_ == 'camera':
                x, y = 0, 0
                props = '<properties><property name="target" type="object" value="%d"/></properties>' % player_id
            if props:
                objects.append('  <object id="%d" name="%s" type="%s" x="%d" y="%d">\n   %s\n   <point/>\n  </object>'
                               % (i, name, type_, x, y, props))
            else:
                objects.append('  <object id="%d" name="%s" type="%s" x="%d" y="%d"><point/></object>'
                               % (i, name, type_, x, y))
        return '''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.11.0" orientation="orthogonal" renderorder="right-down" width="%d" height="%d" tilewidth="16" tileheight="16" infinite="0" nextlayerid="4" nextobjectid="%d">
 <properties>
  <property name="title" value="%s"/>
 </properties>
 <tileset firstgid="1" source="terrain.tsx"/>
 <imagelayer id="1" name="sky" parallaxx="0.3" parallaxy="0.3" repeatx="1" repeaty="1">
  <properties><property name="renderLayer" value="background"/></properties>
  <image source="background.png" width="64" height="64"/>
 </imagelayer>
 <layer id="2" name="ground" width="%d" height="%d">
  <data encoding="csv">
%s
</data>
 </layer>
 <objectgroup id="3" name="entities">
%s
 </objectgroup>
</map>
''' % (self.width, H, len(self.objects) + 1, self.title, self.width, H, '\n'.join(rows), '\n'.join(objects))


def body(**fields):
    """A class-typed PlatformerBody property, saved the way Tiled saves an edited class member (I-245)."""
    members = ''.join('<property name="%s" type="%s" value="%s"/>' % (
        k, 'bool' if isinstance(v, bool) else 'int', str(v).lower()) for k, v in fields.items())
    return ('<properties><property name="PlatformerBody" type="class" propertytype="PlatformerBody">'
            '<properties>%s</properties></property></properties>' % members)


def sign(text):
    return '<properties><property name="Text.text">%s</property></properties>' % text


def level2():
    """Cliffs: wall slide and wall jump, a vertical moving platform."""
    lv = Level('Level 2', 110)
    lv.block(0, 1, 0)                     # left edge
    lv.block(2, 20, 14)                   # start
    lv.block(21, 28, 12)                  # a step up
    lv.block(29, 31, 17)                  # spike pit
    lv.spikes(29, 31, 16, 'pit')
    lv.block(32, 50, 14)                  # floor under the climb
    lv.block(45, 46, 5, 11)               # hanging wall: the left side of the shaft
    lv.block(50, 66, 4)                   # cliff: the right side of the shaft, and the plateau on top
    lv.block(67, 78, 10)                  # a step down
    lv.block(79, 80, 15)                  # the lift's shaft floor
    lv.block(81, 95, 3)                   # high plateau
    lv.block(96, 109, 14)                 # goal area
    lv.block(108, 109, 0)                 # right edge
    lv.obj('player', 'player', 4, 13, body(wallSlide=True, wallJump=True))
    lv.obj('start', 'level/start', 8, 13, sign('Jump at a wall,\nthen jump again!'))
    lv.obj('pig_1', 'enemy/pig', 16, 13)
    lv.obj('pig_2', 'enemy/pig', 36, 13)
    lv.obj('checkpoint', 'level/checkpoint', 56, 3)
    lv.obj('pig_3', 'enemy/pig', 62, 3)
    lv.obj('pig_4', 'enemy/pig', 73, 9)
    lv.obj('lift', 'platform/moving', 80, 14,
           '<properties><property name="MovingPlatform" type="class" propertytype="MovingPlatform"><properties>'
           '<property name="vertical" type="bool" value="true"/><property name="distance" type="float" value="10"/>'
           '<property name="speed" type="float" value="2"/></properties></property></properties>')
    lv.spikes(100, 102, 13)
    lv.obj('pig_5', 'enemy/pig', 90, 2)
    lv.obj('goal', 'level/goal', 105, 13)
    lv.obj('camera', 'camera', 0, 0)
    return lv


def level3():
    """Sky: air jump and dash over spike pits."""
    lv = Level('Level 3', 120)
    lv.block(0, 1, 0)
    lv.block(2, 14, 14)
    lv.block(15, 20, 17)
    lv.spikes(15, 20, 16, 'pit_a')
    lv.block(21, 28, 14)
    lv.block(29, 34, 17)
    lv.spikes(29, 34, 16, 'pit_b')
    lv.block(35, 40, 12)
    lv.block(41, 48, 9)
    lv.block(49, 56, 14)
    lv.block(57, 70, 17)
    lv.spikes(57, 70, 16, 'pit_c')
    lv.block(71, 80, 13)
    lv.block(81, 86, 17)
    lv.spikes(81, 86, 16, 'pit_d')
    lv.block(87, 95, 11)
    lv.planks(97, 99, 9)
    lv.block(96, 117, 14)
    lv.spikes(99, 104, 13)
    lv.block(118, 119, 0)
    lv.obj('player', 'player', 4, 13, body(airJumps=1, dash=True))
    lv.obj('start', 'level/start', 8, 13, sign('Jump again in the air.\nX or Shift dashes!'))
    lv.obj('pig_1', 'enemy/pig', 25, 13)
    lv.obj('pig_2', 'enemy/pig', 38, 11)
    lv.obj('checkpoint', 'level/checkpoint', 45, 8)
    lv.obj('pig_3', 'enemy/pig', 53, 13)
    lv.obj('platform', 'platform/moving', 59, 12,
           '<properties><property name="MovingPlatform" type="class" propertytype="MovingPlatform"><properties>'
           '<property name="distance" type="float" value="9"/></properties></property></properties>')
    lv.obj('pig_4', 'enemy/pig', 76, 12)
    lv.obj('pig_5', 'enemy/pig', 91, 10)
    lv.obj('goal', 'level/goal', 112, 13)
    lv.obj('camera', 'camera', 0, 0)
    return lv


if __name__ == '__main__':
    for name, lv in (('level2.tmx', level2()), ('level3.tmx', level3())):
        path = os.path.normpath(os.path.join(OUT, name))
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(lv.tmx())
        print('wrote', path)
