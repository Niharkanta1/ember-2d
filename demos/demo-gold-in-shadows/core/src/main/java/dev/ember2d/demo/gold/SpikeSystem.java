package dev.ember2d.demo.gold;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.kit.common.Health;
import dev.ember2d.kit.common.HealthSystem;
import dev.ember2d.render.Sprite;

/**
 * Spike traps (F-029), ported from the original's {@code SpikeTrap} and {@code HazardSystem}: a 3-second cycle (down,
 * a warning frame from 1.4 s, rising from 1.75 s, up and dangerous from 1.9 s to 2.85 s, then lowering), offset per
 * trap by the level's phase so they move in waves. While up, a trap hurts the player (1 damage) when the player's 6 px
 * circle touches the trap's cell shrunk by 3 px, pushing the player away from its centre. Frames are the tileset's
 * hole and spike tiles (row 3, columns 6 to 9).
 */
final class SpikeSystem extends EntitySystem {

    static final float PX = 1f / 16f;
    static final float CYCLE = 3f;
    static final float WARNING = 1.4f;
    static final float RISE = 1.75f;
    static final float UP = 1.9f;
    static final float LOWER = 2.85f;
    static final float INSET = 3f * PX;
    static final float PLAYER_RADIUS = 6f * PX;
    static final float KNOCKBACK = 110f * PX;

    private final HealthSystem health;
    private final Audio audio;
    private final TextureRegion[] frames = new TextureRegion[4];
    private float time;

    SpikeSystem(World world, HealthSystem health, Assets assets, Audio audio) {
        super(Phase.FIXED, world.family(SpikeTrap.class, Transform.class));
        this.health = health;
        this.audio = audio;
        Texture tiles = assets == null ? null : assets.get("tex.tileset", Texture.class);
        for (int i = 0; i < 4 && tiles != null; i++) {
            frames[i] = new TextureRegion(tiles, (6 + i) * 16, 3 * 16, 16, 16);
        }
    }

    /** The tileset column for a time in the cycle (6 down, 7 warning, 8 moving, 9 up). */
    static int column(float t) {
        if (t >= UP && t < LOWER) {
            return 9;
        }
        if (t >= RISE) {
            return 8;
        }
        return t >= WARNING ? 7 : 6;
    }

    static boolean dangerous(float t) {
        return t >= UP && t < LOWER;
    }

    @Override
    protected void begin() {
        time += 1f / 60f;
    }

    @Override
    protected void process(int e, float delta) {
        SpikeTrap trap = world().get(e, SpikeTrap.class);
        Transform t = world().get(e, Transform.class);
        float cycle = ((time + trap.phase) % CYCLE + CYCLE) % CYCLE;
        boolean wasUp = trap.up;
        trap.up = dangerous(cycle);
        Sprite sprite = world().get(e, Sprite.class);
        if (sprite != null && frames[0] != null) {
            sprite.region = frames[column(cycle) - 6];
        }
        // The trap's cell: its position is the bottom centre.
        float cx = t.position.x;
        float cy = t.position.y + 0.5f;
        if (trap.up && !wasUp) {
            audio.playVaried("sfx.spikes", 0.1f);
        }
        if (!trap.up) {
            return;
        }
        int player = world().findByName("player");
        Transform p = player == World.NONE ? null : world().get(player, Transform.class);
        Health h = player == World.NONE ? null : world().get(player, Health.class);
        if (p == null || h == null || h.dead) {
            return;
        }
        float half = 0.5f - INSET;
        float dx = Math.max(Math.abs(p.position.x - cx) - half, 0f);
        float dy = Math.max(Math.abs(p.position.y - cy) - half, 0f);
        if (dx * dx + dy * dy < PLAYER_RADIUS * PLAYER_RADIUS) {
            float ax = p.position.x - cx;
            float ay = p.position.y - cy;
            float len = Math.max(1e-4f, (float) Math.sqrt(ax * ax + ay * ay));
            health.damage(player, 1, e, ax / len * KNOCKBACK, ay / len * KNOCKBACK);
        }
    }
}
