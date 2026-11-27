package dev.ember2d.kit.novel;

import dev.ember2d.core.PublicApi;
import dev.ember2d.save.SaveData;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What is on the novel stage (F-023): the background, the music, the visible characters with their expression and
 * position, and a full-screen CG. Updated by {@link NovelDirector}; saved with the story.
 */
@PublicApi
public final class StageState {

    /** A visible character. */
    public record Shown(String expression, String position) {
    }

    private String background;
    private String music;
    private String cg;
    private final Map<String, Shown> characters = new LinkedHashMap<>();

    /** The background region ID, or null. */
    public String background() {
        return background;
    }

    /** The music asset ID, or null. */
    public String music() {
        return music;
    }

    /** The CG region ID on screen, or null. */
    public String cg() {
        return cg;
    }

    /** Visible characters in the order they were shown (drawn in that order). */
    public Map<String, Shown> characters() {
        return Collections.unmodifiableMap(characters);
    }

    void setBackground(String id) {
        background = id;
    }

    void setMusic(String id) {
        music = id;
    }

    void setCg(String id) {
        cg = id;
    }

    /** Shows a character, or changes one already shown (which keeps its place in the drawing order). */
    void show(String character, String expression, String position) {
        characters.put(character, new Shown(expression, position));
    }

    void hide(String character) {
        characters.remove(character);
    }

    /** Writes the stage into {@code out}. */
    public void save(SaveData out) {
        out.clear();
        if (background != null) {
            out.set("background", background);
        }
        if (music != null) {
            out.set("music", music);
        }
        if (cg != null) {
            out.set("cg", cg);
        }
        for (Map.Entry<String, Shown> e : characters.entrySet()) {
            out.list("characters").add(new SaveData().set("id", e.getKey()).set("expression", e.getValue().expression())
                    .set("position", e.getValue().position()));
        }
    }

    /** Replaces the stage with saved data. */
    public void load(SaveData in) {
        background = in.getString("background", null);
        music = in.getString("music", null);
        cg = in.getString("cg", null);
        characters.clear();
        if (in.has("characters")) {
            for (SaveData c : in.list("characters")) {
                characters.put(c.getString("id", ""), new Shown(c.getString("expression", null),
                        c.getString("position", "center")));
            }
        }
    }
}
