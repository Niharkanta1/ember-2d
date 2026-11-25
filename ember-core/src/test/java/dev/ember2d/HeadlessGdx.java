package dev.ember2d;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.backends.headless.mock.audio.MockAudio;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.lang.reflect.Proxy;
import java.util.function.BiConsumer;

/**
 * Test support: libGDX without a window. Headless files and mock audio and graphics, plus do-nothing stand-ins for
 * OpenGL and the application built with {@link Proxy} (test code only), so textures, atlases and fonts can load.
 * Real shaders cannot compile against the stand-in, so tests use a recording {@code Batch} instead of
 * {@code SpriteBatch}.
 */
public final class HeadlessGdx {

    private HeadlessGdx() {
    }

    public static void install() {
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.audio = new MockAudio();
        Gdx.graphics = new MockGraphics();
        GL20 gl = stub(GL20.class);
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.app = stub(Application.class);
    }

    public static void clear() {
        Gdx.files = null;
        Gdx.audio = null;
        Gdx.graphics = null;
        Gdx.gl = null;
        Gdx.gl20 = null;
        Gdx.app = null;
    }

    /** An interface implementation whose methods do nothing and return defaults. */
    public static <T> T stub(Class<T> type) {
        return recorder(type, (name, args) -> { });
    }

    /** Like {@link #stub(Class)}, but reports every call (method name and arguments) to {@code calls}. */
    @SuppressWarnings("unchecked")
    public static <T> T recorder(Class<T> type, BiConsumer<String, Object[]> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            calls.accept(method.getName(), args == null ? new Object[0] : args);
            Class<?> r = method.getReturnType();
            if (r == boolean.class) {
                return false;
            } else if (r == int.class) {
                return 0;
            } else if (r == long.class) {
                return 0L;
            } else if (r == float.class) {
                return 0f;
            } else if (r == double.class) {
                return 0d;
            } else if (r == String.class) {
                return "";
            } else if (r == Application.ApplicationType.class) {
                return Application.ApplicationType.HeadlessDesktop;
            }
            return null;
        });
    }
}
