package dev.ember2d.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.Input;
import dev.ember2d.input.TouchLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lets the player arrange the on-screen controls (F-022, I-225): drag a control to move it, tap to select it, and use
 * Size −/+, Opacity −/+, Reset and Done. The rest of the UI is hidden and the controls are inert while editing.
 * Changes apply at once through {@code Input.setTouchLayout}; saving them is the settings store's job (I-230).
 */
@PublicApi
public final class TouchLayoutEditor {

    static final float SIZE_STEP = 0.1f;
    static final float OPACITY_STEP = 0.1f;

    private final Ui ui;
    private final Input input;
    private final Runnable onDone;
    private final Table overlay;
    private final Label selectedLabel;
    private final Vector3 circle = new Vector3();
    private final Vector2 point = new Vector2();
    private final TouchLayout original;
    private final List<Actor> hidden = new ArrayList<>();
    private String selected;
    private String dragging;

    private TouchLayoutEditor(Ui ui, Input input, Runnable onDone) {
        this.ui = ui;
        this.input = input;
        this.onDone = onDone;
        this.original = input.touchLayout();
        Skin skin = ui.skin();
        overlay = new Table(skin);
        overlay.setFillParent(true);
        overlay.setTouchable(Touchable.enabled);
        overlay.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                dragging = controlAt(event.getStageX(), event.getStageY());
                if (dragging != null) {
                    select(dragging);
                }
                return dragging != null;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (dragging != null) {
                    toWindow(event.getStageX(), event.getStageY(), point);
                    input.moveTouchControl(dragging, point.x, point.y);
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                dragging = null;
            }
        });
        overlay.addActor(new Frame());

        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(6f);
        selectedLabel = new Label("Drag a control", skin, "dim");
        panel.add(selectedLabel).colspan(3).padBottom(4f).row();
        panel.add(button("Size -", () -> changeScale(-SIZE_STEP))).pad(1f);
        panel.add(button("Size +", () -> changeScale(SIZE_STEP))).pad(1f);
        panel.add(button("Reset", this::reset)).pad(1f).row();
        panel.add(button("Opacity -", () -> changeOpacity(-OPACITY_STEP))).pad(1f);
        panel.add(button("Opacity +", () -> changeOpacity(OPACITY_STEP))).pad(1f);
        MenuList done = new MenuList(ui);
        done.add("Done", this::close);
        done.onBack(this::close);
        panel.add(done).pad(1f);
        overlay.add(panel).expand().top().padTop(4f);
    }

    /** Opens the editor above the UI; {@code onDone} runs when the player closes it. */
    public static Table open(Ui ui, Input input, Runnable onDone) {
        TouchLayoutEditor editor = new TouchLayoutEditor(Objects.requireNonNull(ui, "ui"),
                Objects.requireNonNull(input, "input"), Objects.requireNonNull(onDone, "onDone"));
        input.setTouchControlsEditing(true);
        for (Actor actor : ui.stage().getActors()) {
            if (actor.isVisible()) {
                editor.hidden.add(actor);   // the editor takes over the UI so the controls can be seen
                actor.setVisible(false);
            }
        }
        ui.stage().addActor(editor.overlay);
        return editor.overlay;
    }

    private TextButton button(String text, Runnable action) {
        TextButton b = new TextButton(text, ui.skin());
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }
        });
        return b;
    }

    private void select(String control) {
        selected = control;
        selectedLabel.setText(control);
    }

    private void changeScale(float delta) {
        if (selected == null) {
            return;
        }
        TouchLayout layout = input.touchLayout();
        layout.scale(selected, layout.scaleOf(selected) + delta);
        input.setTouchLayout(layout);
    }

    private void changeOpacity(float delta) {
        TouchLayout layout = input.touchLayout();
        layout.setOpacity(layout.opacity(0.35f) + delta);
        input.setTouchLayout(layout);
    }

    private void reset() {
        input.setTouchLayout(TouchLayout.defaults());
    }

    private void close() {
        input.setTouchControlsEditing(false);
        overlay.remove();
        for (Actor actor : hidden) {
            actor.setVisible(true);
        }
        hidden.clear();
        onDone.run();
    }

    /** The control under a stage point, or null. */
    String controlAt(float stageX, float stageY) {
        toWindow(stageX, stageY, point);
        String best = null;
        float bestDistance = Float.MAX_VALUE;
        for (String name : input.touchControlNames()) {
            if (!input.touchControlCircle(name, circle)) {
                continue;
            }
            float dx = point.x - circle.x;
            float dy = point.y - circle.y;
            float d = dx * dx + dy * dy;
            if (d <= circle.z * circle.z * 1.2f && d < bestDistance) {
                best = name;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Stage coordinates to window pixels with y up (the controls' coordinates). */
    private void toWindow(float stageX, float stageY, Vector2 out) {
        out.set(stageX, stageY);
        ui.stage().stageToScreenCoordinates(out);   // y down from the window's top
        out.y = windowHeight() - out.y;
    }

    private static float windowHeight() {
        return Gdx.graphics == null ? 0f : Gdx.graphics.getHeight();
    }

    /** Draws a frame around the selected control. */
    private final class Frame extends Actor {
        private final Vector2 a = new Vector2();
        private final Vector2 b = new Vector2();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (selected == null || !input.touchControlCircle(selected, circle)) {
                return;
            }
            Drawable white = ui.skin().getDrawable("white");
            a.set(circle.x - circle.z, windowHeight() - (circle.y + circle.z));   // to y down, then to the stage
            ui.stage().screenToStageCoordinates(a);
            b.set(circle.x + circle.z, windowHeight() - (circle.y - circle.z));
            ui.stage().screenToStageCoordinates(b);
            float x = Math.min(a.x, b.x);
            float y = Math.min(a.y, b.y);
            float w = Math.abs(b.x - a.x);
            float h = Math.abs(b.y - a.y);
            Color old = batch.getColor().cpy();
            batch.setColor(1f, 0.78f, 0.25f, parentAlpha);
            white.draw(batch, x, y, w, 1f);
            white.draw(batch, x, y + h - 1f, w, 1f);
            white.draw(batch, x, y, 1f, h);
            white.draw(batch, x + w - 1f, y, 1f, h);
            batch.setColor(old);
        }
    }

    /** The layout the editor started from (Reset goes to the defaults, not here). */
    TouchLayout original() {
        return original;
    }
}
