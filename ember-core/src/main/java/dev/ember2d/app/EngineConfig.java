package dev.ember2d.app;

import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.List;

/**
 * Startup configuration of an {@link EmberGame}. Set the fields before passing the config to the game; it is
 * validated and read once in {@code create()}, so changes after that have no effect.
 *
 * <p>Fields are consumed by the systems that need them as those arrive in Phase 1: fixed step and catch-up by
 * the game loop (F-002), virtual resolution and pixels per unit by the renderer (F-004), the asset manifest by
 * the asset service (F-008). The first scene is added with the scene manager (F-010).
 */
@PublicApi
public final class EngineConfig {

    /** Longest fixed step accepted; frame deltas are clamped to the same value (ARCHITECTURE.md, runtime loop). */
    public static final float MAX_FIXED_STEP = 0.25f;

    /** Largest number of fixed steps run in one frame to catch up. */
    public static final int MAX_CATCH_UP_LIMIT = 60;

    /** Width of the pixel-perfect world render target, in virtual pixels. */
    public int virtualWidth = 320;

    /** Height of the pixel-perfect world render target, in virtual pixels. */
    public int virtualHeight = 180;

    /** Virtual pixels per world unit. */
    public float pixelsPerUnit = 16f;

    /** Fixed simulation step in seconds. */
    public float fixedStep = 1f / 60f;

    /** Maximum fixed steps per frame before the accumulator is dropped. */
    public int maxCatchUpSteps = 5;

    /** Enables dev features: debug logging, boot timing, hot reload and dev tools hooks (later phases). */
    public boolean devMode = false;

    /** Asset manifest path inside the game's assets. */
    public String assetManifest = "assets/manifest.json";

    /**
     * Checks every field and throws one exception listing all problems.
     *
     * @throws EngineConfigException if any field is invalid
     */
    public void validate() {
        List<String> problems = new ArrayList<>();
        if (virtualWidth <= 0) {
            problems.add("virtualWidth must be > 0 (was " + virtualWidth + ")");
        }
        if (virtualHeight <= 0) {
            problems.add("virtualHeight must be > 0 (was " + virtualHeight + ")");
        }
        if (!(pixelsPerUnit > 0f) || Float.isInfinite(pixelsPerUnit)) {
            problems.add("pixelsPerUnit must be a finite number > 0 (was " + pixelsPerUnit + ")");
        }
        if (!(fixedStep > 0f && fixedStep <= MAX_FIXED_STEP)) {
            problems.add("fixedStep must be > 0 and <= " + MAX_FIXED_STEP + " seconds (was " + fixedStep + ")");
        }
        if (maxCatchUpSteps < 1 || maxCatchUpSteps > MAX_CATCH_UP_LIMIT) {
            problems.add("maxCatchUpSteps must be between 1 and " + MAX_CATCH_UP_LIMIT
                    + " (was " + maxCatchUpSteps + ")");
        }
        if (assetManifest == null || assetManifest.isBlank()) {
            problems.add("assetManifest must be a non-empty path (was " + quote(assetManifest) + ")");
        }
        if (!problems.isEmpty()) {
            throw new EngineConfigException(problems);
        }
    }

    @Override
    public String toString() {
        return "EngineConfig[virtual=" + virtualWidth + "x" + virtualHeight + ", pixelsPerUnit=" + pixelsPerUnit
                + ", fixedStep=" + fixedStep + ", maxCatchUpSteps=" + maxCatchUpSteps + ", devMode=" + devMode
                + ", assetManifest=" + quote(assetManifest) + "]";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
