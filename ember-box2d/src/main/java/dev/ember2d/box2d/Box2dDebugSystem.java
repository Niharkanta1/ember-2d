package dev.ember2d.box2d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.debug.DebugOverlay;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import java.util.Objects;

/**
 * Draws every Box2D fixture through the debug overlay while it is visible (F-006B, F3): static grey, kinematic blue,
 * dynamic green (asleep: dark green), sensors yellow; circles get a radius line showing their rotation.
 */
@PublicApi
public final class Box2dDebugSystem extends EntitySystem {

    private static final Color STATIC = new Color(0.6f, 0.6f, 0.6f, 1f);
    private static final Color KINEMATIC = new Color(0.3f, 0.6f, 1f, 1f);
    private static final Color DYNAMIC = new Color(0.3f, 1f, 0.3f, 1f);
    private static final Color ASLEEP = new Color(0.15f, 0.5f, 0.15f, 1f);
    private static final Color SENSOR = new Color(1f, 0.9f, 0.2f, 1f);

    private final Box2dSystem physics;
    private final DebugOverlay debug;
    private final Vector2 a = new Vector2();
    private final Vector2 b = new Vector2();
    private final Vector2 first = new Vector2();

    public Box2dDebugSystem(World world, Box2dSystem physics, DebugOverlay debug) {
        super(Phase.RENDER, world.family(Transform.class, RigidBody.class));
        this.physics = Objects.requireNonNull(physics, "physics");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    @Override
    protected void begin() {
        if (debug.isVisible()) {
            physics.forEachBody(this::draw);
        }
    }

    @Override
    protected void process(int entity, float delta) {
        // Everything is drawn in begin(), including tile bodies that have no entity.
    }

    private void draw(Body body) {
        for (Fixture fixture : body.getFixtureList()) {
            Color color = fixture.isSensor() ? SENSOR
                    : body.getType() == BodyDef.BodyType.StaticBody ? STATIC
                    : body.getType() == BodyDef.BodyType.KinematicBody ? KINEMATIC
                    : body.isAwake() ? DYNAMIC : ASLEEP;
            Shape shape = fixture.getShape();
            if (shape instanceof PolygonShape polygon) {
                int n = polygon.getVertexCount();
                for (int i = 0; i < n; i++) {
                    polygon.getVertex(i, a);
                    a.set(body.getWorldPoint(a));
                    if (i == 0) {
                        first.set(a);
                    } else {
                        debug.line(b.x, b.y, a.x, a.y, color);
                    }
                    b.set(a);
                }
                debug.line(b.x, b.y, first.x, first.y, color);
            } else if (shape instanceof CircleShape circle) {
                a.set(body.getWorldPoint(circle.getPosition()));
                float r = circle.getRadius();
                debug.circle(a.x, a.y, r, color);
                float angle = body.getAngle();
                debug.line(a.x, a.y, a.x + r * MathUtils.cos(angle), a.y + r * MathUtils.sin(angle), color);
            }
        }
    }
}
