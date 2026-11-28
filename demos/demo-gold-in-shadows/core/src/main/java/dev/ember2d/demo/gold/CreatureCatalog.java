package dev.ember2d.demo.gold;

import com.badlogic.gdx.utils.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The four creatures' values (F-029), from {@code creatures.json}: the original's {@code EnemyCatalog} as data.
 * The file uses the original's pixels; everything here is in world units (16 pixels each), seconds and degrees.
 */
final class CreatureCatalog {

    private static final float PX = 1f / 16f;

    /** One creature's values. */
    static final class Def {
        String kind;
        int health;
        float speed;
        float acceleration;
        float detect;
        float lose;
        boolean lineOfSight;
        float stun;
        float knockback;
        boolean interruptible;
        float radius;
        int gold;
        // behaviour when it has a target
        String behavior;
        float stop;
        float behaviorSpeed;
        float preferred;
        float tolerance;
        boolean chaseWithoutSight;
        boolean strafe;
        float strafeAngle;
        float minSwitch;
        float maxSwitch;
        // behaviour without a target
        String idle;
        float wanderRadius;
        float minWait;
        float maxWait;
        float idleSpeed;
        float patrolSpeed;
        // attack
        String attack;
        int damage;
        float range;
        float windup;
        float recovery;
        float cooldown;
        boolean attackSight;
        float leeway;
        float lunge;
        String bullet;
        float bulletSpeed;
        int count;
        float spread;
        float offset;
        float dashSpeed;
        float padding;
        // look
        float walkFrame;
        float idleFrame;
        boolean wobble;
        String telegraph;
        float dormantAlpha;
    }

    private final Map<String, Def> defs = new LinkedHashMap<>();

    CreatureCatalog(JsonValue json) {
        JsonValue all = json.get("creatures");
        for (JsonValue c = all.child; c != null; c = c.next) {
            Def d = new Def();
            d.kind = c.name;
            d.health = c.getInt("health");
            d.speed = c.getFloat("speed") * PX;
            d.acceleration = c.getFloat("acceleration");
            d.detect = c.getFloat("detect") * PX;
            d.lose = c.getFloat("lose") * PX;
            d.lineOfSight = c.getBoolean("lineOfSight", true);
            d.stun = c.getFloat("stun");
            d.knockback = c.getFloat("knockback") * PX;
            d.interruptible = c.getBoolean("interruptible", false);
            d.radius = c.getFloat("radius") * PX;
            d.gold = c.getInt("gold");
            JsonValue b = c.get("behavior");
            d.behavior = b.getString("type");
            d.stop = b.getFloat("stop", 0f) * PX;
            d.behaviorSpeed = b.getFloat("speed", 1f);
            d.preferred = b.getFloat("preferred", 0f) * PX;
            d.tolerance = b.getFloat("tolerance", 0f) * PX;
            d.chaseWithoutSight = b.getBoolean("chaseWithoutSight", false);
            d.strafe = b.getBoolean("strafe", false);
            d.strafeAngle = b.getFloat("strafeAngle", 0f);
            d.minSwitch = b.getFloat("minSwitch", 1f);
            d.maxSwitch = b.getFloat("maxSwitch", 1f);
            JsonValue i = c.get("idle");
            d.idle = i.getString("type");
            d.wanderRadius = i.getFloat("radius", 0f) * PX;
            d.minWait = i.getFloat("minWait", 1f);
            d.maxWait = i.getFloat("maxWait", 1f);
            d.idleSpeed = i.getFloat("speed", 1f);
            d.patrolSpeed = i.getFloat("patrolSpeed", 1f);
            JsonValue a = c.get("attack");
            d.attack = a.getString("type");
            d.damage = a.getInt("damage");
            d.range = a.getFloat("range") * PX;
            d.windup = a.getFloat("windup");
            d.recovery = a.getFloat("recovery");
            d.cooldown = a.getFloat("cooldown");
            d.attackSight = a.getBoolean("lineOfSight", true);
            d.leeway = a.getFloat("leeway", 0f) * PX;
            d.lunge = a.getFloat("lunge", 0f) * PX;
            d.bullet = a.getString("bullet", null);
            d.bulletSpeed = a.getFloat("bulletSpeed", 0f) * PX;
            d.count = a.getInt("count", 1);
            d.spread = a.getFloat("spread", 0f);
            d.offset = a.getFloat("offset", 0f) * PX;
            d.dashSpeed = a.getFloat("dashSpeed", 0f) * PX;
            d.padding = a.getFloat("padding", 0f) * PX;
            JsonValue l = c.get("look");
            d.walkFrame = l.getFloat("walkFrame");
            d.idleFrame = l.getFloat("idleFrame");
            d.wobble = l.getBoolean("wobble", false);
            d.telegraph = l.getString("telegraph");
            d.dormantAlpha = l.getFloat("dormantAlpha", 1f);
            defs.put(d.kind, d);
        }
    }

    Def get(String kind) {
        Def d = defs.get(kind);
        if (d == null) {
            throw new IllegalArgumentException("Unknown creature \"" + kind + "\" (known: " + defs.keySet() + ")");
        }
        return d;
    }

    int size() {
        return defs.size();
    }
}
