package dev.ember2d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.render.internal.GdxRenderer;
import dev.ember2d.render.internal.RenderTarget;
import java.util.ArrayList;
import java.util.List;

/** Test support for rendering: a recording batch and render target around a real {@link GdxRenderer}. */
public final class RenderTestKit {

    /** One recorded call: method name and arguments (colours copied at call time). */
    public record Call(String name, Object[] args) {
        public float f(int index) {
            return (Float) args[index];
        }
    }

    /** A render target that records begin/end/resize. */
    public static final class FakeTarget implements RenderTarget {
        public final List<String> events = new ArrayList<>();
        public final TextureRegion region = new TextureRegion();
        public int width;
        public int height;

        @Override
        public void resize(int width, int height) {
            this.width = width;
            this.height = height;
            events.add("resize " + width + "x" + height);
        }

        @Override
        public void begin() {
            events.add("begin");
        }

        @Override
        public void end() {
            events.add("end");
        }

        @Override
        public TextureRegion region() {
            return region;
        }

        @Override
        public void dispose() {
            events.add("dispose");
        }
    }

    public final List<Call> calls = new ArrayList<>();
    public final Batch batch = HeadlessGdx.recorder(Batch.class, (name, args) -> {
        Object[] copy = args.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] instanceof Color color) {
                copy[i] = new Color(color);
            }
        }
        calls.add(new Call(name, copy));
    });
    public final FakeTarget target = new FakeTarget();
    public final TimeState time = new TimeState(1f / 60f);

    public GdxRenderer renderer(RenderMode mode) {
        return new GdxRenderer(320, 180, 16f, mode, new Color(0.1f, 0.2f, 0.3f, 1f), time, batch, target);
    }

    /** Recorded calls with this method name. */
    public List<Call> calls(String name) {
        return calls.stream().filter(c -> c.name().equals(name)).toList();
    }
}
