package dev.ember2d.kit.topdown.ai;

import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.steer.SteeringAcceleration;
import com.badlogic.gdx.ai.steer.SteeringBehavior;
import com.badlogic.gdx.ai.steer.behaviors.Arrive;
import com.badlogic.gdx.ai.steer.behaviors.Flee;
import com.badlogic.gdx.ai.steer.behaviors.Seek;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntMap;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.kit.topdown.TopDownBody;

/**
 * Enemy AI hooks (F-016, I-330), in {@code FIXED}: turns each {@link Steering} into the move direction of its
 * {@code TopDownBody} (setting {@code useInput} to false), using gdx-ai's seek, flee and arrive behaviours and, for
 * {@link Steering.Mode#FOLLOW_PATH}, paths from a {@link PathPlanner}. Add it before the {@code TopDownSystem}. gdx-ai
 * stays behind this adapter: no gdx-ai type is part of the kit's API.
 */
@PublicApi
public final class AiSystem extends EntitySystem {

    /** Waypoints closer than this count as reached. */
    private static final float WAYPOINT_RADIUS = 0.2f;

    private final PathPlanner planner;
    private final Agent agent = new Agent();
    private final Point point = new Point();
    private final SteeringAcceleration<Vector2> out = new SteeringAcceleration<>(new Vector2());
    private final Seek<Vector2> seek = new Seek<>(agent, point);
    private final Flee<Vector2> flee = new Flee<>(agent, point);
    private final Arrive<Vector2> arrive = new Arrive<>(agent, point).setTimeToTarget(1f);
    private final IntMap<FloatArray> paths = new IntMap<>();
    private final Vector2 target = new Vector2();

    /** @param planner plans {@code FOLLOW_PATH} routes; null makes that mode seek directly */
    public AiSystem(World world, PathPlanner planner) {
        super(Phase.FIXED, world.family(Steering.class, TopDownBody.class, Transform.class));
        this.planner = planner;
    }

    @Override
    protected void process(int entity, float delta) {
        Steering s = world().get(entity, Steering.class);
        TopDownBody body = world().get(entity, TopDownBody.class);
        Transform t = world().get(entity, Transform.class);
        body.useInput = false;
        body.moveX = 0f;
        body.moveY = 0f;
        if (s.mode == Steering.Mode.NONE || !target(s)) {
            return;
        }
        float dx = target.x - t.position.x;
        float dy = target.y - t.position.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        agent.position.set(t.position);
        agent.maxSpeed = body.maxSpeed;
        switch (s.mode) {
            case SEEK -> {
                if (s.sight <= 0f || distance <= s.sight) {
                    steer(seek, body, target.x, target.y);
                }
            }
            case FLEE -> {
                if (s.fleeRadius <= 0f || distance < s.fleeRadius) {
                    steer(flee, body, target.x, target.y);
                }
            }
            case ARRIVE -> arriveAt(s, body, target.x, target.y);
            case FOLLOW_PATH -> follow(entity, s, body, t, distance, delta);
            default -> { }
        }
    }

    private void follow(int entity, Steering s, TopDownBody body, Transform t, float distance, float delta) {
        if (s.sight > 0f && distance > s.sight) {
            return;
        }
        if (planner == null) {
            steer(seek, body, target.x, target.y);
            return;
        }
        FloatArray waypoints = paths.get(entity);
        if (waypoints == null) {
            waypoints = new FloatArray();
            paths.put(entity, waypoints);
            s.repathLeft = 0f;
        }
        s.repathLeft -= delta;
        if (s.repathLeft <= 0f) {
            s.repathLeft = s.repathSeconds;
            s.pathFound = planner.find(t.position.x, t.position.y, target.x, target.y, waypoints);
            s.waypoint = 0;
        }
        if (!s.pathFound) {
            return;
        }
        int count = waypoints.size / 2;
        while (s.waypoint < count - 1 && near(t, waypoints, s.waypoint)) {
            s.waypoint++;
        }
        if (s.waypoint >= count - 1) {
            arriveAt(s, body, target.x, target.y);   // the goal's cell: go to the target itself
            return;
        }
        steer(seek, body, waypoints.get(s.waypoint * 2), waypoints.get(s.waypoint * 2 + 1));
    }

    private void arriveAt(Steering s, TopDownBody body, float x, float y) {
        arrive.setArrivalTolerance(s.arriveRadius).setDecelerationRadius(Math.max(s.slowRadius, s.arriveRadius));
        steer(arrive, body, x, y);
    }

    /** Runs a behaviour; the agent reports no velocity, so the result is the wanted velocity (move = it / max speed). */
    private void steer(SteeringBehavior<Vector2> behaviour, TopDownBody body, float x, float y) {
        point.position.set(x, y);
        behaviour.calculateSteering(out);
        if (body.maxSpeed <= 0f) {
            return;
        }
        float mx = out.linear.x / body.maxSpeed;
        float my = out.linear.y / body.maxSpeed;
        float len2 = mx * mx + my * my;
        if (len2 > 1f) {
            float len = (float) Math.sqrt(len2);
            mx /= len;
            my /= len;
        }
        body.moveX = mx;
        body.moveY = my;
    }

    private static boolean near(Transform t, FloatArray waypoints, int i) {
        float dx = waypoints.get(i * 2) - t.position.x;
        float dy = waypoints.get(i * 2 + 1) - t.position.y;
        return dx * dx + dy * dy <= WAYPOINT_RADIUS * WAYPOINT_RADIUS;
    }

    private boolean target(Steering s) {
        int e = s.target == null ? World.NONE : s.target.entity(world());
        Transform t = e == World.NONE ? null : world().get(e, Transform.class);
        if (t != null) {
            target.set(t.position);
            return true;
        }
        if (s.target != null) {
            return false;   // the target entity is gone: stand still
        }
        target.set(s.targetX, s.targetY);
        return true;
    }

    @Override
    protected void end() {
        for (IntMap.Keys keys = paths.keys(); keys.hasNext; ) {
            if (!world().isAlive(keys.next())) {
                keys.remove();
            }
        }
    }

    // ---- the gdx-ai adapter ----

    /** A point target. */
    private static class Point implements Location<Vector2> {
        final Vector2 position = new Vector2();
        float orientation;

        @Override
        public Vector2 getPosition() {
            return position;
        }

        @Override
        public float getOrientation() {
            return orientation;
        }

        @Override
        public void setOrientation(float orientation) {
            this.orientation = orientation;
        }

        @Override
        public float vectorToAngle(Vector2 vector) {
            return (float) Math.atan2(-vector.x, vector.y);
        }

        @Override
        public Vector2 angleToVector(Vector2 outVector, float angle) {
            return outVector.set(-(float) Math.sin(angle), (float) Math.cos(angle));
        }

        @Override
        public Location<Vector2> newLocation() {
            return new Point();
        }
    }

    /**
     * The body being steered, reused for every entity. It reports zero velocity and a maximum acceleration equal to its
     * maximum speed, so a behaviour's output is the velocity it wants (TopDownSystem does the accelerating).
     */
    private static final class Agent extends Point implements Steerable<Vector2> {
        final Vector2 velocity = new Vector2();
        float maxSpeed;
        boolean tagged;

        @Override
        public Vector2 getLinearVelocity() {
            return velocity;
        }

        @Override
        public float getAngularVelocity() {
            return 0f;
        }

        @Override
        public float getBoundingRadius() {
            return 0.4f;
        }

        @Override
        public boolean isTagged() {
            return tagged;
        }

        @Override
        public void setTagged(boolean tagged) {
            this.tagged = tagged;
        }

        @Override
        public float getZeroLinearSpeedThreshold() {
            return 0.001f;
        }

        @Override
        public void setZeroLinearSpeedThreshold(float value) {
        }

        @Override
        public float getMaxLinearSpeed() {
            return maxSpeed;
        }

        @Override
        public void setMaxLinearSpeed(float maxLinearSpeed) {
            this.maxSpeed = maxLinearSpeed;
        }

        @Override
        public float getMaxLinearAcceleration() {
            return maxSpeed;
        }

        @Override
        public void setMaxLinearAcceleration(float maxLinearAcceleration) {
        }

        @Override
        public float getMaxAngularSpeed() {
            return 0f;
        }

        @Override
        public void setMaxAngularSpeed(float maxAngularSpeed) {
        }

        @Override
        public float getMaxAngularAcceleration() {
            return 0f;
        }

        @Override
        public void setMaxAngularAcceleration(float maxAngularAcceleration) {
        }
    }
}
