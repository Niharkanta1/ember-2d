package dev.ember2d.demo.rpg;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.audio.Audio;
import dev.ember2d.input.Input;
import dev.ember2d.kit.common.DialogueRunner;
import dev.ember2d.ui.DialogueBox;
import dev.ember2d.ui.Ui;
import java.util.List;
import java.util.Map;

/**
 * Plays one knot of the town story in a {@link DialogueBox} at the bottom of the screen: lines one by one, then the
 * choices, until the knot ends. The UI input context is on top while it runs, so the knight does not act.
 */
final class Conversation {

    private static final Map<String, Color> NAME_COLORS = Map.of("Grik", Color.valueOf("FFD36B"),
            "Slime", Color.valueOf("8BE38B"), "Knight", Color.valueOf("9FD3FF"));

    private final Input input;
    private final DialogueRunner story;
    private final Audio audio;
    private final Table overlay;
    private final DialogueBox box;
    private Runnable onEnd;
    private boolean active;

    Conversation(Ui ui, Input input, DialogueRunner story, Audio audio) {
        this.input = input;
        this.story = story;
        this.audio = audio;
        box = new DialogueBox(ui);
        box.setTextSpeed(45f);
        box.setOnAdvance(this::showNext);
        overlay = new Table();
        overlay.setFillParent(true);
        overlay.add(box).expand().bottom().growX().minHeight(58f).pad(4f, 6f, 6f, 6f);
        overlay.setVisible(false);
        ui.stage().addActor(overlay);
    }

    boolean active() {
        return active;
    }

    /** Runs the knot; {@code onEnd} runs when it is over. */
    void start(String knot, Runnable onEnd) {
        this.onEnd = onEnd;
        active = true;
        input.pushContext("ui");
        overlay.setVisible(true);
        overlay.toFront();
        story.jumpTo(knot);
        showNext();
    }

    private void showNext() {
        while (story.canContinue()) {
            story.next();
            String text = story.text();
            if (text.isBlank()) {
                continue;
            }
            String speaker = story.speaker();
            box.show(speaker, NAME_COLORS.get(speaker), text);
            audio.play("sfx.blip");
            return;
        }
        List<String> choices = story.choices();
        if (!choices.isEmpty()) {
            box.showChoices(choices, index -> {
                story.choose(index);
                showNext();
            });
            return;
        }
        end();
    }

    private void end() {
        active = false;
        overlay.setVisible(false);
        input.popContext();
        if (onEnd != null) {
            onEnd.run();
        }
    }
}
