package dev.ember2d.box2d;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.Transform;

/**
 * A Box2D rigid body on an entity (F-006B). {@link Box2dSystem} makes the body on the first fixed step after the
 * component is added (placed at the entity's {@code Transform}) and destroys it with the entity. One metre is one
 * world unit.
 *
 * <p>The fields describe the body when it is made; changing them later has no effect. Use the methods to push, stop or
 * move a body that exists. Before the body exists, {@link #setVelocity} sets the start velocity and the other methods
 * do nothing.
 */
@PublicApi
public final class RigidBody {

    public BodyType type = BodyType.DYNAMIC;
    public BodyShape shape = BodyShape.BOX;
    /** Box size in world units. */
    public float width = 1f;
    public float height = 1f;
    /** Circle radius in world units. */
    public float radius = 0.5f;
    /** The shape's centre relative to the entity's position. */
    public final Vector2 offset = new Vector2();
    public float density = 1f;
    public float friction = 0.4f;
    public float restitution;
    /** Reports overlaps as {@code TriggerEntered}/{@code TriggerExited}; never pushes or is pushed. */
    public boolean sensor;
    public boolean fixedRotation;
    /** Continuous collision for fast bodies, so they don't pass through thin walls. */
    public boolean bullet;
    public float gravityScale = 1f;
    public float linearDamping;
    public float angularDamping;
    /** Collision filter: this body's category bits, the categories it collides with, and a group (F-006B). */
    public int category = 1;
    public int mask = 0xFFFF;
    public int group;
    /** Start velocity in world units per second, applied once when the body is made. */
    public float velocityX;
    public float velocityY;
    /** Start angular velocity in degrees per second. */
    public float angularVelocity;

    // Runtime, set by Box2dSystem.
    Body body;
    Transform transform;

    /** True once {@link Box2dSystem} has made the body. */
    public boolean hasBody() {
        return body != null;
    }

    /** An instant push at the centre, in mass x world units per second (kg m/s). */
    public void applyImpulse(float x, float y) {
        if (body != null) {
            body.applyLinearImpulse(x, y, body.getWorldCenter().x, body.getWorldCenter().y, true);
        }
    }

    /** A force at the centre for the next step, in newtons. */
    public void applyForce(float x, float y) {
        if (body != null) {
            body.applyForceToCenter(x, y, true);
        }
    }

    /** A turning force for the next step. */
    public void applyTorque(float torque) {
        if (body != null) {
            body.applyTorque(torque, true);
        }
    }

    /** Sets the velocity in world units per second (before the body exists: the start velocity). */
    public void setVelocity(float x, float y) {
        if (body != null) {
            body.setLinearVelocity(x, y);
            body.setAwake(true);
        } else {
            velocityX = x;
            velocityY = y;
        }
    }

    /** Writes the velocity in world units per second into {@code out} (the start velocity before the body exists). */
    public Vector2 velocity(Vector2 out) {
        if (body == null) {
            return out.set(velocityX, velocityY);
        }
        return out.set(body.getLinearVelocity());
    }

    /** Angular velocity in degrees per second. */
    public float angularVelocity() {
        return body == null ? angularVelocity : body.getAngularVelocity() * MathUtils.radiansToDegrees;
    }

    /** Sets the angular velocity in degrees per second. */
    public void setAngularVelocity(float degreesPerSecond) {
        if (body != null) {
            body.setAngularVelocity(degreesPerSecond * MathUtils.degreesToRadians);
            body.setAwake(true);
        } else {
            angularVelocity = degreesPerSecond;
        }
    }

    /** Moves the body (and its {@code Transform}, without interpolating across the jump) and wakes it. */
    public void teleport(float x, float y, float degrees) {
        if (body != null) {
            body.setTransform(x, y, degrees * MathUtils.degreesToRadians);
            body.setAwake(true);
        }
        if (transform != null) {
            transform.teleport(x, y);
            transform.rotation = degrees;
            transform.previousRotation = degrees;
        }
    }

    /** False when the body is asleep (at rest); also false before it exists. */
    public boolean isAwake() {
        return body != null && body.isAwake();
    }
}
