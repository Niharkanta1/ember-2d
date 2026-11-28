package dev.ember2d.demo.gold;

import com.badlogic.gdx.math.MathUtils;
import dev.ember2d.animation.Animator;
import dev.ember2d.audio.Audio;
import dev.ember2d.core.Phase;
import dev.ember2d.data.PrefabLibrary;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.kit.common.Health;
import dev.ember2d.render.Sprite;
import java.util.Map;

/**
 * Collecting (F-029), ported from the original's {@code CollectionSystem}, {@code Collectible} and {@code Chest}. In
 * {@code FIXED}:
 * <ul>
 *   <li>items bob gently; dropped ones fly out, slow down (friction 6) and wait 0.45 s before they can be taken;</li>
 *   <li>gold (coins, piles, pouches) within 28 px drifts to the player at 90 px/s;</li>
 *   <li>the player takes an item within 10 px: gold adds to the score, a medkit heals 2 hearts (and stays while health
 *       is full), a key is carried for the exit door; every item is taken once, with a floating "+10", "+{" or "KEY!";</li>
 *   <li>the interact action (E) opens a chest within 22 px: 4 coins and a 20-gold pouch spill out.</li>
 * </ul>
 */
final class PickupSystem extends EntitySystem {

    static final float PX = 1f / 16f;
    static final float PICKUP_RADIUS = 10f * PX;
    static final float MAGNET_RADIUS = 28f * PX;
    static final float MAGNET_SPEED = 90f * PX;
    static final float INTERACT_RADIUS = 22f * PX;
    static final float DROP_DELAY = 0.45f;
    static final float DROP_SPEED_MIN = 40f * PX;
    static final float DROP_SPEED_MAX = 75f * PX;
    static final float FRICTION = 6f;
    static final float BOB_SPEED = 4f;
    static final float BOB_HEIGHT = 1.5f;
    static final int CHEST_COINS = 4;
    static final int CHEST_POUCH_GOLD = 20;
    static final float TEXT_LIFT = 8f * PX;

    private final Campaign campaign;
    private final Input input;
    private final PrefabLibrary prefabs;
    private final Audio audio;
    private final Family chests;
    private final FloatingTexts texts;
    private float time;
    private int player;
    private boolean chestInReach;

    PickupSystem(World world, Campaign campaign, Input input, PrefabLibrary prefabs, Audio audio,
            FloatingTexts texts) {
        super(Phase.FIXED, world.family(Pickup.class, Transform.class));
        this.campaign = campaign;
        this.input = input;
        this.prefabs = prefabs;
        this.audio = audio;
        this.chests = world.family(Chest.class, Transform.class);
        this.texts = texts;
    }

    /** True while a closed chest is within reach (for the HUD's hint). */
    boolean chestInReach() {
        return chestInReach;
    }

    @Override
    protected void begin() {
        time += 1f / 60f;
        player = world().findByName("player");
        chestInReach = false;
        Transform p = playerTransform();
        if (p == null || isDead()) {
            return;
        }
        for (int i = 0; i < chests.size(); i++) {
            int e = chests.entities().get(i);
            Chest chest = world().get(e, Chest.class);
            Transform t = world().get(e, Transform.class);
            // the chest's centre is half a tile above its bottom-centre position
            if (chest.open || p.position.dst(t.position.x, t.position.y + 0.5f) > INTERACT_RADIUS) {
                continue;
            }
            chestInReach = true;
            if (input.justPressed("interact")) {
                open(e, chest, t);
            }
        }
    }

    @Override
    protected void process(int e, float delta) {
        Pickup item = world().get(e, Pickup.class);
        Transform t = world().get(e, Transform.class);
        if (Float.isNaN(item.bob)) {
            item.bob = t.position.x * 16f * 0.05f;   // the original's phase per pixel, so items don't bob in step
        }
        Sprite sprite = world().get(e, Sprite.class);
        if (sprite != null) {
            sprite.origin.y = 8f - BOB_HEIGHT * MathUtils.sin(time * BOB_SPEED + item.bob);
        }
        if (item.vx != 0f || item.vy != 0f) {
            t.position.add(item.vx * delta, item.vy * delta);
            float k = Math.max(0f, 1f - FRICTION * delta);
            item.vx *= k;
            item.vy *= k;
            if (Math.abs(item.vx) + Math.abs(item.vy) < 0.01f) {
                item.vx = 0f;
                item.vy = 0f;
            }
        }
        if (item.delay > 0f) {
            item.delay -= delta;
            return;
        }
        Transform p = playerTransform();
        if (p == null || isDead()) {
            return;
        }
        float dist = t.position.dst(p.position);
        boolean gold = isGold(item.kind);
        if (gold && dist <= MAGNET_RADIUS && dist > 1e-4f) {
            float step = Math.min(dist, MAGNET_SPEED * delta);
            t.position.add((p.position.x - t.position.x) / dist * step, (p.position.y - t.position.y) / dist * step);
            dist = t.position.dst(p.position);
        }
        if (dist <= PICKUP_RADIUS) {
            take(e, item, t);
        }
    }

    static boolean isGold(String kind) {
        return kind.equals("coin") || kind.equals("pile") || kind.equals("pouch");
    }

    private void take(int e, Pickup item, Transform t) {
        float tx = t.position.x;
        float ty = t.position.y + TEXT_LIFT;
        switch (item.kind) {
            case "coin", "pile", "pouch" -> {
                campaign.gold += item.amount;
                audio.playVaried(item.kind.equals("coin") ? "sfx.coin" : "sfx.gold", 0.05f);
                say("+" + item.amount, tx, ty, Hud.TAN);
            }
            case "medkit" -> {
                Health h = world().get(player, Health.class);
                if (h == null || h.current >= h.max) {
                    return;   // left in place while health is full
                }
                h.current = Math.min(h.max, h.current + item.amount);
                audio.play("sfx.heal");
                say("+{", tx, ty, Hud.MAROON);
            }
            case "key" -> {
                Hero hero = world().get(player, Hero.class);
                if (hero != null) {
                    hero.keys++;
                }
                audio.play("sfx.key");
                say("KEY!", tx, ty, Hud.CREAM);
            }
            default -> throw new IllegalStateException("Unknown pickup \"" + item.kind + "\"");
        }
        world().destroy(e);
    }

    private void open(int e, Chest chest, Transform t) {
        chest.open = true;
        Animator animator = world().get(e, Animator.class);
        if (animator != null) {
            animator.play("chest_open");
        }
        audio.play("sfx.chest");
        float cx = t.position.x;
        float cy = t.position.y + 0.5f;
        for (int i = 0; i < CHEST_COINS; i++) {
            drop(world(), prefabs, "pickup/coin", cx, cy, -1);
        }
        drop(world(), prefabs, "pickup/pouch", cx, cy, CHEST_POUCH_GOLD);
    }

    /**
     * Drops an item that flies out in a random direction and can be taken after a moment. Works while a system runs
     * (the values go in as prefab overrides, not as changes to queued components).
     *
     * @param amount the item's amount, or -1 to keep the prefab's
     */
    static int drop(World world, PrefabLibrary prefabs, String prefab, float x, float y, int amount) {
        float angle = MathUtils.random(MathUtils.PI2);
        float speed = MathUtils.random(DROP_SPEED_MIN, DROP_SPEED_MAX);
        Map<String, Object> overrides = new java.util.HashMap<>();
        overrides.put("Pickup.vx", MathUtils.cos(angle) * speed);
        overrides.put("Pickup.vy", MathUtils.sin(angle) * speed);
        overrides.put("Pickup.delay", DROP_DELAY);
        if (amount >= 0) {
            overrides.put("Pickup.amount", amount);
        }
        return prefabs.spawn(world, prefab, x, y, overrides);
    }

    private void say(String text, float x, float y, com.badlogic.gdx.graphics.Color color) {
        if (texts != null) {
            texts.spawn(text, x, y, color);
        }
    }

    private Transform playerTransform() {
        return player == World.NONE ? null : world().get(player, Transform.class);
    }

    private boolean isDead() {
        Health h = world().get(player, Health.class);
        return h != null && h.dead;
    }
}
