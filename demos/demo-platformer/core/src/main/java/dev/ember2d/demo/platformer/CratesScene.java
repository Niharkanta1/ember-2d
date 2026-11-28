package dev.ember2d.demo.platformer;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.box2d.Box2dDebugSystem;
import dev.ember2d.box2d.Box2dSystem;
import dev.ember2d.box2d.RigidBody;
import dev.ember2d.core.Log;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.tilemap.TileMap;
import dev.ember2d.tilemap.TileMapRenderer;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * Box2D crates (F-006B, I-500): three stacks of crates on the arena's tile floor. A click or tap drops another crate
 * at the pointer; Reset (R) rebuilds the stacks; Back (Escape) returns to the menu. F3 draws the bodies.
 */
final class CratesScene extends Scene {

    private static final Log LOG = Log.get("crates");
    /** The crate image is 28 x 24 pixels with a centred 20 x 20 crate in it; the body is the crate. */
    private static final float CRATE_W = 20f / 16f;
    private static final float CRATE_H = 20f / 16f;
    private static final int MAX_CRATES = 80;

    private Input input;
    private Box2dSystem physics;
    private TextureRegion crate;
    private final Rectangle bounds = new Rectangle();
    private final Vector2 pointer = new Vector2();
    private Label count;

    @Override
    protected String assetGroup() {
        return "crates";
    }

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        World world = world();
        context.renderer().setLayers(new RenderLayer("background", SortMode.NONE), new RenderLayer("world", SortMode.Z));
        TileMap map = TileMap.load(context.assets(), "map.crates", context.renderer().pixelsPerUnit());
        map.bounds(bounds);
        context.renderer().camera().position.set(bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f);

        physics = new Box2dSystem(world, context.events(), context.time(), 0f, -20f);
        physics.addTileGrid(map.collision());
        world.addSystem(physics);
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time());
        world.addSystem(sprites);
        new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
        world.addSystem(new Box2dDebugSystem(world, physics, context.debug()));

        crate = new TextureRegion(context.assets().get("tex.level_crate", Texture.class));
        stack();

        Ui ui = ui();
        DemoUi.useSkin(ui, context.assets());
        Table panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(4f, 6f, 4f, 6f);
        panel.setTouchable(Touchable.enabled);   // taps on the panel background must not drop crates
        count = new Label("", ui.skin());
        panel.add(count).left().row();
        MenuList menu = new MenuList(ui);
        menu.add("Reset", this::stack);
        menu.add("Back", () -> back(context));
        menu.onBack(() -> back(context));
        panel.add(menu).growX();
        ui.root().top().right().pad(4f);
        ui.root().add(panel);
        LOG.info("Crates: tap or click to drop a crate, R to reset");
    }

    /** Three stacks: 7 in the middle (as tall as the view allows), 5 on each side. */
    private void stack() {
        World world = world();
        for (int e : world.family(RigidBody.class).entities().toArray()) {
            world.destroy(e);
        }
        float floor = bounds.y + 2f;   // the arena's floor is two tiles deep
        float mid = bounds.x + bounds.width / 2f;
        column(mid, floor, 7);
        column(mid - 5f, floor, 5);
        column(mid + 5f, floor, 5);
    }

    private void column(float x, float floor, int n) {
        for (int i = 0; i < n; i++) {
            spawn(x, floor + CRATE_H / 2f + i * (CRATE_H + 0.01f), 0f);
        }
    }

    private void spawn(float x, float y, float degrees) {
        World world = world();
        int e = world.create();
        Transform t = world.add(e, new Transform());
        t.teleport(x, y);
        t.rotation = degrees;
        t.previousRotation = degrees;
        Sprite sprite = world.add(e, new Sprite());
        sprite.region = crate;
        sprite.origin.set(crate.getRegionWidth() / 2f, crate.getRegionHeight() / 2f);
        RigidBody body = world.add(e, new RigidBody());
        body.width = CRATE_W;
        body.height = CRATE_H;
        body.friction = 0.6f;
    }

    private void back(EngineContext context) {
        context.scenes().replace(new MenuScene(), Transition.fade(0.4f));
    }

    @Override
    protected void update(float delta) {
        if (input.justPressed("crates_reset")) {
            stack();
        }
        int crates = world().family(RigidBody.class).size();
        if (input.pointerJustDown(0) && crates < MAX_CRATES) {
            input.pointerScreen(0, pointer);
            Vector2 stage = ui().stage().screenToStageCoordinates(pointer.cpy());
            if (ui().stage().hit(stage.x, stage.y, true) == null) {   // not on the panel
                input.pointerWorld(0, pointer);
                if (bounds.contains(pointer)) {
                    spawn(pointer.x, pointer.y, MathUtils.random(-30f, 30f));
                }
            }
        }
        count.setText("Crates " + crates + "/" + MAX_CRATES);
    }

    @Override
    protected void exit() {
        physics.dispose();
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
