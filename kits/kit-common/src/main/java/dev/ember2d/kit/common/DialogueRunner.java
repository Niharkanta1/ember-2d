package dev.ember2d.kit.common;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.bladecoder.ink.runtime.Choice;
import com.bladecoder.ink.runtime.Story;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.PublicApi;
import dev.ember2d.save.SaveData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs an Ink story (F-021, ADR-017) through blade-ink, compiled by inklecate into a {@code .ink.json} asset.
 * <pre>{@code
 * DialogueRunner talk = DialogueRunner.fromAsset(ctx.assets(), "ink.town");
 * talk.bind("give", args -> { inventory.add(items, (String) args[0], 1); return null; });
 * talk.jumpTo("elder");
 * while (talk.canContinue()) { box.show(talk.speaker(), talk.next()); }
 * List<String> options = talk.choices();
 * }</pre>
 * An {@code EXTERNAL} function that is not bound runs its Ink fallback, as in Inky.
 * The speaker comes from a {@code speaker:name} tag, or a {@code Name: text} prefix; {@link #text()} is the line
 * without that prefix. No Ink type is part of this API.
 */
@PublicApi
public final class DialogueRunner {

    /** "Name: text": a name of 1 to 24 letters, digits and spaces, then a colon and a space. */
    private static final Pattern PREFIX = Pattern.compile("^([\\p{L}\\p{N} ]{1,24}): (.*)$", Pattern.DOTALL);
    private static final String STATE_KEY = "ink";

    private final Story story;
    private final String name;
    private String line = "";
    private List<String> tags = List.of();

    private DialogueRunner(Story story, String name) {
        this.story = story;
        this.name = name;
    }

    /**
     * A story from compiled Ink JSON (a leading byte-order mark, as some editors add, is stripped).
     *
     * @throws IllegalArgumentException when the JSON is not a compiled Ink story
     */
    public static DialogueRunner fromJson(String json, String name) {
        Objects.requireNonNull(json, "json");
        String text = json.startsWith("﻿") ? json.substring(1) : json;
        try {
            Story story = new Story(text);
            // An EXTERNAL function not bound (yet) uses its Ink fallback, as in Inky (blade-ink refuses by default).
            story.setAllowExternalFunctionFallbacks(true);
            return new DialogueRunner(story, name);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ink story " + name + " cannot be loaded: " + e.getMessage(), e);
        }
    }

    /** A story from a {@code json} asset (an inklecate {@code .ink.json} file). */
    public static DialogueRunner fromAsset(Assets assets, String id) {
        JsonValue json = assets.get(id, JsonValue.class);
        return fromJson(json.toJson(JsonWriter.OutputType.json), assets.path(id));
    }

    // ---- lines ----

    public boolean canContinue() {
        return story.canContinue();
    }

    /**
     * Advances one line and returns it (without the line break).
     *
     * @throws IllegalStateException when the story cannot continue, or on an Ink runtime error
     */
    public String next() {
        if (!story.canContinue()) {
            throw new IllegalStateException("Ink story " + name + " cannot continue (choices or the end)");
        }
        try {
            String raw = story.Continue();
            line = raw.endsWith("\n") ? raw.substring(0, raw.length() - 1) : raw;
            tags = List.copyOf(story.getCurrentTags());
        } catch (Exception e) {
            throw error(e);
        }
        if (story.hasError()) {
            throw new IllegalStateException("Ink error in " + name + ": " + story.getCurrentErrors());
        }
        return line;
    }

    /** The current line as written (with any "Name: " prefix). */
    public String line() {
        return line;
    }

    /** The current line without its "Name: " prefix. */
    public String text() {
        Matcher m = PREFIX.matcher(line);
        return m.matches() ? m.group(2) : line;
    }

    /** The speaker: the {@code speaker:} tag, else the "Name: " prefix, else empty. */
    public String speaker() {
        String tagged = tag("speaker");
        if (tagged != null) {
            return tagged;
        }
        Matcher m = PREFIX.matcher(line);
        return m.matches() ? m.group(1) : "";
    }

    /** The current line's tags, as written ({@code mood:stern}). */
    public List<String> tags() {
        return tags;
    }

    /** The value of a {@code key:value} tag on the current line, or null. */
    public String tag(String key) {
        String prefix = key + ":";
        for (String t : tags) {
            if (t.startsWith(prefix)) {
                return t.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    // ---- choices ----

    /** The current choice texts (empty while the story can continue or has ended). */
    public List<String> choices() {
        List<Choice> current = story.getCurrentChoices();
        if (current.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(current.size());
        for (Choice c : current) {
            out.add(c.getText());
        }
        return out;
    }

    /**
     * Picks a choice.
     *
     * @throws IndexOutOfBoundsException for an index that is not a current choice
     */
    public void choose(int index) {
        int count = story.getCurrentChoices().size();
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("choice " + index + " (the story offers " + count + ")");
        }
        try {
            story.chooseChoiceIndex(index);
        } catch (Exception e) {
            throw error(e);
        }
    }

    /** True when the story can neither continue nor offer choices. */
    public boolean isFinished() {
        return !story.canContinue() && story.getCurrentChoices().isEmpty();
    }

    /**
     * Continues at a knot or stitch ({@code elder}, {@code elder.after_quest}).
     *
     * @throws IllegalArgumentException for an unknown path
     */
    public void jumpTo(String path) {
        try {
            story.choosePathString(path);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ink story " + name + " has no knot or stitch \"" + path + "\": "
                    + e.getMessage(), e);
        }
    }

    // ---- variables and functions ----

    /**
     * An Ink global variable (Integer, Float, Boolean or String).
     *
     * @throws IllegalArgumentException for an unknown variable
     */
    public Object variable(String variable) {
        Object value = story.getVariablesState().get(variable);
        if (value == null) {
            throw new IllegalArgumentException("Ink story " + name + " has no variable \"" + variable + "\"");
        }
        return value;
    }

    /** Sets an Ink global variable (int, float, boolean or String). */
    public void setVariable(String variable, Object value) {
        variable(variable);   // unknown: IllegalArgumentException
        try {
            story.getVariablesState().set(variable, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ink variable \"" + variable + "\" cannot be set to " + value + ": "
                    + e.getMessage(), e);
        }
    }

    /** Runs game code for an Ink {@code EXTERNAL} function; it replaces the Ink fallback. */
    public void bind(String function, DialogueFunction body) {
        Objects.requireNonNull(body, "body");
        try {
            story.bindExternalFunction(function, args -> body.call(args));
        } catch (Exception e) {
            throw new IllegalArgumentException("Ink function \"" + function + "\" cannot be bound: " + e.getMessage(),
                    e);
        }
    }

    // ---- saving ----

    /** The whole story state (position, variables, visit counts) as JSON. */
    public String saveState() {
        try {
            return story.getState().toJson();
        } catch (Exception e) {
            throw error(e);
        }
    }

    /** Restores a state from {@link #saveState()}. */
    public void loadState(String json) {
        try {
            story.getState().loadJson(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Saved state of Ink story " + name + " cannot be loaded: "
                    + e.getMessage(), e);
        }
        line = "";
        tags = List.of();
    }

    /** Stores the state in a save tree (key {@code ink}). */
    public void save(SaveData out) {
        out.set(STATE_KEY, saveState());
    }

    /** Restores the state stored by {@link #save}; does nothing when there is none. */
    public void load(SaveData in) {
        String json = in.getString(STATE_KEY, "");
        if (!json.isEmpty()) {
            loadState(json);
        }
    }

    private IllegalStateException error(Exception e) {
        return new IllegalStateException("Ink error in " + name + ": " + e.getMessage(), e);
    }
}
