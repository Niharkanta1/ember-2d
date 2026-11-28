package dev.ember2d.demo.gold;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.kit.topdown.vision.FogLayer;
import dev.ember2d.kit.topdown.vision.Viewer;
import dev.ember2d.kit.topdown.vision.VisionField;
import dev.ember2d.kit.topdown.vision.VisionSettings;
import dev.ember2d.kit.topdown.vision.VisionSystem;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.tilemap.TileMap;
import dev.ember2d.tilemap.TileMapRenderer;

/** Placeholder (I-600, I-610): the first level around the start with the field of view turning slowly. Replaced in I-620. */
final class MainScene extends Scene {

    private Input input;
    private Viewer viewer;
    private FogLayer fog;

    @Override
    protected String assetGroup() {
        return "level1";
    }

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        World world = world();
        context.renderer().setLayers(new RenderLayer("floor", SortMode.NONE), new RenderLayer("world", SortMode.Y_SORT),
                new RenderLayer("overhead", SortMode.NONE), new RenderLayer("fog", SortMode.NONE));
        TileMap map = TileMap.load(context.assets(), "map.level1", context.renderer().pixelsPerUnit());
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time());
        world.addSystem(sprites);
        new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
        Rectangle bounds = map.bounds(new Rectangle());
        float x = 216f / 16f;
        float y = bounds.height - 248f / 16f;   // the player's start
        context.renderer().camera().position.set(x, y);
        Collision collision = new Collision(world, context.events());
        world.addSystem(new CollisionSystem(world, collision));
        Walls.build(world, map);
        VisionField field = new VisionField(collision, Walls.SIGHT_MASK, new VisionSettings());
        world.addSystem(new VisionSystem(world, field, context.events()));
        int eye = world.create();
        world.add(eye, new Transform()).teleport(x, y);
        viewer = world.add(eye, new Viewer());
        fog = new FogLayer(field, context.renderer().camera(), "fog");
        sprites.addLayerContent(fog);
    }

    @Override
    protected void update(float delta) {
        viewer.angle = (viewer.angle + 30f * delta) % 360f;   // look around slowly
        if (input.justPressed("quit")) {
            Gdx.app.exit();
        }
    }

    @Override
    protected void exit() {
        fog.dispose();
    }
}
