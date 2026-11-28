package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.audio.Audio;
import dev.ember2d.audio.AudioCategory;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.TouchLayout;
import dev.ember2d.save.SaveData;
import dev.ember2d.save.internal.FileSaveStore;
import dev.ember2d.save.internal.MemorySaveFiles;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The engine's own settings (F-013, I-230): applied at boot, captured by saveSettings, bad values skipped. */
class EngineSettingsTest {

    static final String BINDINGS = """
            { "version": 1,
              "contexts": { "gameplay": { "actions": { "jump": ["SPACE"], "dash": ["X"] },
                                          "axes": { "move": { "left": ["A"], "right": ["D"], "up": ["W"], "down": ["S"] } } } },
              "touchControls": { "buttons": [ { "action": "jump", "label": "A", "x": 72, "y": 72, "radius": 40 } ] } }
            """;

    final Map<AudioCategory, Float> volumes = new EnumMap<>(AudioCategory.class);
    final Audio audio = (Audio) Proxy.newProxyInstance(Audio.class.getClassLoader(), new Class<?>[] {Audio.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setVolume" -> volumes.put((AudioCategory) args[0], (Float) args[1]);
                case "volume" -> volumes.getOrDefault((AudioCategory) args[0], 1f);
                default -> method.getReturnType() == boolean.class ? false
                        : method.getReturnType() == float.class ? 0f
                        : method.getReturnType() == long.class ? 0L : null;
            });
    final EngineConfig config = new EngineConfig();
    final FileSaveStore store = new FileSaveStore(new MemorySaveFiles(), 1, 1, () -> 0L);

    EngineContext context() {
        return new EngineContext.Builder(config).audio(audio).saves(store)
                .inputBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "input.json")).build();
    }

    @Test
    void storedSettingsAreAppliedAndBadOnesSkipped() {
        TouchLayout layout = TouchLayout.defaults().move("jump", 100f, 50f);
        SaveData s = store.settings();
        s.set("audio.music", 0.3f).set("audio.sfx", 7f).set("input.haptics", false)
                .set("input.touchLayout", layout.toJson()).set("ui.scale", 2f);
        s.child("input.bindings").set("jump", "UP, SPACE").set("fly", "Q").set("dash", "NOT_A_KEY");

        EngineContext ctx = context();
        EngineSettings.apply(ctx);
        assertThat(volumes).containsExactly(Map.entry(AudioCategory.MUSIC, 0.3f));
        assertThat(ctx.input().hapticsEnabled()).isFalse();
        assertThat(ctx.input().touchLayout().toJson()).isEqualTo(layout.toJson());
        assertThat(ctx.input().actionInputs("jump")).containsExactly("UP", "SPACE");
        assertThat(ctx.input().actionInputs("dash")).as("a bad override is skipped").containsExactly("X");
        assertThat(config.uiScale).isEqualTo(2f);

        s.set("input.touchLayout", "{oops").set("ui.scale", -1f);
        EngineSettings.apply(context());   // logged and skipped, never thrown
    }

    @Test
    void saveSettingsCapturesTheCurrentValues() {
        EngineContext ctx = context();
        volumes.put(AudioCategory.SFX, 0.5f);
        ctx.input().setHapticsEnabled(false);
        ctx.input().rebindAction("jump", List.of("W"));
        assertThat(ctx.saveSettings().ok()).isTrue();

        SaveData s = store.settings();
        assertThat(s.getFloat("audio.sfx", 0f)).isEqualTo(0.5f);
        assertThat(s.getFloat("audio.master", 0f)).isEqualTo(1f);
        assertThat(s.getBool("input.haptics", true)).isFalse();
        assertThat(s.has("input.touchLayout")).as("default layout: not stored").isFalse();
        assertThat(s.child("input.bindings").getString("jump", "")).isEqualTo("W");

        ctx.input().resetBinding("jump");
        ctx.input().setTouchLayout(TouchLayout.defaults().scale("jump", 1.5f));
        ctx.saveSettings();
        assertThat(s.has("input.bindings")).isFalse();
        assertThat(TouchLayout.parse(s.getString("input.touchLayout", "")).scaleOf("jump")).isEqualTo(1.5f);
    }

    @Test
    void axisOverridesAreSavedAndLoaded() {
        EngineContext ctx = context();
        ctx.input().rebindAxis("move", "left", List.of("J", "PAD_LEFT"));
        assertThat(ctx.saveSettings().ok()).isTrue();
        assertThat(store.settings().child("input.bindings").getString("move:left", "")).isEqualTo("J,PAD_LEFT");

        EngineContext again = context();
        EngineSettings.apply(again);
        assertThat(again.input().axisInputs("move", "left")).containsExactly("J", "PAD_LEFT");
        assertThat(again.input().axisInputs("move", "right")).containsExactly("D");
    }
}
