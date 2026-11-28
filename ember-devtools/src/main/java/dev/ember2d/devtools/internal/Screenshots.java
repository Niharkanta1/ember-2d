package dev.ember2d.devtools.internal;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import dev.ember2d.core.Log;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.zip.Deflater;

/**
 * Screenshots for dev runs (desktop only, never in a release): F12 saves the frame as a PNG, and the system properties
 * {@code ember.screenshotDir} and {@code ember.screenshotAt} (seconds after start, comma-separated, for example
 * {@code "3,6.5"}) take timed shots, for checks without anyone at the screen. The frame is read right after the game
 * draws, before the dev tools' panels, so they never appear in a shot. Files: {@code shot-<time>.png} in the folder
 * (default: {@code logs/} next to the game).
 */
final class Screenshots {

    private static final Log LOG = Log.get("devtools");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final FileHandle dir;
    private final float[] at;
    private int next;
    private float elapsed;

    Screenshots(String dir, String at) {
        this.dir = dir == null || dir.isBlank() ? Gdx.files.local("logs") : Gdx.files.absolute(dir);
        this.at = parse(at);
    }

    static float[] parse(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String[] parts = text.split(",");
        float[] out = new float[parts.length];
        int n = 0;
        for (String p : parts) {
            try {
                out[n++] = Float.parseFloat(p.trim());
            } catch (NumberFormatException e) {
                LOG.warn("ember.screenshotAt: not a number: \"" + p.trim() + "\"");
                n--;
            }
        }
        float[] times = Arrays.copyOf(out, n);
        Arrays.sort(times);
        return times;
    }

    /** Call right after the game has drawn the frame. */
    void afterFrame(float delta) {
        elapsed += delta;
        boolean timed = next < at.length && elapsed >= at[next];
        if (timed) {
            next++;
        }
        if (timed || Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
            save();
        }
    }

    private void save() {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
        try {
            FileHandle file = dir.child("shot-" + LocalDateTime.now().format(STAMP) + ".png");
            PixmapIO.writePNG(file, pixmap, Deflater.DEFAULT_COMPRESSION, true);
            LOG.info("Screenshot " + file.path());
        } catch (RuntimeException e) {
            LOG.warn("Screenshot failed: " + e.getMessage());
        } finally {
            pixmap.dispose();
        }
    }
}
