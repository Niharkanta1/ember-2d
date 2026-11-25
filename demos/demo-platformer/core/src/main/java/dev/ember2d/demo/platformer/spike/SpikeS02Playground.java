package dev.ember2d.demo.platformer.spike;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import dev.ember2d.demo.platformer.spike.SpikeMover.Actor;
import dev.ember2d.demo.platformer.spike.SpikeMover.Platform;

/**
 * SPIKE S-02 (throwaway): a playable check of {@link SpikeMover} on the sample TMX map. Arrow keys run, Space jumps,
 * Down + Space drops through one-way tiles, R resets. Raw {@code Gdx.input}; not an Ember2D scene.
 */
public final class SpikeS02Playground extends ApplicationAdapter {

    private static final float TILE = 16f;
    private static final float DT = 1f / 60f;
    private static final float GRAVITY = 40f;
    private static final float RUN = 8f;
    private static final float JUMP = 20f;
    private static final float PLATFORM_SPEED = 2f;

    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private ShapeRenderer shapes;
    private OrthographicCamera camera;
    private FitViewport viewport;
    private SpikeMover mover;
    private Actor player;
    private Platform platform;
    private float platformStartX;
    private float platformDistance;
    private float platformDir = 1f;
    private float accumulator;

    @Override
    public void create() {
        BaseTiledMapLoader.Parameters parameters = new BaseTiledMapLoader.Parameters();
        parameters.projectFilePath = "spike/s02.tiled-project";
        map = new TmxMapLoader().load("spike/s02-level.tmx", parameters);
        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f / TILE);
        shapes = new ShapeRenderer();
        camera = new OrthographicCamera();
        viewport = new FitViewport(40f, 12f, camera);
        reset();
    }

    private void reset() {
        mover = new SpikeMover(SpikeMover.TileGrid.from(map, "ground"));
        MapObject o = map.getLayers().get("platforms").getObjects().get("platform_1");
        float x = o.getProperties().get("x", Float.class) / TILE;
        float y = o.getProperties().get("y", Float.class) / TILE;
        float w = o.getProperties().get("width", Float.class) / TILE;
        float h = o.getProperties().get("height", Float.class) / TILE;
        platform = mover.add(new Platform(x, y, w, h));
        platformStartX = x;
        platformDistance = o.getProperties().get("distance", 6f, Float.class);
        platformDir = 1f;
        player = mover.add(new Actor(2f, 1f, 0.75f, 1.5f));
        accumulator = 0f;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            reset();
        }
        boolean jump = Gdx.input.isKeyJustPressed(Keys.SPACE);
        accumulator += Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        while (accumulator >= DT) {
            fixedStep(jump);
            jump = false;
            accumulator -= DT;
        }

        Gdx.gl.glClearColor(0.12f, 0.13f, 0.18f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        mapRenderer.setView(camera);
        mapRenderer.render();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.SKY);
        shapes.rect(platform.x, platform.y, platform.w, platform.h);
        shapes.setColor(player.grounded ? Color.LIME : Color.ORANGE);
        shapes.rect(player.x, player.y, player.w, player.h);
        shapes.end();
    }

    private void fixedStep(boolean jump) {
        float nextX = platform.x + platformDir * PLATFORM_SPEED * DT;
        if (nextX > platformStartX + platformDistance || nextX < platformStartX) {
            platformDir = -platformDir;
        }
        mover.movePlatform(platform, platformDir * PLATFORM_SPEED * DT, 0f);

        float input = (Gdx.input.isKeyPressed(Keys.RIGHT) ? 1f : 0f) - (Gdx.input.isKeyPressed(Keys.LEFT) ? 1f : 0f);
        player.vx = input * RUN;
        if (jump && player.grounded) {
            if (Gdx.input.isKeyPressed(Keys.DOWN)) {
                player.dropThroughTicks = 6;
            } else {
                player.vy = JUMP;
            }
        }
        player.vy = Math.max(player.vy - GRAVITY * DT, -30f);
        mover.move(player, player.vx * DT, player.vy * DT);
    }

    @Override
    public void dispose() {
        map.dispose();
        mapRenderer.dispose();
        shapes.dispose();
    }
}
