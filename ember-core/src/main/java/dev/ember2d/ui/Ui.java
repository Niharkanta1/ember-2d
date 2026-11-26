package dev.ember2d.ui;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.Input;
import java.util.Objects;

/**
 * A scene's UI (F-012): a Scene2D {@link Stage} with a root {@link Table} filling the UI area, drawn above the world.
 * Get it with {@code Scene.ui()}; the engine creates it on first use, lays it out, acts it on real time, draws it,
 * routes pointer input to it before the game, and disposes it when the scene exits.
 */
@PublicApi
public final class Ui implements Disposable {

    private final UiViewport viewport = new UiViewport();
    private final Stage stage;
    private final Table root = new Table();
    private final Input input;
    private final Array<MenuList> menus = new Array<>(true, 4);
    private Skin skin;
    private boolean ownsSkin;
    private BitmapFont defaultFont;
    private Insets safeInsets = Insets.NONE;
    private float pixelsPerDp = 1f;
    private float pixelsPerUnit = 1f;

    /** Engine and tests: a UI drawing with this batch (shared, not owned) and reading menus from this input. */
    public Ui(Batch batch, Input input) {
        this.stage = new Stage(viewport, Objects.requireNonNull(batch, "batch"));
        this.input = Objects.requireNonNull(input, "input");
        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        stage.addActor(root);
    }

    public Stage stage() {
        return stage;
    }

    /** A table filling the UI area; add widgets here (or add actors to the stage directly). */
    public Table root() {
        return root;
    }

    /** The input the menus read. */
    public Input input() {
        return input;
    }

    /** The skin; by default a pixel skin over libGDX's built-in font, created on first use. */
    public Skin skin() {
        if (skin == null) {
            defaultFont = new BitmapFont();
            skin = UiSkins.pixel(defaultFont);
            ownsSkin = true;
        }
        return skin;
    }

    /** Uses a skin; with {@code disposeWithUi} it is disposed when the scene exits. */
    public void setSkin(Skin skin, boolean disposeWithUi) {
        disposeSkin();
        this.skin = Objects.requireNonNull(skin, "skin");
        this.ownsSkin = disposeWithUi;
    }

    /** Uses a skin owned by someone else (for example an asset). */
    public void setSkin(Skin skin) {
        setSkin(skin, false);
    }

    /** Width of the UI area in UI units (virtual pixels in {@link UiMode#VIRTUAL}). */
    public float width() {
        return viewport.getWorldWidth();
    }

    public float height() {
        return viewport.getWorldHeight();
    }

    /** The safe-area insets in UI units (notches, rounded corners; F-022, I-225); the root table is padded by them. */
    public Insets safeInsets() {
        return safeInsets;
    }

    /** Converts density-independent pixels to UI units (for touch-friendly sizes). */
    public float dp(float dp) {
        return dp * pixelsPerDp / pixelsPerUnit;
    }

    /** The menu that reads input now: the most recently shown one still on the stage. */
    public MenuList activeMenu() {
        return menus.isEmpty() ? null : menus.peek();
    }

    // ---- engine hooks ----

    /** Engine: the UI area in UI units and its screen rectangle in back-buffer pixels. */
    public void layout(float width, float height, int screenX, int screenY, int screenWidth, int screenHeight) {
        if (viewport.set(width, height, screenX, screenY, screenWidth, screenHeight)) {
            root.invalidateHierarchy();
        }
    }

    /**
     * Engine: the safe-area insets in UI units, and how many window pixels one dp and one UI unit are.
     */
    public void setScreenMetrics(Insets insets, float pixelsPerDp, float pixelsPerUnit) {
        this.pixelsPerDp = pixelsPerDp > 0f ? pixelsPerDp : 1f;
        this.pixelsPerUnit = pixelsPerUnit > 0f ? pixelsPerUnit : 1f;
        if (!insets.equals(safeInsets)) {
            safeInsets = insets;
            root.pad(insets.top(), insets.left(), insets.bottom(), insets.right());
            root.invalidateHierarchy();
        }
    }

    /** Engine: advances the UI by the real frame delta. */
    public void act(float realDelta) {
        stage.act(realDelta);
    }

    /** Engine: draws the UI (the batch must not be drawing). */
    public void draw() {
        viewport.apply();
        stage.draw();
    }

    /** Engine: receives pointer and key events before the game. */
    public InputProcessor inputProcessor() {
        return stage;
    }

    void menuShown(MenuList menu) {
        menus.removeValue(menu, true);
        menus.add(menu);
    }

    void menuHidden(MenuList menu) {
        menus.removeValue(menu, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        disposeSkin();
    }

    private void disposeSkin() {
        if (skin != null && ownsSkin) {
            skin.dispose();
        }
        if (defaultFont != null) {
            defaultFont.dispose();
            defaultFont = null;
        }
        skin = null;
    }
}
