package dev.ember2d.demo.shooter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.app.EngineContext;
import dev.ember2d.ecs.Family;
import dev.ember2d.kit.common.Spawner;
import dev.ember2d.render.Sprite;

/**
 * The crypt (I-340): three waves of enemies from spawners placed in Tiled ({@code maps/room.tmx}); when every spawner
 * is done and no enemy is left, the exit opens and leads to the boss.
 */
final class RoomScene extends StageScene {

    private Family spawners;
    private Family enemies;
    private int exit;
    private boolean open;
    private Label message;
    private float messageLeft;

    RoomScene(Run run) {
        super(run);
    }

    @Override
    protected String mapId() {
        return "map.room";
    }

    @Override
    protected String assetGroup() {
        return "room";
    }

    @Override
    protected void built(EngineContext context) {
        spawners = world.family(Spawner.class);
        enemies = world.family(Enemy.class);
        exit = world.findByName("exit");
        world.get(exit, Sprite.class).tint.a = 0f;   // shown when it opens
        message = new Label("Clear the crypt!", ui().skin(), "title");
        ui().root().row();
        ui().root().add(message).expandX().padBottom(30f);
        messageLeft = 3f;
        context.scenes().preload("boss");   // the boss arena loads while the player fights
    }

    @Override
    protected void tick(float delta) {
        if (messageLeft > 0f) {
            messageLeft -= delta;
            message.setVisible(messageLeft > 0f);
        }
        if (open || enemies.size() > 0) {
            return;
        }
        IntArray all = spawners.entities();
        for (int i = 0; i < all.size; i++) {
            Spawner s = world.get(all.get(i), Spawner.class);
            if (s.total <= 0 || s.spawned < s.total) {
                return;
            }
        }
        open = true;
        world.get(exit, Exit.class).open = true;
        Sprite sprite = world.get(exit, Sprite.class);
        sprite.tint.set(new Color(0.3f, 0.9f, 1f, 1f));
        message.setText("The way is open!");
        message.setVisible(true);
        messageLeft = 3f;
        audio.play("sfx.boss_phase");
        LOG.info("Room cleared at " + (int) run.seconds + " s, score " + run.score);
    }
}
