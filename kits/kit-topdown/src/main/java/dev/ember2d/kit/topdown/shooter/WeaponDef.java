package dev.ember2d.kit.topdown.shooter;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.core.PublicApi;
import dev.ember2d.kit.bullets.BulletPatterns;
import dev.ember2d.kit.bullets.BulletTypes;
import dev.ember2d.kit.bullets.Emitter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A weapon definition (F-016), parsed from JSON:
 * <pre>{@code { "version": 1, "fireRate": 8, "auto": true, "magazine": 24, "reload": 1.2, "muzzle": 0.6,
 *   "emit": { "type": "arc", "bullet": "pellet", "count": 3, "spread": 12, "speed": 14 } } }</pre>
 * {@code fireRate} (shots per second) and {@code emit} (a kit-bullets emitter; its angle is replaced by the aim) are
 * required; {@code auto} false, {@code magazine} 0 (unlimited), {@code reload} 1 second, {@code muzzle} 0.5.
 */
@PublicApi
public final class WeaponDef {

    private static final Set<String> FIELDS = Set.of("version", "fireRate", "auto", "magazine", "reload", "muzzle",
            "emit");

    public final String id;
    public final float fireRate;
    public final boolean auto;
    public final int magazine;
    public final float reload;
    public final float muzzle;
    public final Emitter emitter;

    private WeaponDef(String id, float fireRate, boolean auto, int magazine, float reload, float muzzle,
            Emitter emitter) {
        this.id = id;
        this.fireRate = fireRate;
        this.auto = auto;
        this.magazine = magazine;
        this.reload = reload;
        this.muzzle = muzzle;
        this.emitter = emitter;
    }

    /**
     * Parses a weapon file.
     *
     * @throws IllegalArgumentException listing every problem, naming the file
     */
    public static WeaponDef parse(String id, JsonValue json, String file, BulletTypes types) {
        List<String> problems = new ArrayList<>();
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException(file + ": must hold a JSON object");
        }
        for (JsonValue f = json.child; f != null; f = f.next) {
            if (!FIELDS.contains(f.name)) {
                problems.add(file + ": unknown field \"" + f.name + "\" (fields: " + FIELDS + ")");
            }
        }
        if (json.getInt("version", 0) != 1) {
            problems.add(file + ": \"version\" must be 1");
        }
        JsonValue rate = json.get("fireRate");
        if (rate == null || !rate.isNumber() || rate.asFloat() <= 0f) {
            problems.add(file + ": \"fireRate\" is required: shots per second, > 0");
        }
        int magazine = json.getInt("magazine", 0);
        float reload = json.getFloat("reload", 1f);
        if (magazine < 0 || reload < 0f) {
            problems.add(file + ": \"magazine\" and \"reload\" must be >= 0");
        }
        Emitter emitter = null;
        try {
            emitter = BulletPatterns.parseEmitter(json.get("emit"), file + ": emit", types);
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage().replace("Bad emitter:\n  ", ""));
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Bad weapon \"" + id + "\":\n  " + String.join("\n  ", problems));
        }
        return new WeaponDef(id, rate.asFloat(), json.getBoolean("auto", false), magazine, reload,
                json.getFloat("muzzle", 0.5f), emitter);
    }
}
