package dev.ember2d.demo.platformer.spike;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.lang.reflect.Proxy;

/** SPIKE S-02 test support: libGDX without a window (copy of ember-core's HeadlessGdx; spike only). */
final class SpikeGdx {

    static final String TMX = "assets/spike/s02-level.tmx";
    static final String TMJ = "assets/spike/s02-level.tmj";
    static final String PROJECT = "assets/spike/s02.tiled-project";

    private SpikeGdx() {
    }

    static void install() {
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.graphics = new MockGraphics();
        GL20 gl = stub(GL20.class);
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.app = stub(Application.class);
    }

    static TiledMap tmx() {
        com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters p = new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters();
        p.projectFilePath = PROJECT;
        return new com.badlogic.gdx.maps.tiled.TmxMapLoader().load(TMX, p);
    }

    static TiledMap tmj() {
        com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters p = new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters();
        p.projectFilePath = PROJECT;
        return new com.badlogic.gdx.maps.tiled.TmjMapLoader().load(TMJ, p);
    }

    @SuppressWarnings("unchecked")
    static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Class<?> r = method.getReturnType();
            if (r == boolean.class) {
                return false;
            } else if (r == int.class) {
                return 0;
            } else if (r == long.class) {
                return 0L;
            } else if (r == float.class) {
                return 0f;
            } else if (r == String.class) {
                return "";
            } else if (r == Application.ApplicationType.class) {
                return Application.ApplicationType.HeadlessDesktop;
            }
            return null;
        });
    }
}
