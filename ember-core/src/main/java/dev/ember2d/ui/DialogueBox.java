package dev.ember2d.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.Input;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A dialogue box (F-012): a name plate, text that appears letter by letter, and a choice list. A press (a confirm
 * action, or a click or tap on the box) first completes the text, then calls the advance handler; a confirm action
 * in the frame a line appears is ignored. Used by RPG
 * conversations and by kit-novel.
 *
 * <pre>{@code
 * DialogueBox box = new DialogueBox(ui);
 * box.setOnAdvance(() -> showNext());
 * box.show("Elder", Color.GOLD, "Welcome, traveller.");
 * box.showChoices(List.of("Yes", "No"), index -> { ... });
 * }</pre>
 */
@PublicApi
public final class DialogueBox extends Table {

    private final Ui ui;
    private final Label name;
    private final Label text;
    private final Table body = new Table();
    private String full = "";
    private float shown;
    private float textSpeed = 40f;
    private String[] confirmActions = {"ui_confirm"};
    private Runnable onAdvance;
    private MenuList choices;
    private boolean fresh;

    public DialogueBox(Ui ui) {
        super(ui.skin());
        this.ui = ui;
        setBackground("panel");
        pad(4f, 8f, 6f, 8f);
        name = new Label("", ui.skin(), "title");
        text = new Label("", ui.skin());
        text.setWrap(true);
        text.setAlignment(Align.topLeft);
        add(name).left().row();
        add(body).grow();
        body.add(text).grow().top().left();
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (choices == null) {
                    press();
                }
            }
        });
    }

    /**
     * Shows a line; the text appears at the {@link #setTextSpeed text speed}.
     *
     * @param speaker the name plate; null or empty hides it
     * @param color the name plate's colour; null for white
     */
    public DialogueBox show(String speaker, Color color, String line) {
        clearChoices();
        full = line == null ? "" : line;
        boolean named = speaker != null && !speaker.isEmpty();
        name.setText(named ? speaker : "");
        name.setVisible(named);
        name.setColor(color == null ? Color.WHITE : color);
        shown = textSpeed <= 0f ? full.length() : 0f;
        text.setText(full.substring(0, (int) shown));
        fresh = true;
        return this;
    }

    /** Characters per second; 0 or less shows lines at once. */
    public void setTextSpeed(float charactersPerSecond) {
        textSpeed = charactersPerSecond;
    }

    public float textSpeed() {
        return textSpeed;
    }

    /** The input actions that press the box (default {@code ui_confirm}); none: only clicks and {@link #press()}. */
    public void setConfirmActions(String... actions) {
        confirmActions = actions.clone();
    }

    /** Called when the box is pressed with the text complete and no choices shown. */
    public void setOnAdvance(Runnable action) {
        onAdvance = action;
    }

    /** The whole line. */
    public String text() {
        return full;
    }

    /** The text shown so far. */
    public String shownText() {
        return text.getText().toString();
    }

    /** Whether the whole line is shown. */
    public boolean isComplete() {
        return (int) shown >= full.length();
    }

    /** Shows the whole line at once. */
    public void complete() {
        shown = full.length();
        text.setText(full);
    }

    /** Completes the text, or (if complete and without choices) calls the advance handler. */
    public void press() {
        if (!isComplete()) {
            complete();
        } else if (choices == null && onAdvance != null) {
            onAdvance.run();
        }
    }

    /**
     * Shows choices in the box, below the completed line; {@code onChoose} gets the chosen index. The list takes the
     * keyboard focus ({@code ui_nav} and {@code ui_confirm}, see {@link MenuList}).
     */
    public MenuList showChoices(List<String> options, IntConsumer onChoose) {
        Objects.requireNonNull(onChoose, "onChoose");
        complete();
        clearChoices();
        choices = new MenuList(ui);
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            choices.add(options.get(i), () -> {
                clearChoices();
                onChoose.accept(index);
            });
        }
        body.row();
        body.add(choices).left().padTop(2f);
        choices.focus(0);
        return choices;
    }

    /** Whether choices are shown. */
    public boolean hasChoices() {
        return choices != null;
    }

    private void clearChoices() {
        if (choices != null) {
            choices.remove();
            choices = null;
            body.clearChildren();
            body.add(text).grow().top().left();
        }
    }

    @Override
    public void act(float delta) {
        // Input first: a choice picked with ui_confirm this frame must not also complete the next line. A press in
        // the frame a line appears (the press that started a conversation) does not count either.
        Input input = ui.input();
        boolean ignore = fresh;
        fresh = false;
        if (!ignore && isVisible() && choices == null && input != null && getStage() != null) {
            for (String action : confirmActions) {
                if (input.hasAction(action) && input.justPressed(action)) {
                    press();
                    break;
                }
            }
        }
        if (!isComplete()) {
            shown = Math.min(full.length(), shown + textSpeed * delta);
            text.setText(full.substring(0, (int) shown));
        }
        super.act(delta);
    }
}
