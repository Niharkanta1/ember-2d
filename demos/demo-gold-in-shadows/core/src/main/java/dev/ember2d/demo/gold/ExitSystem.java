package dev.ember2d.demo.gold;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.kit.common.Health;
import dev.ember2d.render.Sprite;

/**
 * The exit door (F-029), ported from the original's {@code ExitDoor}: two tiles in a north wall. When the player comes
 * within 20 px of its front it opens, using a key if it is locked (without one, "FIND THE &" at most every 2 s);
 * opening removes the door's leaf from the walls. Walking into the open doorway queues {@link LevelCleared}. The door is
 * drawn from the tileset (columns 0-1 closed, 2-3 open, rows 6-7). Its position is the bottom centre of the doorway.
 */
final class ExitSystem extends EntitySystem {

    static final float PX = 1f / 16f;
    static final float REACH = 20f * PX;
    static final float LOCKED_NOTICE_SECONDS = 2f;
    static final float LEFT_PILLAR = 6.95f * PX;
    static final float RIGHT_PILLAR = 9.6f * PX;
    static final float EXIT_DEPTH = 5f * PX;
    static final float TEXT_LIFT = 14f * PX;

    private final EventBus events;
    private final Audio audio;
    private final TextureRegion closed;
    private final TextureRegion open;
    private float noticeLeft;
    private final FloatingTexts texts;
    private boolean cleared;
    private final IntArray leaves = new IntArray();

    ExitSystem(World world, EventBus events, Assets assets, Audio audio, FloatingTexts texts) {
        super(Phase.FIXED, world.family(ExitDoor.class, Transform.class));
        this.events = events;
        this.audio = audio;
        this.texts = texts;
        Texture tiles = assets == null ? null : assets.get("tex.tileset", Texture.class);
        closed = tiles == null ? null : new TextureRegion(tiles, 0, 6 * 16, 32, 32);
        open = tiles == null ? null : new TextureRegion(tiles, 2 * 16, 6 * 16, 32, 32);
    }

    @Override
    protected void process(int e, float delta) {
        ExitDoor door = world().get(e, ExitDoor.class);
        Transform t = world().get(e, Transform.class);
        Sprite sprite = world().get(e, Sprite.class);
        if (sprite != null && closed != null) {
            sprite.region = door.open ? open : closed;
        }
        noticeLeft = Math.max(0f, noticeLeft - delta);
        int player = world().findByName("player");
        Transform p = player == World.NONE ? null : world().get(player, Transform.class);
        Health h = player == World.NONE ? null : world().get(player, Health.class);
        if (p == null || h == null || h.dead) {
            return;
        }
        float x = t.position.x;
        float y = t.position.y;
        if (!door.open && p.position.dst(x, y - 6f * PX) <= REACH) {
            Hero hero = world().get(player, Hero.class);
            if (!door.locked) {
                openDoor(door, x, y);
            } else if (hero != null && hero.keys > 0) {
                hero.keys--;
                openDoor(door, x, y);
            } else if (noticeLeft <= 0f) {
                noticeLeft = LOCKED_NOTICE_SECONDS;
                audio.play("sfx.locked");
                say("FIND THE &", x, y, Hud.MAROON);
            }
        }
        boolean inDoorway = p.position.x >= x - 1f + LEFT_PILLAR && p.position.x <= x + RIGHT_PILLAR
                && p.position.y >= y + 1f - EXIT_DEPTH && p.position.y <= y + 2f;
        if (door.open && inDoorway && !cleared) {
            cleared = true;
            events.queue(new LevelCleared());
        }
    }

    private void openDoor(ExitDoor door, float x, float y) {
        door.open = true;
        audio.play("sfx.door");
        say("OPEN!", x, y, Hud.CREAM);
        world().findByTag(Walls.DOOR_TAG, leaves);
        for (int i = 0; i < leaves.size; i++) {
            world().destroy(leaves.get(i));
        }
    }

    /** The original's feedback point: the door's front, lifted 14 px. */
    private void say(String text, float x, float y, com.badlogic.gdx.graphics.Color color) {
        if (texts != null) {
            texts.spawn(text, x, y - 6f * PX + TEXT_LIFT, color);
        }
    }

    /** The player walked out through the open exit door. */
    record LevelCleared() {
    }
}
