package dev.ember2d.app;

import dev.ember2d.audio.Audio;
import dev.ember2d.audio.AudioCategory;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.Input;
import dev.ember2d.input.TouchLayout;
import dev.ember2d.save.SaveData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings the engine itself keeps in {@code ctx.saves().settings()} (F-013, I-230). They are applied at boot and
 * captured by {@link EngineContext#saveSettings()}, on suspend and at shutdown. A bad stored value is logged and
 * skipped. Games keep their own settings under other keys.
 */
@PublicApi
public final class EngineSettings {

    /** Volume 0-1 per category: {@code audio.master}, {@code audio.music}, {@code audio.sfx}. */
    public static final String AUDIO_PREFIX = "audio.";
    /** Vibration on or off (boolean). */
    public static final String HAPTICS = "input.haptics";
    /** The on-screen controls layout ({@link TouchLayout#toJson()}). */
    public static final String TOUCH_LAYOUT = "input.touchLayout";
    /** Rebound actions: a tree of action name to comma-separated input names. */
    public static final String BINDINGS = "input.bindings";
    /** {@code EngineConfig.uiScale}, read at boot only (float, 0 = from density). */
    public static final String UI_SCALE = "ui.scale";

    private static final Log LOG = Log.get("save");

    private EngineSettings() {
    }

    static String volumeKey(AudioCategory category) {
        return AUDIO_PREFIX + category.name().toLowerCase(Locale.ROOT);
    }

    /** Applies stored settings (boot, after the bindings load and before the first scene). */
    static void apply(EngineContext context) {
        SaveData s = context.saves().settings();
        EngineConfig config = context.config();
        if (s.has(UI_SCALE)) {
            float scale = s.getFloat(UI_SCALE, -1f);
            if (scale >= 0f && scale <= 16f) {
                config.uiScale = scale;
            } else {
                skip(UI_SCALE, "must be from 0 to 16");
            }
        }
        Audio audio = findAudio(context);
        if (audio != null) {
            for (AudioCategory category : AudioCategory.values()) {
                String key = volumeKey(category);
                if (s.has(key)) {
                    float v = s.getFloat(key, -1f);
                    if (v >= 0f && v <= 1f) {
                        audio.setVolume(category, v);
                    } else {
                        skip(key, "must be a number from 0 to 1");
                    }
                }
            }
        }
        Input input = context.input();
        if (s.has(HAPTICS)) {
            input.setHapticsEnabled(s.getBool(HAPTICS, true));
        }
        if (s.has(TOUCH_LAYOUT)) {
            try {
                input.setTouchLayout(TouchLayout.parse(s.getString(TOUCH_LAYOUT, "")));
            } catch (RuntimeException e) {
                skip(TOUCH_LAYOUT, e.getMessage());
            }
        }
        if (s.has(BINDINGS)) {
            SaveData bindings;
            try {
                bindings = s.child(BINDINGS);
            } catch (IllegalStateException e) {
                skip(BINDINGS, "must be an object");
                return;
            }
            for (String action : bindings.keys()) {
                List<String> inputs = new ArrayList<>();
                for (String name : bindings.getString(action, "").split(",")) {
                    if (!name.trim().isEmpty()) {
                        inputs.add(name.trim());
                    }
                }
                try {
                    input.rebindAction(action, inputs);
                } catch (IllegalArgumentException e) {
                    skip(BINDINGS + "." + action, e.getMessage());
                }
            }
        }
    }

    /** Copies the engine's current settings into the store's settings tree (does not write). */
    static void capture(EngineContext context) {
        SaveData s = context.saves().settings();
        Audio audio = findAudio(context);
        if (audio != null) {
            for (AudioCategory category : AudioCategory.values()) {
                s.set(volumeKey(category), audio.volume(category));
            }
        }
        Input input = context.input();
        s.set(HAPTICS, input.hapticsEnabled());
        TouchLayout layout = input.touchLayout();
        if (layout.toJson().equals(TouchLayout.defaults().toJson())) {
            s.remove(TOUCH_LAYOUT);
        } else {
            s.set(TOUCH_LAYOUT, layout.toJson());
        }
        Map<String, List<String>> overrides = input.bindingOverrides();
        if (overrides.isEmpty()) {
            s.remove(BINDINGS);
        } else {
            SaveData bindings = new SaveData();
            overrides.forEach((action, inputs) -> bindings.set(action, String.join(",", inputs)));
            s.set(BINDINGS, bindings);
        }
    }

    private static Audio findAudio(EngineContext context) {
        try {
            return context.audio();
        } catch (IllegalStateException e) {
            return null;   // a context without audio (tests, headless tools)
        }
    }

    private static void skip(String key, String reason) {
        LOG.warn("Ignoring setting " + key + ": " + reason);
    }
}
