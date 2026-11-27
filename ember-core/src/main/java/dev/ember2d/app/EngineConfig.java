package dev.ember2d.app;

import com.badlogic.gdx.graphics.Color;
import dev.ember2d.core.PublicApi;
import dev.ember2d.render.RenderMode;
import dev.ember2d.data.ComponentSetup;
import dev.ember2d.assets.AssetWatcher;
import dev.ember2d.ui.Insets;
import dev.ember2d.ui.UiMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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

    /** How the virtual resolution is fitted to the window (F-004). */
    public RenderMode renderMode = RenderMode.PIXEL_PERFECT;

    /** Colour behind the world (inside the letterbox; the bars are black). */
    public final Color backgroundColor = new Color(Color.BLACK);

    /** Allows the debug overlay (F3) in release builds; it is always available in dev mode (F-014). */
    public boolean debugOverlayInRelease = false;

    /** Shows the debug overlay from the start (useful on phones, which have no F3 key). */
    public boolean debugOverlayVisibleAtStart = false;

    /** The scene started at boot (F-010); null starts no scene. */
    public Supplier<Scene> firstScene;

    /** Asset manifest path relative to the assets root (F-008). */
    public String assetManifest = "manifest.json";

    /**
     * Asset ID of the input bindings file (F-005): a {@code json} entry of the manifest, normally in group
     * {@code global}. {@code null} means no bindings (every action query fails).
     */
    public String inputBindings = null;

    /**
     * Path of the game's Tiled project file ({@code .tiled-project}) in the assets, passed to every map load so class
     * properties resolve (F-009). {@code null} if the maps use no class properties.
     */
    public String tiledProject = null;

    /**
     * The game's component registrations (F-010), run once at boot; the build runs the same setup to generate R8 keep
     * rules. {@code null} registers only the engine components ({@code Transform}, {@code Sprite}).
     */
    public ComponentSetup components = null;

    /** How the UI is laid out (F-012): in virtual pixels like the world (default), or in screen pixels. */
    public UiMode uiMode = UiMode.VIRTUAL;

    /**
     * {@link UiMode#SCREEN} only: screen pixels per UI unit (2 makes everything twice as big); 0 (default) picks it
     * from the screen density (at least 1).
     */
    public float uiScale = 0f;

    /**
     * Dev builds only (ignored unless {@link #devMode}): fake safe-area insets in window pixels, to test notch layouts
     * on desktop (I-225). {@code null} uses the device's insets.
     */
    public Insets simulatedSafeInsets = null;

    /**
     * Asset ID of a {@code skin} whose drawables ({@code touch-base}, {@code touch-knob}, {@code touch-button},
     * {@code touch-button-down}, {@code touch-dpad}, {@code touch-dpad-down}) replace the on-screen controls' default
     * look (I-225); {@code null} uses the generated look.
     */
    public String touchControlsSkin = null;

    /**
     * The folder name saves and settings go in (F-013, I-230): a plain name such as {@code "my-game"}. Desktop:
     * {@code ~/.ember2d/<name>}; Android: the app's private {@code saves} folder. {@code null} keeps saves in memory
     * only (nothing survives a restart).
     */
    public String saveFolder = null;

    /** The number of save slots (1 to 99). */
    public int saveSlots = 3;

    /** The current save version; older saves are upgraded by the store's migrations (F-013). */
    public int saveVersion = 1;

    /**
     * Drawn while a scene transition waits for the incoming scene's asset group (F-010, I-240); {@code null} shows only
     * the transition's cover colour.
     */
    public LoadingScreen loadingScreen = LoadingScreen.bar();

    /** When the on-screen controls of the bindings file are shown (F-022, I-122). */
    public TouchControlsMode touchControls = TouchControlsMode.AUTO;

    /**
     * Hot reload (F-008, dev builds only; ignored unless {@link #devMode}): an absolute folder the assets are read
     * from instead of the internal assets, normally the game's source assets folder. {@code null} reads the internal
     * assets.
     */
    public String assetsDir = null;

    /**
     * Hot reload (F-008, dev builds only; ignored unless {@link #devMode}): reports changed files in
     * {@link #assetsDir}, for example {@code new DirectoryWatcher(path)} from {@code ember-devtools}. The engine
     * closes it at shutdown. {@code null} turns hot reload off.
     */
    public AssetWatcher assetWatcher = null;

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
        if (renderMode == null) {
            problems.add("renderMode must be set (was null)");
        }
        if (assetManifest == null || assetManifest.isBlank()) {
            problems.add("assetManifest must be a non-empty path (was " + quote(assetManifest) + ")");
        }
        if (touchControls == null) {
            problems.add("touchControls must be set (was null)");
        }
        if (tiledProject != null && tiledProject.isBlank()) {
            problems.add("tiledProject must be null or a path (was " + quote(tiledProject) + ")");
        }
        if (uiMode == null) {
            problems.add("uiMode must be set (was null)");
        }
        if (!(uiScale >= 0f) || Float.isInfinite(uiScale)) {
            problems.add("uiScale must be 0 (from density) or a finite number > 0 (was " + uiScale + ")");
        }
        if (assetsDir != null && assetsDir.isBlank()) {
            problems.add("assetsDir must be null or an absolute folder (was " + quote(assetsDir) + ")");
        }
        if (devMode && assetWatcher != null && assetsDir == null) {
            problems.add("assetWatcher needs assetsDir: the folder it watches must also be the one assets are read from");
        }
        if (saveFolder != null && !saveFolder.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            problems.add("saveFolder must be null or a plain folder name of letters, digits, '.', '_' and '-' (was "
                    + quote(saveFolder) + ")");
        }
        if (saveSlots < 1 || saveSlots > 99) {
            problems.add("saveSlots must be between 1 and 99 (was " + saveSlots + ")");
        }
        if (saveVersion < 1) {
            problems.add("saveVersion must be >= 1 (was " + saveVersion + ")");
        }
        if (inputBindings != null && inputBindings.isBlank()) {
            problems.add("inputBindings must be null or an asset ID (was " + quote(inputBindings) + ")");
        }
        if (!problems.isEmpty()) {
            throw new EngineConfigException(problems);
        }
    }

    @Override
    public String toString() {
        return "EngineConfig[virtual=" + virtualWidth + "x" + virtualHeight + ", pixelsPerUnit=" + pixelsPerUnit
                + ", fixedStep=" + fixedStep + ", maxCatchUpSteps=" + maxCatchUpSteps + ", renderMode=" + renderMode + ", devMode=" + devMode
                + ", assetManifest=" + quote(assetManifest) + ", inputBindings=" + quote(inputBindings)
                + ", tiledProject=" + quote(tiledProject) + "]";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
