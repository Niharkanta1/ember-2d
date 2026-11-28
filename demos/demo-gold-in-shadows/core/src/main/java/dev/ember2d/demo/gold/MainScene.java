package dev.ember2d.demo.gold;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.tilemap.TileMap;
import dev.ember2d.tilemap.TileMapRenderer;

/** I-600 placeholder: shows the converted first level around the start, to check the conversion. Replaced in I-620. */
final class MainScene extends Scene {

    private Input input;

    @Override
    protected String assetGroup() {
        return "level1";
    }

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        World world = world();
        context.renderer().setLayers(new RenderLayer("floor", SortMode.NONE), new RenderLayer("world", SortMode.Y_SORT),
                new RenderLayer("overhead", SortMode.NONE));
        TileMap map = TileMap.load(context.assets(), "map.level1", context.renderer().pixelsPerUnit());
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time());
        world.addSystem(sprites);
        new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
        Rectangle bounds = map.bounds(new Rectangle());
        context.renderer().camera().position.set(216f / 16f, bounds.height - 248f / 16f);   // the player's start
    }

    @Override
    protected void update(float delta) {
        if (input.justPressed("quit")) {
            Gdx.app.exit();
        }
    }
}
