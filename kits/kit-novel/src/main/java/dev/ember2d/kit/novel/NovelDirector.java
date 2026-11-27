package dev.ember2d.kit.novel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Colors;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.kit.common.DialogueRunner;
import dev.ember2d.save.SaveData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Runs a story as a staged scene (F-023): advances the {@link DialogueRunner}, runs each line's staging tags in order
 * (updating the {@link StageState} and calling the {@link StageListener}), skips lines that only stage, and hands back
 * lines, choices or the end. Keeps a backlog of the last 100 lines and records read lines and CGs in the
 * {@link NovelMemory}. In dev builds a bad tag fails, naming the story and the line; in release builds it is logged
 * and skipped.
 */
@PublicApi
public final class NovelDirector {

    /** Lines kept in the backlog. */
    public static final int BACKLOG = 100;
    private static final Log LOG = Log.get("novel");

    /** What {@link #next()} found. */
    public enum Kind { LINE, CHOICES, END }

    /** One step of the story: a line, choices, or the end. */
    public record Step(Kind kind, NovelLine line, List<String> choices) {
    }

    /** A line in the backlog. */
    public record Logged(String name, String text, String voice) {
    }

    private final DialogueRunner runner;
    private final NovelCharacters characters;
    private final StageListener listener;
    private final NovelMemory memory;
    private final boolean devMode;
    private final StageState stage = new StageState();
    private final ArrayDeque<Logged> backlog = new ArrayDeque<>();

    public NovelDirector(DialogueRunner runner, NovelCharacters characters, StageListener listener, NovelMemory memory,
            boolean devMode) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.listener = listener == null ? new StageListener() { } : listener;
        this.memory = memory == null ? NovelMemory.none() : memory;
        this.devMode = devMode;
    }

    public DialogueRunner runner() {
        return runner;
    }

    public StageState stage() {
        return stage;
    }

    public NovelCharacters characters() {
        return characters;
    }

    /** The next line, the choices, or the end; lines that only stage are run and skipped. */
    public Step next() {
        float delay = 0f;
        while (runner.canContinue()) {
            runner.next();
            String voice = null;
            List<String> gameTags = new ArrayList<>();
            for (String tag : runner.tags()) {
                NovelTag command;
                try {
                    command = NovelTag.parse(tag);
                } catch (IllegalArgumentException e) {
                    problem(e.getMessage());
                    continue;
                }
                if (command == null) {
                    gameTags.add(tag);
                    continue;
                }
                try {
                    switch (command.name) {
                        case "wait" -> delay += NovelTag.parseNumber("wait", required(command, 0));
                        case "voice" -> {
                            voice = required(command, 0);
                            listener.voice(voice);
                        }
                        default -> run(command);
                    }
                } catch (IllegalArgumentException e) {
                    problem("#" + tag + ": " + e.getMessage());
                }
            }
            String text = runner.text();
            if (text.isBlank()) {
                continue;   // a line that only stages
            }
            String speaker = runner.speaker();
            NovelCharacters.Character character = characters.forSpeaker(speaker);
            String name = character != null ? character.name() : speaker;
            Color color = character != null ? character.color() : Color.WHITE;
            boolean read = memory.isRead(speaker, text);
            memory.markRead(speaker, text);
            if (backlog.size() == BACKLOG) {
                backlog.removeFirst();
            }
            backlog.addLast(new Logged(name, text, voice));
            return new Step(Kind.LINE, new NovelLine(character == null ? null : character.id(), name, color, text,
                    voice, Collections.unmodifiableList(gameTags), read, delay), List.of());
        }
        List<String> choices = runner.choices();
        return choices.isEmpty() ? new Step(Kind.END, null, List.of()) : new Step(Kind.CHOICES, null, choices);
    }

    /** Picks a choice; then call {@link #next()}. */
    public void choose(int index) {
        runner.choose(index);
    }

    /** The last {@value #BACKLOG} lines, oldest first. */
    public List<Logged> backlog() {
        return List.copyOf(backlog);
    }

    /** Saves the story position (key {@code ink}) and the stage (key {@code stage}). */
    public void save(SaveData out) {
        runner.save(out);
        SaveData s = new SaveData();
        stage.save(s);
        out.set("stage", s);
    }

    /** Restores {@link #save}d data and redraws the whole stage at once ({@link StageListener#restore}). */
    public void load(SaveData in) {
        runner.load(in);
        stage.load(in.has("stage") ? in.child("stage") : new SaveData());
        listener.restore(stage, characters);
    }

    // ---- staging ----

    private void run(NovelTag t) {
        switch (t.name) {
            case "bg" -> {
                String id = required(t, 0);
                stage.setBackground(id);
                listener.background(id, transition(t, "fade"), t.number("time", 0.5f));
            }
            case "show" -> show(t);
            case "expr" -> {
                NovelCharacters.Character c = character(required(t, 0));
                StageState.Shown shown = stage.characters().get(c.id());
                if (shown == null) {
                    throw new IllegalArgumentException(c.id() + " is not on stage (use show:)");
                }
                String expr = expression(c, required(t, 1));
                stage.show(c.id(), expr, shown.position());
                listener.show(c.id(), c.expressions().get(expr), characters.position(shown.position()), "cut", 0f);
            }
            case "hide" -> {
                NovelCharacters.Character c = character(required(t, 0));
                stage.hide(c.id());
                listener.hide(c.id(), transition(t, "fade"), t.number("time", 0.3f));
            }
            case "music" -> {
                String id = required(t, 0);
                stage.setMusic(id);
                listener.music(id, t.number("fade", 1f));
            }
            case "stopmusic" -> {
                stage.setMusic(null);
                listener.stopMusic(t.number("fade", 1f));
            }
            case "sfx" -> listener.sound(required(t, 0));
            case "cg" -> {
                String id = required(t, 0);
                stage.setCg(id);
                memory.unlockCg(id);
                listener.cg(id);
            }
            case "hidecg" -> {
                stage.setCg(null);
                listener.cg(null);
            }
            case "shake" -> listener.shake(t.arg(0) == null ? 0.4f : NovelTag.parseNumber("shake", t.arg(0)));
            case "flash" -> flash(t);
            default -> throw new IllegalArgumentException("unknown staging tag " + t.name);
        }
    }

    private void show(NovelTag t) {
        NovelCharacters.Character c = character(required(t, 0));
        StageState.Shown shown = stage.characters().get(c.id());
        String expr = shown != null ? shown.expression() : c.defaultExpression();
        String position = shown != null ? shown.position() : c.position();
        for (int i = 1; i < t.args.size(); i++) {
            String word = t.args.get(i);
            if (c.expressions().containsKey(word)) {
                expr = word;
            } else if (characters.position(word) != null) {
                position = word;
            } else {
                throw new IllegalArgumentException("\"" + word + "\" is neither an expression of " + c.id() + " "
                        + c.expressions().keySet() + " nor a position");
            }
        }
        stage.show(c.id(), expr, position);
        listener.show(c.id(), expr == null ? null : c.expressions().get(expr), characters.position(position),
                transition(t, "fade"), t.number("time", 0.3f));
    }

    private void flash(NovelTag t) {
        Color color = new Color(Color.WHITE);
        float seconds = 0.2f;
        for (String word : t.args) {
            if (Character.isDigit(word.charAt(0)) || word.startsWith(".")) {
                seconds = NovelTag.parseNumber("flash", word);
            } else if (word.startsWith("#")) {
                color = Color.valueOf(word.substring(1));
            } else {
                Color named = Colors.get(word.toUpperCase(Locale.ROOT));
                if (named == null) {
                    throw new IllegalArgumentException("unknown colour \"" + word + "\"");
                }
                color = new Color(named);
            }
        }
        listener.flash(color, seconds);
    }

    private NovelCharacters.Character character(String id) {
        NovelCharacters.Character c = characters.get(id);
        if (c == null) {
            throw new IllegalArgumentException("unknown character \"" + id + "\" (characters: "
                    + characters.all().keySet() + ")");
        }
        return c;
    }

    private static String expression(NovelCharacters.Character c, String expr) {
        if (!c.expressions().containsKey(expr)) {
            throw new IllegalArgumentException("unknown expression \"" + expr + "\" of " + c.id() + " (expressions: "
                    + c.expressions().keySet() + ")");
        }
        return expr;
    }

    private static String transition(NovelTag t, String fallback) {
        String tr = t.options.getOrDefault("transition", fallback);
        if (!tr.equals("fade") && !tr.equals("slide") && !tr.equals("cut")) {
            throw new IllegalArgumentException("unknown transition \"" + tr + "\" (fade, slide, cut)");
        }
        return tr;
    }

    private static String required(NovelTag t, int index) {
        String v = t.arg(index);
        if (v == null) {
            throw new IllegalArgumentException(t.name + ": needs " + (index + 1) + " value(s)");
        }
        return v;
    }

    private void problem(String message) {
        String where = "Story " + runner.name() + ", line \"" + runner.line() + "\": " + message;
        if (devMode) {
            throw new IllegalStateException(where);
        }
        LOG.warn(where + " (skipped)");
    }
}
