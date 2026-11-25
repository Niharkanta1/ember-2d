package dev.ember2d.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import dev.ember2d.core.PublicApi;

/**
 * Lightweight diagnostics that work in any build (F-014): a metrics panel and debug shapes drawn over the world.
 * Available as {@code context.debug()}; always present, and its methods do nothing while the overlay is hidden or
 * unavailable, so game code never checks first. Toggle with F3 in dev builds.
 *
 * <pre>{@code
 * context.debug().rect(collider.x, collider.y, collider.width, collider.height, Color.GREEN);
 * }</pre>
 */
@PublicApi
public final class DebugOverlay {

    private static final Color PANEL_BACKGROUND = new Color(0f, 0f, 0f, 0.6f);
    private static final Color TEXT = new Color(Color.WHITE);
    private static final float PADDING = 6f;
    private static final int LINE = 0;
    private static final int RECT = 1;
    private static final int CIRCLE = 2;

    private final boolean available;
    private boolean visible;
    private final FloatArray shapeData = new FloatArray(false, 64);
    private final Array<Color> shapeColors = new Array<>(false, 16);
    private final Matrix4 screen = new Matrix4();
    private final StringBuilder text = new StringBuilder(64);

    // Metrics: a one-second window, published when the window closes.
    private int windowFrames;
    private float windowSeconds;
    private float windowFrameSum;
    private float windowFrameMax;
    private double windowEngineSum;
    private double windowEngineMax;
    private long lastFixedTick = -1;
    private int fps;
    private float frameAverageMs;
    private float frameMaxMs;
    private float engineAverageMs;
    private float engineMaxMs;
    private long stepsLastFrame;
    private long droppedSteps;
    private int entities;
    private int renderCalls;

    /**
     * @param available whether the overlay can be shown (dev mode, or allowed in release by config)
     * @param visibleAtStart whether it starts visible (ignored when not available)
     */
    public DebugOverlay(boolean available, boolean visibleAtStart) {
        this.available = available;
        this.visible = available && visibleAtStart;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isVisible() {
        return visible;
    }

    /** Shows or hides the overlay; showing does nothing when the overlay is not available. */
    public void setVisible(boolean visible) {
        boolean show = available && visible;
        if (show && !this.visible) {
            resetMetrics();
        }
        this.visible = show;
        if (!show) {
            shapeData.clear();
            shapeColors.clear();
        }
    }

    public void toggle() {
        setVisible(!visible);
    }

    /** Outlines a rectangle in world units for this frame. */
    public void rect(float x, float y, float width, float height, Color color) {
        if (visible) {
            addShape(RECT, x, y, width, height, color);
        }
    }

    /** Draws a line in world units for this frame. */
    public void line(float x1, float y1, float x2, float y2, Color color) {
        if (visible) {
            addShape(LINE, x1, y1, x2, y2, color);
        }
    }

    /** Outlines a circle in world units for this frame. */
    public void circle(float x, float y, float radius, Color color) {
        if (visible) {
            addShape(CIRCLE, x, y, radius, 0f, color);
        }
    }

    /**
     * Engine hook, once per frame after the frame ran: feeds the metrics. Does nothing while hidden.
     *
     * @param frameSeconds real time since the previous frame
     * @param engineNanos CPU time the engine spent on this frame
     */
    public void recordFrame(float frameSeconds, long engineNanos, long fixedTick, long droppedTotal, int entityCount,
            int renderCallCount) {
        if (!visible) {
            return;
        }
        stepsLastFrame = lastFixedTick < 0 ? 0 : fixedTick - lastFixedTick;
        lastFixedTick = fixedTick;
        droppedSteps = droppedTotal;
        entities = entityCount;
        renderCalls = renderCallCount;
        windowFrames++;
        windowSeconds += frameSeconds;
        windowFrameSum += frameSeconds;
        windowFrameMax = Math.max(windowFrameMax, frameSeconds);
        windowEngineSum += engineNanos;
        windowEngineMax = Math.max(windowEngineMax, engineNanos);
        if (windowSeconds >= 1f) {
            fps = Math.round(windowFrames / windowSeconds);
            frameAverageMs = windowFrameSum / windowFrames * 1000f;
            frameMaxMs = windowFrameMax * 1000f;
            engineAverageMs = (float) (windowEngineSum / windowFrames / 1_000_000d);
            engineMaxMs = (float) (windowEngineMax / 1_000_000d);
            windowFrames = 0;
            windowSeconds = 0f;
            windowFrameSum = 0f;
            windowFrameMax = 0f;
            windowEngineSum = 0d;
            windowEngineMax = 0d;
        }
    }

    /** Engine hook: draws this frame's shapes with the world camera, then forgets them. */
    public void renderWorld(DebugCanvas canvas, Matrix4 worldProjection) {
        if (!visible || shapeColors.size == 0) {
            shapeData.clear();
            shapeColors.clear();
            return;
        }
        canvas.beginLines(worldProjection);
        float[] d = shapeData.items;
        for (int i = 0; i < shapeColors.size; i++) {
            int o = i * 5;
            Color color = shapeColors.get(i);
            switch ((int) d[o]) {
                case LINE -> canvas.line(d[o + 1], d[o + 2], d[o + 3], d[o + 4], color);
                case RECT -> canvas.rect(d[o + 1], d[o + 2], d[o + 3], d[o + 4], color);
                default -> canvas.circle(d[o + 1], d[o + 2], d[o + 3], color);
            }
        }
        canvas.endLines();
        shapeData.clear();
        shapeColors.clear();
    }

    /** Engine hook: draws the metrics panel in the top-left corner of the window. */
    public void renderPanel(DebugCanvas canvas, int screenWidth, int screenHeight) {
        if (!visible) {
            return;
        }
        screen.setToOrtho2D(0, 0, screenWidth, screenHeight);
        float lineHeight = canvas.lineHeight();
        int lines = 4;
        float width = 300f;
        float height = lines * lineHeight + 2 * PADDING;
        canvas.panel(0f, screenHeight - height, width, height, PANEL_BACKGROUND, screen);
        canvas.beginText(screen);
        float y = screenHeight - PADDING;
        canvas.text(line1(), PADDING, y, TEXT);
        canvas.text(line2(), PADDING, y - lineHeight, TEXT);
        canvas.text(line3(), PADDING, y - 2 * lineHeight, TEXT);
        canvas.text(line4(), PADDING, y - 3 * lineHeight, TEXT);
        canvas.endText();
    }

    /** The panel's text lines (tests and dev tools). */
    public String[] panelLines() {
        return new String[] {line1().toString(), line2().toString(), line3().toString(), line4().toString()};
    }

    private CharSequence line1() {
        text.setLength(0);
        text.append("FPS ").append(fps).append("   frame ");
        appendMs(frameAverageMs);
        text.append(" ms (max ");
        appendMs(frameMaxMs);
        return text.append(')');
    }

    private CharSequence line2() {
        text.setLength(0);
        text.append("engine ");
        appendMs(engineAverageMs);
        text.append(" ms (max ");
        appendMs(engineMaxMs);
        return text.append(')');
    }

    private CharSequence line3() {
        text.setLength(0);
        return text.append("fixed steps ").append(stepsLastFrame).append("   dropped ").append(droppedSteps);
    }

    private CharSequence line4() {
        text.setLength(0);
        return text.append("entities ").append(entities).append("   render calls ").append(renderCalls);
    }

    /** Appends a millisecond value with two decimals, without String.format. */
    private void appendMs(float ms) {
        int hundredths = Math.round(ms * 100f);
        text.append(hundredths / 100).append('.');
        int fraction = hundredths % 100;
        if (fraction < 10) {
            text.append('0');
        }
        text.append(fraction);
    }

    private void addShape(int kind, float a, float b, float c, float d, Color color) {
        shapeData.add(kind, a, b, c);
        shapeData.add(d);
        shapeColors.add(color);
    }

    private void resetMetrics() {
        windowFrames = 0;
        windowSeconds = 0f;
        windowFrameSum = 0f;
        windowFrameMax = 0f;
        windowEngineSum = 0d;
        windowEngineMax = 0d;
        lastFixedTick = -1;
    }
}
