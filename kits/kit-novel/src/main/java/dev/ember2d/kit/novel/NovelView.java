package dev.ember2d.kit.novel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.camera.CameraRig;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.Input;
import dev.ember2d.ui.DialogueBox;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The standard novel presentation (F-023) on a scene's {@link Ui}: background, characters at their anchors, CG, a
 * dialogue box with name plate and typewriter text, choices, and the reader buttons Auto, Skip, Log and Hide. Drive it
 * with {@link #play} and call {@link #update} every frame. Without a {@code bg:} the background stays transparent, so
 * a story scene can run over a game's world (an RPG cutscene).
 */
@PublicApi
public final class NovelView implements StageListener {

    private static final float SKIP_STEP = 0.06f;

    private final Ui ui;
    private final Assets assets;
    private final Audio audio;
    private final Input input;
    private final CameraRig camera;
    private final NovelSettings settings;
    private final Group root = new Group();
    private final Image background = new Image();
    private final Group people = new Group();
    private final Image cg = new Image();
    private final Image flash = new Image();
    private final DialogueBox box;
    private final Table buttons;
    private final Table choices;
    private final Map<String, Image> shown = new LinkedHashMap<>();
    private NovelDirector director;
    private Runnable onFinished;
    private float wait;
    private float autoWait;
    private boolean lineShown;
    private boolean auto;
    private boolean skip;
    private boolean hidden;
    private boolean read;
    private boolean finished = true;
    private Table backlog;
    private NovelLine pending;
    private float characterHeight = 0.85f;
    private float characterBottom;
    private Consumer<NovelLine> onLine;

    /**
     * @param camera shakes for {@code shake:}; may be null
     */
    public NovelView(Ui ui, Assets assets, Audio audio, Input input, CameraRig camera, NovelSettings settings) {
        this.ui = ui;
        this.assets = assets;
        this.audio = audio;
        this.input = input;
        this.camera = camera;
        this.settings = settings;
        float w = ui.width();
        float h = ui.height();
        root.setSize(w, h);
        background.setSize(w, h);
        background.setScaling(Scaling.fill);
        cg.setSize(w, h);
        cg.setScaling(Scaling.fit);
        cg.setVisible(false);
        flash.setSize(w, h);
        flash.setDrawable(ui.skin().getDrawable("white"));
        flash.setColor(1f, 1f, 1f, 0f);
        flash.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        Actor catcher = new Actor();   // a tap anywhere advances
        catcher.setSize(w, h);
        catcher.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tap();
            }
        });
        box = new DialogueBox(ui);
        box.setConfirmActions("ui_confirm", "fire");
        box.setOnAdvance(() -> {
            if (backlog == null && lineShown) {
                auto = false;
                advance();
            }
        });
        box.setSize(w - 16f, h * 0.3f);
        box.setPosition(8f, 6f);
        buttons = new Table(ui.skin());
        buttons.add(button("Auto", () -> {
            auto = !auto;
            skip = false;
        })).padRight(2f);
        buttons.add(button("Skip", () -> {
            skip = !skip;
            auto = false;
        })).padRight(2f);
        buttons.add(button("Log", this::openBacklog)).padRight(2f);
        buttons.add(button("Hide", () -> setHidden(true)));
        buttons.pack();
        buttons.setPosition(w - buttons.getWidth() - 6f, h - buttons.getHeight() - 4f);
        choices = new Table(ui.skin());
        choices.setFillParent(true);
        root.addActor(background);
        root.addActor(people);
        root.addActor(cg);
        root.addActor(catcher);
        root.addActor(box);
        root.addActor(buttons);
        root.addActor(choices);
        root.addActor(flash);
        box.setVisible(false);
    }

    private TextButton button(String label, Runnable action) {
        TextButton b = new TextButton(label, ui.skin());
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return b;
    }

    /** Called with each line as it appears (its game tags, its speaker); null for none. */
    public void setOnLine(Consumer<NovelLine> listener) {
        onLine = listener;
    }

    /**
     * Where characters stand, as fractions of the screen height: their feet at {@code bottom} and their height
     * (default 0 and 0.85, a full-screen novel). A story scene over a game's world suits smaller characters standing
     * on the dialogue box, such as (0.33, 0.5). Applies to characters shown from now on.
     */
    public void setCharacterArea(float bottom, float height) {
        if (height <= 0f || bottom < 0f || bottom + height > 1f) {
            throw new IllegalArgumentException("character area from " + bottom + " with height " + height
                    + " must lie within the screen (0 to 1)");
        }
        characterBottom = bottom;
        characterHeight = height;
    }

    /**
     * Starts presenting a director's story; {@code onFinished} runs at its end (the view is removed). After
     * {@link NovelDirector#load}, it starts with the line that was on screen when the story was saved.
     */
    public void play(NovelDirector director, Runnable onFinished) {
        this.director = director;
        this.onFinished = onFinished;
        finished = false;
        if (root.getStage() == null) {
            ui.stage().addActor(root);
        }
        NovelLine resume = director.resumeLine();   // a loaded story shows its saved line again
        if (resume != null) {
            pending = resume;
            read = true;
            showLine();
            return;
        }
        advance();
    }

    /** Every frame (real time): typewriter, waits, auto and skip modes, keyboard advance. */
    public void update(float delta) {
        if (finished || backlog != null) {
            return;
        }
        if (wait > 0f) {
            wait -= delta;
            if (wait <= 0f) {
                showLine();
            }
            return;
        }
        if (!lineShown) {
            return;
        }
        if (hidden && (pressed("ui_confirm") || pressed("fire"))) {
            setHidden(false);
            return;
        }
        if (skip && !read && !settings.skipUnread) {
            skip = false;   // skip mode never passes unread text, also the line on screen when it was turned on
        }
        if (skip) {
            box.complete();
        }
        if (box.isComplete()) {
            if (skip) {
                autoWait += delta;
                if (autoWait >= SKIP_STEP) {
                    advance();
                }
            } else if (auto) {
                autoWait += delta;
                if (autoWait >= settings.autoDelay) {
                    advance();
                }
            }
        }
    }

    private boolean pressed(String action) {
        return input != null && input.hasAction(action) && input.justPressed(action);
    }

    /** Whether a story is being presented. */
    public boolean isPlaying() {
        return !finished;
    }

    private void tap() {
        if (finished || !lineShown) {
            return;
        }
        if (hidden) {
            setHidden(false);
            return;
        }
        box.press();   // completes the line first, then advances
    }

    private void advance() {
        lineShown = false;
        autoWait = 0f;
        NovelDirector.Step step = director.next();
        switch (step.kind()) {
            case LINE -> {
                NovelLine line = step.line();
                read = line.read();
                if (skip && !read && !settings.skipUnread) {
                    skip = false;   // skip stops at unread text
                }
                pending = line;
                wait = skip ? 0f : line.delay();
                box.setVisible(false);
                if (wait <= 0f) {
                    showLine();
                }
            }
            case CHOICES -> showChoices(step.choices());
            case END -> finish();
        }
    }

    private void showLine() {
        wait = 0f;
        box.setTextSpeed(settings.textSpeed);
        box.show(pending.name(), pending.color(), pending.text());
        if (skip) {
            box.complete();
        }
        box.setVisible(!hidden);
        lineShown = true;
        if (onLine != null) {
            onLine.accept(pending);
        }
    }

    private void showChoices(List<String> options) {
        skip = false;
        auto = false;
        box.setVisible(false);
        choices.clearChildren();
        MenuList menu = new MenuList(ui);
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            menu.add(options.get(i), () -> {
                choices.clearChildren();
                director.choose(index);
                advance();
            });
        }
        choices.add(menu).minWidth(ui.width() * 0.5f);
    }

    private void finish() {
        finished = true;
        box.setVisible(false);
        root.remove();
        if (onFinished != null) {
            onFinished.run();
        }
    }

    private void setHidden(boolean hide) {
        hidden = hide;
        box.setVisible(!hide && lineShown);
        buttons.setVisible(!hide);
    }

    private void openBacklog() {
        backlog = new Table(ui.skin());
        backlog.setFillParent(true);
        backlog.setBackground("panel");
        Table lines = new Table(ui.skin());
        for (NovelDirector.Logged l : director.backlog()) {
            Label line = new Label((l.name().isEmpty() ? "" : l.name() + ": ") + l.text(), ui.skin());
            line.setWrap(true);
            lines.add(line).width(ui.width() - 60f).left().pad(1f);
            if (l.voice() != null) {
                String voice = l.voice();
                lines.add(button(">", () -> voice(voice))).padLeft(2f);
            }
            lines.row();
        }
        ScrollPane scroll = new ScrollPane(lines, ui.skin());
        scroll.layout();
        scroll.setScrollPercentY(1f);
        backlog.add(scroll).grow().pad(6f).row();
        backlog.add(button("Close", () -> {
            backlog.remove();
            backlog = null;
        })).pad(4f);
        root.addActor(backlog);
    }

    // ---- StageListener ----

    @Override
    public void background(String region, String transition, float seconds) {
        background.setDrawable(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(assets.region(region)));
        if (transition.equals("fade") && seconds > 0f) {
            background.getColor().a = 0f;
            background.addAction(Actions.fadeIn(seconds));
        } else {
            background.getColor().a = 1f;
        }
    }

    @Override
    public void show(String character, String region, float x, String transition, float seconds) {
        Image image = shown.get(character);
        boolean fresh = image == null;
        if (fresh) {
            image = new Image();
            shown.put(character, image);
            people.addActor(image);
        }
        if (region != null) {
            TextureRegion r = assets.region(region);
            image.setDrawable(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(r));
            float h = ui.height() * characterHeight;
            float w = h * r.getRegionWidth() / r.getRegionHeight();
            image.setSize(w, h);
        }
        float targetX = x * ui.width() - image.getWidth() / 2f;
        image.clearActions();
        if (transition.equals("slide") && seconds > 0f) {
            if (fresh) {
                image.setPosition(x < 0.5f ? -image.getWidth() : ui.width(), characterBottom * ui.height());
            }
            image.getColor().a = 1f;
            image.addAction(Actions.moveTo(targetX, characterBottom * ui.height(), seconds));
        } else {
            image.setPosition(targetX, characterBottom * ui.height());
            if (fresh && transition.equals("fade") && seconds > 0f) {
                image.getColor().a = 0f;
                image.addAction(Actions.fadeIn(seconds));
            } else {
                image.getColor().a = 1f;
            }
        }
    }

    @Override
    public void hide(String character, String transition, float seconds) {
        Image image = shown.remove(character);
        if (image == null) {
            return;
        }
        image.clearActions();
        if (transition.equals("fade") && seconds > 0f) {
            image.addAction(Actions.sequence(Actions.fadeOut(seconds), Actions.removeActor()));
        } else {
            image.remove();
        }
    }

    @Override
    public void music(String id, float fadeSeconds) {
        if (audio != null) {
            audio.playMusic(id, fadeSeconds);
        }
    }

    @Override
    public void stopMusic(float fadeSeconds) {
        if (audio != null) {
            audio.stopMusic(fadeSeconds);
        }
    }

    @Override
    public void sound(String id) {
        if (audio != null) {
            audio.play(id);
        }
    }

    @Override
    public void voice(String id) {
        if (audio == null) {
            return;
        }
        String asset = assets.contains(id) ? id : "voice." + id;
        if (assets.contains(asset)) {
            audio.play(asset);
        }
    }

    @Override
    public void cg(String region) {
        if (region == null) {
            cg.setVisible(false);
            return;
        }
        cg.setDrawable(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(assets.region(region)));
        cg.setVisible(true);
        cg.getColor().a = 0f;
        cg.addAction(Actions.fadeIn(0.4f));
    }

    @Override
    public void shake(float strength) {
        if (camera != null) {
            camera.addTrauma(strength);
        } else {
            root.addAction(Actions.sequence(Actions.moveBy(4f * strength, 0f, 0.05f),
                    Actions.moveBy(-8f * strength, 0f, 0.05f), Actions.moveBy(4f * strength, 0f, 0.05f)));
        }
    }

    @Override
    public void flash(Color color, float seconds) {
        flash.clearActions();
        flash.setColor(color.r, color.g, color.b, 1f);
        flash.addAction(Actions.fadeOut(Math.max(0.01f, seconds)));
    }

    @Override
    public void restore(StageState stage, NovelCharacters characters) {
        for (Image image : shown.values()) {
            image.remove();
        }
        shown.clear();
        if (stage.background() != null) {
            background(stage.background(), "cut", 0f);
        } else {
            background.setDrawable(null);
        }
        for (Map.Entry<String, StageState.Shown> e : stage.characters().entrySet()) {
            NovelCharacters.Character c = characters.get(e.getKey());
            String region = c == null || e.getValue().expression() == null ? null
                    : c.expressions().get(e.getValue().expression());
            Float x = characters.position(e.getValue().position());
            show(e.getKey(), region, x == null ? 0.5f : x, "cut", 0f);
        }
        cg(stage.cg());
        if (stage.music() != null) {
            music(stage.music(), 0f);
        }
    }
}
