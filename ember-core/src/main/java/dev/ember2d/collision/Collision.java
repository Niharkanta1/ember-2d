package dev.ember2d.collision;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import java.util.Objects;

/**
 * Kinematic collision for one world (F-006, ADR-005, spike S-02). Create one in a scene's {@code enter}, add the
 * map's tile grids, and register a {@link CollisionSystem} after the game's movement systems.
 *
 * <p>Moves sweep each axis against solid tiles and solid boxes and stop at exact contact, so nothing tunnels and
 * nothing snags on tile seams. A contact tolerance of {@link #EPS} keeps boxes that only touch from counting as
 * overlapping. Actors never block each other. One-way tiles and solids block only actors coming from above that are
 * not dropping through. Nothing is allocated per move or query in steady state.
 */
@PublicApi
public final class Collision {

    /** Contact tolerance in world units. */
    public static final float EPS = 1e-4f;
    private static final float PROBE = 2 * EPS;

    private final World world;
    private final EventBus events;
    private final Family colliders;
    private final Array<TileGrid> grids = new Array<>(false, 2, TileGrid[]::new);
    private final IntArray gridLayers = new IntArray();
    private final MoveResult result = new MoveResult();
    private final IntArray candidates = new IntArray();
    private final IntArray riders = new IntArray();
    private final IntArray pushed = new IntArray();
    private final IntArray removed = new IntArray();
    private final IntArray now = new IntArray();
    private final IntSet squished = new IntSet();
    private final IntMap<IntArray> insideByTrigger = new IntMap<>();
    private final FloatArray distances = new FloatArray();
    private final Rectangle box = new Rectangle();
    private final Rectangle other = new Rectangle();
    private final Rectangle indexBox = new Rectangle();
    private float cellSize = 2f;
    private SpatialHash solids;   // solid boxes: all that moves are swept against
    private SpatialHash bodies;   // actors, triggers and other colliders
    private final IntArray solidHits = new IntArray();
    private final IntArray bodyHits = new IntArray();
    private int stamp;
    private int ignore = World.NONE;
    // outputs of the last sweep
    private boolean sweepHit;
    private int sweepEntity;

    public Collision(World world, EventBus events) {
        this.world = Objects.requireNonNull(world, "world");
        this.events = Objects.requireNonNull(events, "events");
        this.colliders = world.family(Transform.class, Collider.class);
    }

    /**
     * Sets the spatial hash cell size in world units (default 2); only before the first move or query.
     *
     * @throws IllegalStateException after first use
     */
    public void setCellSize(float size) {
        if (solids != null) {
            throw new IllegalStateException("Set the cell size before the first move or query");
        }
        if (!(size > 0f)) {
            throw new IllegalArgumentException("Cell size must be > 0 (was " + size + ")");
        }
        cellSize = size;
    }

    /** Adds a tile grid (row 0 at the bottom, origin at world (0, 0)) on a collision layer (bit index 0-31). */
    public void addTileGrid(TileGrid grid, int layer) {
        grids.add(Objects.requireNonNull(grid, "grid"));
        gridLayers.add(checkLayer(layer));
    }

    // ------------------------------------------------------------------------------------------------
    // Moves
    // ------------------------------------------------------------------------------------------------

    /**
     * Moves an actor x first, then y; a blocked axis stops at exact contact while the other one continues (the
     * platformer move). Returns the shared result, valid until the next move; the contacts are also stored on the
     * {@link Actor}.
     */
    public MoveResult moveAndSlide(int actor, float dx, float dy) {
        ensureBuilt();
        Transform t = transform(actor);
        Collider c = actorCollider(actor);
        Actor a = world.get(actor, Actor.class);
        result.reset();
        stepX(actor, t, c, a, dx);
        stepY(actor, t, c, a, dy);
        return finishMove(actor, t, c, a);
    }

    /**
     * Moves an actor along (dx, dy) and stops entirely at the first contact on either axis (projectiles, dashes).
     * Returns the shared result, valid until the next move.
     */
    public MoveResult moveAndCollide(int actor, float dx, float dy) {
        ensureBuilt();
        Transform t = transform(actor);
        Collider c = actorCollider(actor);
        Actor a = world.get(actor, Actor.class);
        result.reset();
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / maxStep()));
        float sx = dx / steps;
        float sy = dy / steps;
        for (int i = 0; i < steps && !result.hitX && !result.hitY; i++) {
            stepX(actor, t, c, a, sx);
            if (!result.hitX) {
                stepY(actor, t, c, a, sy);
            }
        }
        return finishMove(actor, t, c, a);
    }

    /** True if something blocks the actor within {@code 2 * EPS} in that direction ({@code DOWN}: grounded). */
    public boolean isTouching(int actor, Direction direction) {
        ensureBuilt();
        Transform t = transform(actor);
        Collider c = actorCollider(actor);
        boolean dropping = world.get(actor, Actor.class).dropThroughTicks > 0;
        float x = t.position.x + c.box.x;
        float y = t.position.y + c.box.y;
        switch (direction) {
            case LEFT -> sweepX(actor, x, y, c.box.width, c.box.height, -PROBE, c.mask);
            case RIGHT -> sweepX(actor, x, y, c.box.width, c.box.height, PROBE, c.mask);
            case UP -> sweepY(actor, x, y, c.box.width, c.box.height, PROBE, c.mask, dropping);
            case DOWN -> sweepY(actor, x, y, c.box.width, c.box.height, -PROBE, c.mask, dropping);
        }
        return sweepHit;
    }

    /**
     * Moves a solid. Actors standing on it move with it; actors in its way are pushed (one-way solids only carry).
     * An actor that cannot be pushed clear stays where it is and an {@link ActorSquished} event is queued (once per
     * call).
     */
    public void moveSolid(int solid, float dx, float dy) {
        ensureBuilt();
        Transform t = transform(solid);
        Collider c = world.get(solid, Collider.class);
        Solid s = world.get(solid, Solid.class);
        if (c == null || s == null) {
            throw new IllegalArgumentException("Entity " + solid + " is not a solid: it needs Transform, Collider and "
                    + "Solid");
        }
        squished.clear();
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / maxStep()));
        for (int i = 0; i < steps; i++) {
            moveSolidAxis(solid, t, c, s, dx / steps, true);
            moveSolidAxis(solid, t, c, s, dy / steps, false);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------

    /** Colliders (triggers included) overlapping the area whose layer is in the mask, sorted by entity. */
    public int overlapBox(Rectangle area, int mask, IntArray out) {
        ensureBuilt();
        queryAll(area.x, area.y, area.width, area.height, candidates);
        out.clear();
        for (int i = 0; i < candidates.size; i++) {
            int e = candidates.get(i);
            if (inMask(mask, e) && bounds(e, other) != null && overlaps(area, other)) {
                out.add(e);
            }
        }
        return out.size;
    }

    /** Colliders (triggers included) containing the point whose layer is in the mask, sorted by entity. */
    public int overlapPoint(float x, float y, int mask, IntArray out) {
        ensureBuilt();
        queryAll(x, y, 0f, 0f, candidates);
        out.clear();
        for (int i = 0; i < candidates.size; i++) {
            int e = candidates.get(i);
            if (inMask(mask, e) && bounds(e, other) != null && other.contains(x, y)) {
                out.add(e);
            }
        }
        return out.size;
    }

    /** The collider (triggers included) nearest to the point within the radius, or {@link World#NONE}. */
    public int nearest(float x, float y, float radius, int mask) {
        ensureBuilt();
        queryAll(x - radius, y - radius, 2 * radius, 2 * radius, candidates);
        int best = World.NONE;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < candidates.size; i++) {
            int e = candidates.get(i);
            if (!inMask(mask, e) || bounds(e, other) == null) {
                continue;
            }
            float ddx = Math.max(Math.max(other.x - x, 0f), x - (other.x + other.width));
            float ddy = Math.max(Math.max(other.y - y, 0f), y - (other.y + other.height));
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (d <= radius && d < bestDistance) {
                bestDistance = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * The first solid tile or non-trigger collider (layer in the mask) on the segment; one-way tiles and solids do
     * not stop rays. Returns true on a hit and fills {@code out}.
     */
    public boolean raycast(float fromX, float fromY, float toX, float toY, int mask, RayHit out) {
        ensureBuilt();
        float dx = toX - fromX;
        float dy = toY - fromY;
        float bestT = Float.MAX_VALUE;
        int bestEntity = World.NONE;
        float nx = 0f;
        float ny = 0f;
        boolean tile = false;
        queryAll(Math.min(fromX, toX), Math.min(fromY, toY), Math.abs(dx), Math.abs(dy), candidates);
        for (int i = 0; i < candidates.size; i++) {
            int e = candidates.get(i);
            if (!rayTarget(e, mask)) {
                continue;
            }
            float tHit = slab(fromX, fromY, dx, dy, other);
            if (tHit >= 0f && tHit < bestT) {
                bestT = tHit;
                bestEntity = e;
                nx = slabNormalX;
                ny = slabNormalY;
                tile = false;
            }
        }
        for (int g = 0; g < grids.size; g++) {
            if ((mask & (1 << gridLayers.get(g))) == 0) {
                continue;
            }
            float tHit = walkGrid(grids.get(g), fromX, fromY, dx, dy, bestT);
            if (tHit >= 0f && tHit < bestT) {
                bestT = tHit;
                bestEntity = World.NONE;
                nx = slabNormalX;
                ny = slabNormalY;
                tile = true;
            }
        }
        if (bestT == Float.MAX_VALUE) {
            return false;
        }
        out.entity = bestEntity;
        out.tile = tile;
        out.x = fromX + dx * bestT;
        out.y = fromY + dy * bestT;
        out.distance = bestT * (float) Math.sqrt(dx * dx + dy * dy);
        out.normalX = nx;
        out.normalY = ny;
        return true;
    }

    /** Every non-trigger collider (layer in the mask) the segment crosses, nearest first (tiles not included). */
    public int raycastAll(float fromX, float fromY, float toX, float toY, int mask, IntArray out) {
        ensureBuilt();
        float dx = toX - fromX;
        float dy = toY - fromY;
        queryAll(Math.min(fromX, toX), Math.min(fromY, toY), Math.abs(dx), Math.abs(dy), candidates);
        out.clear();
        distances.clear();
        for (int i = 0; i < candidates.size; i++) {
            int e = candidates.get(i);
            if (!rayTarget(e, mask)) {
                continue;
            }
            float tHit = slab(fromX, fromY, dx, dy, other);
            if (tHit >= 0f) {
                int at = 0;
                while (at < distances.size && distances.get(at) <= tHit) {
                    at++;
                }
                distances.insert(at, tHit);
                out.insert(at, e);
            }
        }
        return out.size;
    }

    /** The actors inside a trigger as of the last {@link CollisionSystem} step, sorted by entity. */
    public int inside(int trigger, IntArray out) {
        out.clear();
        IntArray list = insideByTrigger.get(trigger);
        if (list != null) {
            out.addAll(list);
        }
        return out.size;
    }

    /** The entity's collider box in world units, or null if it has no Transform and Collider. */
    public Rectangle bounds(int entity, Rectangle out) {
        if (!world.isAlive(entity) || !world.has(entity, Collider.class) || !world.has(entity, Transform.class)) {
            return null;
        }
        Transform t = world.get(entity, Transform.class);
        Rectangle b = world.get(entity, Collider.class).box;
        return out.set(t.position.x + b.x, t.position.y + b.y, b.width, b.height);
    }

    /** The tile grids added so far (for the debug view). */
    Array<TileGrid> grids() {
        return grids;
    }

    // ------------------------------------------------------------------------------------------------
    // The fixed step (CollisionSystem)
    // ------------------------------------------------------------------------------------------------

    /** Re-syncs the spatial hash with the world, then updates triggers and queues their events. */
    void step() {
        sync();
        for (int i = 0; i < removed.size; i++) {
            IntArray was = insideByTrigger.remove(removed.get(i));
            if (was != null) {
                for (int j = 0; j < was.size; j++) {
                    events.queue(new TriggerExited(removed.get(i), was.get(j)));
                }
            }
        }
        IntArray entities = colliders.entities();
        for (int i = 0; i < entities.size; i++) {
            int trigger = entities.get(i);
            Collider c = world.get(trigger, Collider.class);
            if (!c.trigger) {
                continue;
            }
            bounds(trigger, box);
            bodies.query(box.x, box.y, box.width, box.height, candidates);
            now.clear();
            for (int j = 0; j < candidates.size; j++) {
                int e = candidates.get(j);
                if (e != trigger && world.has(e, Actor.class) && (c.mask & (1 << layer(e))) != 0
                        && bounds(e, other) != null && overlaps(box, other)) {
                    now.add(e);
                }
            }
            IntArray was = insideByTrigger.get(trigger);
            if (was == null) {
                was = new IntArray(4);
                insideByTrigger.put(trigger, was);
            }
            for (int j = 0; j < now.size; j++) {
                if (!was.contains(now.get(j))) {
                    events.queue(new TriggerEntered(trigger, now.get(j)));
                }
            }
            for (int j = 0; j < was.size; j++) {
                if (!now.contains(was.get(j))) {
                    events.queue(new TriggerExited(trigger, was.get(j)));
                }
            }
            was.clear();
            was.addAll(now);
        }
    }

    private void ensureBuilt() {
        if (solids == null) {
            sync();
        }
    }

    private void sync() {
        if (solids == null) {
            solids = new SpatialHash(cellSize);
            bodies = new SpatialHash(cellSize);
        }
        stamp++;
        IntArray entities = colliders.entities();
        for (int i = 0; i < entities.size; i++) {
            index(entities.get(i));
        }
        removed.clear();
        solids.removeUnstamped(stamp, removed);
        bodies.removeUnstamped(stamp, removed);
    }

    private void index(int entity) {
        bounds(entity, indexBox);
        boolean solid = world.has(entity, Solid.class);
        SpatialHash into = solid ? solids : bodies;
        SpatialHash notInto = solid ? bodies : solids;
        if (notInto.contains(entity)) {
            notInto.remove(entity);   // gained or lost its Solid
        }
        into.put(entity, indexBox.x, indexBox.y, indexBox.width, indexBox.height, stamp);
    }

    /** Both hashes, merged and sorted by entity. */
    private void queryAll(float x, float y, float w, float h, IntArray out) {
        solids.query(x, y, w, h, solidHits);
        bodies.query(x, y, w, h, bodyHits);
        out.clear();
        int i = 0;
        int j = 0;
        while (i < solidHits.size || j < bodyHits.size) {
            if (j >= bodyHits.size || (i < solidHits.size && solidHits.get(i) < bodyHits.get(j))) {
                out.add(solidHits.get(i++));
            } else {
                out.add(bodyHits.get(j++));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Sweeps
    // ------------------------------------------------------------------------------------------------

    private void stepX(int actor, Transform t, Collider c, Actor a, float dx) {
        if (dx == 0f) {
            return;
        }
        float x = t.position.x + c.box.x;
        float nx = sweepX(actor, x, t.position.y + c.box.y, c.box.width, c.box.height, dx, c.mask);
        t.position.x += nx - x;
        if (sweepHit) {
            result.hitX = true;
            result.entityX = sweepEntity;
            result.normalX = dx > 0 ? -1f : 1f;
        }
    }

    private void stepY(int actor, Transform t, Collider c, Actor a, float dy) {
        if (dy == 0f) {
            return;
        }
        float y = t.position.y + c.box.y;
        float ny = sweepY(actor, t.position.x + c.box.x, y, c.box.width, c.box.height, dy, c.mask,
                a.dropThroughTicks > 0);
        t.position.y += ny - y;
        if (sweepHit) {
            result.hitY = true;
            result.entityY = sweepEntity;
            result.normalY = dy > 0 ? -1f : 1f;
        }
    }

    private MoveResult finishMove(int actor, Transform t, Collider c, Actor a) {
        if (a.dropThroughTicks > 0) {
            a.dropThroughTicks--;
        }
        a.hitX = result.hitX;
        a.hitY = result.hitY;
        a.hitEntityX = result.entityX;
        a.hitEntityY = result.entityY;
        index(actor);
        return result;
    }

    /** The box's new x after moving dx; sets {@code sweepHit} and {@code sweepEntity}. */
    private float sweepX(int self, float x, float y, float w, float h, float dx, int mask) {
        sweepHit = false;
        sweepEntity = World.NONE;
        float target = x + dx;
        float bottom = y + EPS;
        float top = y + h - EPS;
        for (int g = 0; g < grids.size; g++) {
            if ((mask & (1 << gridLayers.get(g))) == 0) {
                continue;
            }
            TileGrid grid = grids.get(g);
            float cw = grid.cellWidth();
            int r0 = Math.max(0, floor(bottom / grid.cellHeight()));
            int r1 = Math.min(grid.height() - 1, floor(top / grid.cellHeight()));
            if (dx > 0) {
                float front = x + w;
                int c0 = Math.max(0, ceil((front - EPS) / cw));
                int c1 = Math.min(grid.width() - 1, ceil((target + w - EPS) / cw) - 1);
                for (int col = c0; col <= c1; col++) {
                    if (solidColumn(grid, col, r0, r1)) {
                        target = Math.min(target, col * cw - w);
                        sweepHit = true;
                        sweepEntity = World.NONE;
                        break;
                    }
                }
            } else {
                int c0 = Math.min(grid.width() - 1, floor((x + EPS) / cw) - 1);
                int c1 = Math.max(0, floor((target + EPS) / cw));
                for (int col = c0; col >= c1; col--) {
                    if (solidColumn(grid, col, r0, r1)) {
                        target = Math.max(target, (col + 1) * cw);
                        sweepHit = true;
                        sweepEntity = World.NONE;
                        break;
                    }
                }
            }
        }
        float left = Math.min(x, target);
        solids.query(left, y, Math.max(x, target) + w - left, h, candidates);
        for (int i = 0; i < candidates.size; i++) {
            int e = candidates.get(i);
            if (!blocks(e, mask) || world.get(e, Solid.class).oneWay || bounds(e, other) == null
                    || !(other.y < top && other.y + other.height > bottom)) {
                continue;
            }
            if (dx > 0) {
                if (other.x >= x + w - EPS && other.x - w < target) {
                    target = other.x - w;
                    sweepHit = true;
                    sweepEntity = e;
                }
            } else if (other.x + other.width <= x + EPS && other.x + other.width > target) {
                target = other.x + other.width;
                sweepHit = true;
                sweepEntity = e;
            }
        }
        return target;
    }

    /** The box's new y after moving dy; sets {@code sweepHit} and {@code sweepEntity}. */
    private float sweepY(int self, float x, float y, float w, float h, float dy, int mask, boolean dropping) {
        sweepHit = false;
        sweepEntity = World.NONE;
        float target = y + dy;
        float left = x + EPS;
        float right = x + w - EPS;
        for (int g = 0; g < grids.size; g++) {
            if ((mask & (1 << gridLayers.get(g))) == 0) {
                continue;
            }
            TileGrid grid = grids.get(g);
            float ch = grid.cellHeight();
            int c0 = Math.max(0, floor(left / grid.cellWidth()));
            int c1 = Math.min(grid.width() - 1, floor(right / grid.cellWidth()));
            if (dy < 0) {
                int r0 = Math.min(grid.height() - 1, floor((y + EPS) / ch) - 1);
                int r1 = Math.max(0, floor((target + EPS) / ch));
                for (int row = r0; row >= r1; row--) {
                    if (landsOn(grid, row, c0, c1, dropping)) {
                        target = Math.max(target, (row + 1) * ch);
                        sweepHit = true;
                        sweepEntity = World.NONE;
                        break;
                    }
                }
            } else {
                float top = y + h;
                int r0 = Math.max(0, ceil((top - EPS) / ch));
                int r1 = Math.min(grid.height() - 1, ceil((target + h - EPS) / ch) - 1);
                for (int row = r0; row <= r1; row++) {
                    if (solidRow(grid, row, c0, c1)) {
                        target = Math.min(target, row * ch - h);
                        sweepHit = true;
                        sweepEntity = World.NONE;
                        break;
                    }
                }
            }
        }
        float bottom = Math.min(y, target);
        solids.query(x, bottom, w, Math.max(y, target) + h - bottom, candidates);
        for (int i = 0; i < candidates.size; i++) {
            int e = candidates.get(i);
            if (!blocks(e, mask) || bounds(e, other) == null || !(other.x < right && other.x + other.width > left)) {
                continue;
            }
            boolean oneWay = world.get(e, Solid.class).oneWay;
            if (dy < 0) {
                float surface = other.y + other.height;
                if ((!oneWay || !dropping) && surface <= y + EPS && surface > target) {
                    target = surface;
                    sweepHit = true;
                    sweepEntity = e;
                }
            } else if (!oneWay && other.y >= y + h - EPS && other.y - h < target) {
                target = other.y - h;
                sweepHit = true;
                sweepEntity = e;
            }
        }
        return target;
    }

    private static boolean solidColumn(TileGrid grid, int col, int r0, int r1) {
        for (int row = r0; row <= r1; row++) {
            if (grid.solid(col, row)) {
                return true;
            }
        }
        return false;
    }

    private static boolean solidRow(TileGrid grid, int row, int c0, int c1) {
        for (int col = c0; col <= c1; col++) {
            if (grid.solid(col, row)) {
                return true;
            }
        }
        return false;
    }

    private static boolean landsOn(TileGrid grid, int row, int c0, int c1, boolean dropping) {
        for (int col = c0; col <= c1; col++) {
            if (grid.solid(col, row) || (!dropping && grid.oneWay(col, row))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------
    // Moving solids
    // ------------------------------------------------------------------------------------------------

    private void moveSolidAxis(int solid, Transform t, Collider c, Solid s, float d, boolean horizontal) {
        if (d == 0f) {
            return;
        }
        bounds(solid, box);
        bodies.query(box.x - PROBE, box.y + box.height - PROBE, box.width + 2 * PROBE, 2 * PROBE, candidates);
        riders.clear();
        for (int i = 0; i < candidates.size; i++) {
            int e = candidates.get(i);
            if (e != solid && isActor(e) && (world.get(e, Collider.class).mask & (1 << c.layer)) != 0
                    && bounds(e, other) != null && Math.abs(other.y - (box.y + box.height)) <= PROBE
                    && other.x < box.x + box.width - EPS && other.x + other.width > box.x + EPS) {
                riders.add(e);
            }
        }
        if (horizontal) {
            t.position.x += d;
        } else {
            t.position.y += d;
        }
        index(solid);
        bounds(solid, box);
        ignore = solid;
        try {
            for (int i = 0; i < riders.size; i++) {
                int rider = riders.get(i);
                carry(rider, horizontal ? d : 0f, horizontal ? 0f : d);
            }
            if (s.oneWay) {
                return;
            }
            bodies.query(box.x, box.y, box.width, box.height, pushed);   // carry() below re-uses candidates
            for (int i = 0; i < pushed.size; i++) {
                int e = pushed.get(i);
                if (e == solid || riders.contains(e) || !isActor(e)
                        || (world.get(e, Collider.class).mask & (1 << c.layer)) == 0
                        || bounds(e, other) == null || !overlaps(box, other)) {
                    continue;
                }
                float push;
                if (horizontal) {
                    push = d > 0 ? box.x + box.width - other.x : box.x - (other.x + other.width);
                    carry(e, push, 0f);
                } else {
                    push = d > 0 ? box.y + box.height - other.y : box.y - (other.y + other.height);
                    carry(e, 0f, push);
                }
                if (bounds(e, other) != null && overlaps(box, other) && squished.add(e)) {
                    events.queue(new ActorSquished(e, solid));
                }
            }
        } finally {
            ignore = World.NONE;
        }
    }

    /** Moves an actor along with a solid (slide; the result fields of the actor are updated). */
    private void carry(int actor, float dx, float dy) {
        Transform t = world.get(actor, Transform.class);
        Collider c = world.get(actor, Collider.class);
        Actor a = world.get(actor, Actor.class);
        result.reset();
        int drop = a.dropThroughTicks;
        stepX(actor, t, c, a, dx);
        stepY(actor, t, c, a, dy);
        a.dropThroughTicks = drop;   // being carried is not the actor's own move
        index(actor);
    }

    // ------------------------------------------------------------------------------------------------
    // Rays
    // ------------------------------------------------------------------------------------------------

    private float slabNormalX;
    private float slabNormalY;

    /** Entry parameter in [0, 1] of the segment into the box, or -1; sets the entry normal. */
    private float slab(float ox, float oy, float dx, float dy, Rectangle r) {
        float tMin = 0f;
        float tMax = 1f;
        float nx = 0f;
        float ny = 0f;
        if (dx == 0f) {
            if (ox < r.x || ox > r.x + r.width) {
                return -1f;
            }
        } else {
            float t1 = (r.x - ox) / dx;
            float t2 = (r.x + r.width - ox) / dx;
            float near = Math.min(t1, t2);
            float far = Math.max(t1, t2);
            if (near > tMin) {
                tMin = near;
                nx = dx > 0 ? -1f : 1f;
                ny = 0f;
            }
            tMax = Math.min(tMax, far);
        }
        if (dy == 0f) {
            if (oy < r.y || oy > r.y + r.height) {
                return -1f;
            }
        } else {
            float t1 = (r.y - oy) / dy;
            float t2 = (r.y + r.height - oy) / dy;
            float near = Math.min(t1, t2);
            float far = Math.max(t1, t2);
            if (near > tMin) {
                tMin = near;
                nx = 0f;
                ny = dy > 0 ? -1f : 1f;
            }
            tMax = Math.min(tMax, far);
        }
        if (tMin > tMax) {
            return -1f;
        }
        slabNormalX = nx;
        slabNormalY = ny;
        return tMin;
    }

    /** The first solid cell the segment enters (grid walk), as a parameter in [0, limit), or -1. */
    private float walkGrid(TileGrid grid, float ox, float oy, float dx, float dy, float limit) {
        float cw = grid.cellWidth();
        float ch = grid.cellHeight();
        int cx = floor(ox / cw);
        int cy = floor(oy / ch);
        if (grid.solid(cx, cy)) {
            slabNormalX = 0f;
            slabNormalY = 0f;
            return 0f;
        }
        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        float tDeltaX = stepX == 0 ? Float.MAX_VALUE : Math.abs(cw / dx);
        float tDeltaY = stepY == 0 ? Float.MAX_VALUE : Math.abs(ch / dy);
        float tMaxX = stepX == 0 ? Float.MAX_VALUE : ((stepX > 0 ? (cx + 1) * cw : cx * cw) - ox) / dx;
        float tMaxY = stepY == 0 ? Float.MAX_VALUE : ((stepY > 0 ? (cy + 1) * ch : cy * ch) - oy) / dy;
        float end = Math.min(1f, limit);
        while (true) {
            float t;
            if (tMaxX < tMaxY) {
                t = tMaxX;
                cx += stepX;
                tMaxX += tDeltaX;
                slabNormalX = -stepX;
                slabNormalY = 0f;
            } else {
                t = tMaxY;
                cy += stepY;
                tMaxY += tDeltaY;
                slabNormalX = 0f;
                slabNormalY = -stepY;
            }
            if (t > end || t == Float.MAX_VALUE) {
                return -1f;
            }
            if (cx < -1 || cy < -1 || cx > grid.width() || cy > grid.height()) {
                if ((stepX > 0 && cx > grid.width()) || (stepX < 0 && cx < -1)
                        || (stepY > 0 && cy > grid.height()) || (stepY < 0 && cy < -1)) {
                    return -1f;
                }
            }
            if (grid.solid(cx, cy)) {
                return t;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------

    private boolean rayTarget(int e, int mask) {
        if (e == ignore || !inMask(mask, e) || bounds(e, other) == null) {
            return false;
        }
        Collider c = world.get(e, Collider.class);
        if (c.trigger) {
            return false;
        }
        Solid s = world.has(e, Solid.class) ? world.get(e, Solid.class) : null;
        return s == null || !s.oneWay;
    }

    private boolean blocks(int e, int actorMask) {
        return e != ignore && world.isAlive(e) && world.has(e, Solid.class) && world.has(e, Collider.class)
                && !world.get(e, Collider.class).trigger && (actorMask & (1 << layer(e))) != 0;
    }

    private boolean isActor(int e) {
        return world.isAlive(e) && world.has(e, Actor.class) && world.has(e, Collider.class);
    }

    private boolean inMask(int mask, int e) {
        return world.isAlive(e) && world.has(e, Collider.class) && (mask & (1 << layer(e))) != 0;
    }

    private int layer(int e) {
        return world.get(e, Collider.class).layer;
    }

    private Transform transform(int entity) {
        if (!world.isAlive(entity) || !world.has(entity, Transform.class)) {
            throw new IllegalArgumentException("Entity " + entity + " is not alive or has no Transform");
        }
        return world.get(entity, Transform.class);
    }

    private Collider actorCollider(int actor) {
        if (!world.has(actor, Collider.class) || !world.has(actor, Actor.class)) {
            throw new IllegalArgumentException("Entity " + actor + " is not an actor: it needs Transform, Collider "
                    + "and Actor");
        }
        return world.get(actor, Collider.class);
    }

    private float maxStep() {
        float step = cellSize / 2f;
        for (int g = 0; g < grids.size; g++) {
            step = Math.min(step, Math.min(grids.get(g).cellWidth(), grids.get(g).cellHeight()) / 2f);
        }
        return step;
    }

    private static boolean overlaps(Rectangle a, Rectangle b) {
        return a.x < b.x + b.width - EPS && a.x + a.width > b.x + EPS
                && a.y < b.y + b.height - EPS && a.y + a.height > b.y + EPS;
    }

    private static int checkLayer(int layer) {
        if (layer < 0 || layer > 31) {
            throw new IllegalArgumentException("Collision layer must be 0-31 (was " + layer + ")");
        }
        return layer;
    }

    private static int floor(float v) {
        return (int) Math.floor(v);
    }

    private static int ceil(float v) {
        return (int) Math.ceil(v);
    }
}
