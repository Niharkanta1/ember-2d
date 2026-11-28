package dev.ember2d.box2d;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.collision.TriggerEntered;
import dev.ember2d.collision.TriggerExited;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Time;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Steps a Box2D world in the fixed phase (F-006B). Every step it makes bodies for new {@code Transform} +
 * {@link RigidBody} entities, destroys the bodies of entities that are gone or lost their {@code RigidBody}, steps
 * the simulation and writes dynamic and kinematic bodies back into their {@code Transform}. Sensor overlaps are
 * queued as {@code TriggerEntered}/{@code TriggerExited}, solid contacts as {@link ContactBegan}/{@link ContactEnded}.
 *
 * <p>Call {@link #dispose()} in the scene's {@code exit()}; it destroys the Box2D world and every body.
 */
@PublicApi
public final class Box2dSystem extends EntitySystem implements Disposable {

    private static final int VELOCITY_ITERATIONS = 8;
    private static final int POSITION_ITERATIONS = 3;
    private static boolean initialised;

    private final EventBus events;
    private final Time time;
    private final com.badlogic.gdx.physics.box2d.World physics;
    private final IntMap<Body> bodies = new IntMap<>();
    private final IntSet seen = new IntSet();
    private final List<Body> tileBodies = new ArrayList<>();
    private final List<Object> pending = new ArrayList<>();
    private final Vector2 tmp = new Vector2();
    private boolean disposed;

    public Box2dSystem(World world, EventBus events, Time time, float gravityX, float gravityY) {
        super(Phase.FIXED, world.family(Transform.class, RigidBody.class));
        this.events = Objects.requireNonNull(events, "events");
        this.time = Objects.requireNonNull(time, "time");
        init();
        physics = new com.badlogic.gdx.physics.box2d.World(new Vector2(gravityX, gravityY), true);
        physics.setContactListener(new Contacts());
    }

    private static synchronized void init() {
        if (!initialised) {
            Box2D.init();
            initialised = true;
        }
    }

    /**
     * Adds the grid's solid cells as static boxes, one per run of solid cells in a row (one-way cells are left out).
     * Tile bodies produce no contact events.
     */
    public void addTileGrid(TileGrid grid) {
        Objects.requireNonNull(grid, "grid");
        requireOpen();
        float cw = grid.cellWidth();
        float ch = grid.cellHeight();
        PolygonShape box = new PolygonShape();
        try {
            for (int y = 0; y < grid.height(); y++) {
                int x = 0;
                while (x < grid.width()) {
                    if (!grid.solid(x, y) || grid.oneWay(x, y)) {
                        x++;
                        continue;
                    }
                    int start = x;
                    while (x < grid.width() && grid.solid(x, y) && !grid.oneWay(x, y)) {
                        x++;
                    }
                    BodyDef def = new BodyDef();
                    def.type = BodyDef.BodyType.StaticBody;
                    def.position.set((start + x) * cw / 2f, (y + 0.5f) * ch);
                    Body body = physics.createBody(def);
                    box.setAsBox((x - start) * cw / 2f, ch / 2f);
                    body.createFixture(box, 0f).setFriction(0.6f);
                    tileBodies.add(body);
                }
            }
        } finally {
            box.dispose();
        }
    }

    /** Bodies of entities (tile bodies not counted). */
    public int bodyCount() {
        return bodies.size;
    }

    @Override
    protected void begin() {
        seen.clear();
    }

    @Override
    protected void process(int entity, float delta) {
        if (disposed) {
            return;
        }
        seen.add(entity);
        if (!bodies.containsKey(entity)) {
            create(entity, world().get(entity, Transform.class), world().get(entity, RigidBody.class));
        }
    }

    @Override
    protected void end() {
        if (disposed) {
            return;
        }
        // Bodies whose entity is gone or lost its RigidBody.
        IntMap.Keys keys = bodies.keys();
        List<Integer> stale = new ArrayList<>();
        while (keys.hasNext) {
            int entity = keys.next();
            if (!seen.contains(entity)) {
                stale.add(entity);
            }
        }
        for (int entity : stale) {
            destroy(entity);
        }

        physics.step(time.fixedDelta(), VELOCITY_ITERATIONS, POSITION_ITERATIONS);

        for (IntMap.Entry<Body> e : bodies) {
            Body body = e.value;
            if (body.getType() == BodyDef.BodyType.StaticBody) {
                continue;
            }
            Transform t = world().get(e.key, Transform.class);
            if (t != null) {
                Vector2 p = body.getPosition();
                t.position.set(p.x, p.y);
                t.rotation = body.getAngle() * MathUtils.radiansToDegrees;
            }
        }
        flushEvents();
    }

    private void create(int entity, Transform t, RigidBody rb) {
        BodyDef def = new BodyDef();
        def.type = switch (rb.type) {
            case STATIC -> BodyDef.BodyType.StaticBody;
            case KINEMATIC -> BodyDef.BodyType.KinematicBody;
            case DYNAMIC -> BodyDef.BodyType.DynamicBody;
        };
        def.position.set(t.position);
        def.angle = t.rotation * MathUtils.degreesToRadians;
        def.fixedRotation = rb.fixedRotation;
        def.bullet = rb.bullet;
        def.gravityScale = rb.gravityScale;
        def.linearDamping = rb.linearDamping;
        def.angularDamping = rb.angularDamping;
        def.linearVelocity.set(rb.velocityX, rb.velocityY);
        def.angularVelocity = rb.angularVelocity * MathUtils.degreesToRadians;
        Body body = physics.createBody(def);
        body.setUserData(entity);

        Shape shape;
        if (rb.shape == BodyShape.CIRCLE) {
            CircleShape circle = new CircleShape();
            circle.setRadius(rb.radius);
            circle.setPosition(rb.offset);
            shape = circle;
        } else {
            PolygonShape box = new PolygonShape();
            box.setAsBox(rb.width / 2f, rb.height / 2f, rb.offset, 0f);
            shape = box;
        }
        try {
            FixtureDef fd = new FixtureDef();
            fd.shape = shape;
            fd.density = rb.density;
            fd.friction = rb.friction;
            fd.restitution = rb.restitution;
            fd.isSensor = rb.sensor;
            fd.filter.categoryBits = (short) rb.category;
            fd.filter.maskBits = (short) rb.mask;
            fd.filter.groupIndex = (short) rb.group;
            body.createFixture(fd);
        } finally {
            shape.dispose();
        }
        rb.body = body;
        rb.transform = t;
        bodies.put(entity, body);
    }

    private void destroy(int entity) {
        Body body = bodies.remove(entity);
        if (body == null) {
            return;
        }
        RigidBody rb = world().isAlive(entity) ? world().get(entity, RigidBody.class) : null;
        if (rb != null && rb.body == body) {
            rb.body = null;
        }
        physics.destroyBody(body);
    }

    private void flushEvents() {
        for (Object event : pending) {
            if (alive(event)) {
                events.queue(event);
            }
        }
        pending.clear();
    }

    private boolean alive(Object event) {
        World w = world();
        if (event instanceof TriggerEntered e) {
            return w.isAlive(e.trigger()) && w.isAlive(e.actor());
        } else if (event instanceof TriggerExited e) {
            return w.isAlive(e.trigger()) && w.isAlive(e.actor());
        } else if (event instanceof ContactBegan e) {
            return w.isAlive(e.a()) && w.isAlive(e.b());
        } else if (event instanceof ContactEnded e) {
            return w.isAlive(e.a()) && w.isAlive(e.b());
        }
        return false;
    }

    /** Visits every body, including tile bodies (debug drawing). */
    void forEachBody(Consumer<Body> visitor) {
        if (disposed) {
            return;
        }
        Array<Body> all = new Array<>(physics.getBodyCount());
        physics.getBodies(all);
        for (Body body : all) {
            visitor.accept(body);
        }
    }

    /** Destroys the Box2D world and every body; the system does nothing afterwards. Safe to call twice. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (IntMap.Entry<Body> e : bodies) {
            RigidBody rb = world().isAlive(e.key) ? world().get(e.key, RigidBody.class) : null;
            if (rb != null) {
                rb.body = null;
            }
        }
        bodies.clear();
        tileBodies.clear();
        pending.clear();
        physics.dispose();
    }

    private void requireOpen() {
        if (disposed) {
            throw new IllegalStateException("The Box2D world was disposed");
        }
    }

    /** Collects contact events during the step; they are queued after it, for entities that still exist. */
    private final class Contacts implements ContactListener {

        @Override
        public void beginContact(Contact contact) {
            record(contact, true);
        }

        @Override
        public void endContact(Contact contact) {
            record(contact, false);
        }

        private void record(Contact contact, boolean began) {
            Fixture fa = contact.getFixtureA();
            Fixture fb = contact.getFixtureB();
            Object ua = fa.getBody().getUserData();
            Object ub = fb.getBody().getUserData();
            if (!(ua instanceof Integer a) || !(ub instanceof Integer b)) {
                return;   // tile bodies
            }
            if (fa.isSensor() || fb.isSensor()) {
                if (fa.isSensor()) {
                    pending.add(began ? new TriggerEntered(a, b) : new TriggerExited(a, b));
                }
                if (fb.isSensor()) {
                    pending.add(began ? new TriggerEntered(b, a) : new TriggerExited(b, a));
                }
            } else {
                pending.add(began ? new ContactBegan(a, b) : new ContactEnded(a, b));
            }
        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold) {
        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse) {
        }
    }
}
