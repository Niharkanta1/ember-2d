package dev.ember2d.demo.platformer.spike;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.utils.Array;

/**
 * SPIKE S-02 (throwaway): a minimal kinematic mover. Float positions and float AABBs in world units (1 tile = 1 unit),
 * moved axis by axis in steps of at most half a tile, against a tile grid (solid and one-way tiles) and moving
 * platforms. A small tolerance keeps boxes that only touch a tile edge from counting as overlapping it, which is
 * what prevents snagging on tile seams.
 */
public final class SpikeMover {

    /** Contact tolerance in world units (1/10000 of a tile). */
    static final float EPS = 1e-4f;
    /** Largest movement per collision step, so nothing tunnels through a 1-tile wall. */
    static final float MAX_STEP = 0.5f;

    /** Solid and one-way tiles; row 0 is the bottom row (world y grows upwards). */
    public static final class TileGrid {
        final int width;
        final int height;
        final boolean[] solid;
        final boolean[] oneWay;

        TileGrid(int width, int height) {
            this.width = width;
            this.height = height;
            this.solid = new boolean[width * height];
            this.oneWay = new boolean[width * height];
        }

        /** Builds the grid from a tile layer: tile property {@code solid=true} or {@code oneWay=true}. */
        public static TileGrid from(TiledMap map, String layerName) {
            TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(layerName);
            TileGrid grid = new TileGrid(layer.getWidth(), layer.getHeight());
            for (int x = 0; x < layer.getWidth(); x++) {
                for (int y = 0; y < layer.getHeight(); y++) {
                    TiledMapTileLayer.Cell cell = layer.getCell(x, y);   // libGDX already uses y-up rows
                    if (cell != null && cell.getTile() != null) {
                        grid.solid[y * grid.width + x] = cell.getTile().getProperties().get("solid", false, Boolean.class);
                        grid.oneWay[y * grid.width + x] = cell.getTile().getProperties().get("oneWay", false, Boolean.class);
                    }
                }
            }
            return grid;
        }

        boolean solid(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && solid[y * width + x];
        }

        boolean oneWay(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && oneWay[y * width + x];
        }
    }

    /** A moving box: position is its bottom-left corner. */
    public static final class Actor {
        public float x;
        public float y;
        public final float w;
        public final float h;
        public float vx;
        public float vy;
        public boolean grounded;
        public boolean hitX;
        public int dropThroughTicks;

        public Actor(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /** A solid box that moves and carries the actors standing on it. */
    public static final class Platform {
        public float x;
        public float y;
        public final float w;
        public final float h;

        public Platform(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    final TileGrid grid;
    final Array<Platform> platforms = new Array<>();
    final Array<Actor> actors = new Array<>();

    public SpikeMover(TileGrid grid) {
        this.grid = grid;
    }

    public Actor add(Actor actor) {
        actors.add(actor);
        return actor;
    }

    public Platform add(Platform platform) {
        platforms.add(platform);
        return platform;
    }

    /** Moves an actor; x first, then y. Sets {@code hitX} and {@code grounded}. */
    public void move(Actor a, float dx, float dy) {
        a.hitX = false;
        moveAxis(a, dx, true);
        a.grounded = false;
        moveAxis(a, dy, false);
        if (a.dropThroughTicks > 0) {
            a.dropThroughTicks--;
        }
    }

    void moveAxis(Actor a, float distance, boolean horizontal) {
        float left = distance;
        while (left != 0f) {
            float step = Math.max(-MAX_STEP, Math.min(MAX_STEP, left));
            left -= step;
            if (!(horizontal ? stepX(a, step) : stepY(a, step))) {
                return;
            }
        }
    }

    private boolean stepX(Actor a, float step) {
        float nx = a.x + step;
        int rowBottom = floor(a.y + EPS);
        int rowTop = floor(a.y + a.h - EPS);
        if (step > 0) {
            int col = floor(nx + a.w - EPS);
            for (int row = rowBottom; row <= rowTop; row++) {
                if (grid.solid(col, row)) {
                    a.x = col - a.w;
                    a.hitX = true;
                    return false;
                }
            }
        } else {
            int col = floor(nx + EPS);
            for (int row = rowBottom; row <= rowTop; row++) {
                if (grid.solid(col, row)) {
                    a.x = col + 1;
                    a.hitX = true;
                    return false;
                }
            }
        }
        for (Platform p : platforms) {
            if (overlaps(nx, a.y, a.w, a.h, p)) {
                a.x = step > 0 ? p.x - a.w : p.x + p.w;
                a.hitX = true;
                return false;
            }
        }
        a.x = nx;
        return true;
    }

    private boolean stepY(Actor a, float step) {
        float ny = a.y + step;
        int colLeft = floor(a.x + EPS);
        int colRight = floor(a.x + a.w - EPS);
        if (step < 0) {
            int row = floor(ny + EPS);
            for (int col = colLeft; col <= colRight; col++) {
                boolean landOnOneWay = grid.oneWay(col, row) && a.dropThroughTicks == 0 && a.y >= row + 1 - EPS;
                if (grid.solid(col, row) || landOnOneWay) {
                    a.y = row + 1;
                    a.vy = 0f;
                    a.grounded = true;
                    return false;
                }
            }
        } else {
            int row = floor(ny + a.h - EPS);
            for (int col = colLeft; col <= colRight; col++) {
                if (grid.solid(col, row)) {
                    a.y = row - a.h;
                    a.vy = 0f;
                    return false;
                }
            }
        }
        for (Platform p : platforms) {
            if (overlaps(a.x, ny, a.w, a.h, p)) {
                if (step < 0) {
                    a.y = p.y + p.h;
                    a.grounded = true;
                } else {
                    a.y = p.y - a.h;
                }
                a.vy = 0f;
                return false;
            }
        }
        a.y = ny;
        return true;
    }

    /** Moves a platform; carries actors standing on it and pushes actors in its way. */
    public void movePlatform(Platform p, float dx, float dy) {
        Array<Actor> riders = new Array<>();
        for (Actor a : actors) {
            boolean onTop = Math.abs(a.y - (p.y + p.h)) < 10 * EPS && a.x + a.w > p.x + EPS && a.x < p.x + p.w - EPS;
            if (onTop) {
                riders.add(a);
            }
        }
        platforms.removeValue(p, true);   // riders and pushed actors must not collide with the platform itself
        p.x += dx;
        p.y += dy;
        for (Actor a : riders) {
            moveAxis(a, dx, true);
            moveAxis(a, dy, false);
        }
        for (Actor a : actors) {
            if (!riders.contains(a, true) && overlaps(a.x, a.y, a.w, a.h, p)) {
                if (dx > 0) {
                    moveAxis(a, p.x + p.w - a.x, true);
                } else if (dx < 0) {
                    moveAxis(a, p.x - a.w - a.x, true);
                }
                if (dy > 0) {
                    moveAxis(a, p.y + p.h - a.y, false);
                } else if (dy < 0) {
                    moveAxis(a, p.y - a.h - a.y, false);
                }
            }
        }
        platforms.add(p);
    }

    static boolean overlaps(float x, float y, float w, float h, Platform p) {
        return x + w > p.x + EPS && x < p.x + p.w - EPS && y + h > p.y + EPS && y < p.y + p.h - EPS;
    }

    static int floor(float v) {
        return (int) Math.floor(v);
    }
}
