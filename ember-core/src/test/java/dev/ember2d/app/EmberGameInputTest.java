package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.core.LogConfig;
import dev.ember2d.input.InputBindingsException;
import dev.ember2d.input.internal.DefaultInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Input wiring in {@link EmberGame} (F-005): bindings loaded at boot, processor, back key, suspend. */
class EmberGameInputTest {

    private static final FileHandleResolver RESOLVER = path -> Gdx.files.internal("assets-test/" + path);
    private static final String MANIFEST = """
            { "version": 1, "assets": [
              { "id": "input.main", "path": "input/test-input.json", "type": "json", "group": "global" },
              { "id": "input.bad", "path": "input/bad-input.json", "type": "json", "group": "global" },
              { "id": "not.json", "path": "input/test-input.json", "type": "sound", "group": "extra" } ] }
            """;

    private final List<String> inputCalls = new ArrayList<>();

    @BeforeEach
    void gdx() {
        HeadlessGdx.install();
        Gdx.gl = null;   // no renderer: these tests are about input only
        Gdx.gl20 = null;
        Gdx.input = HeadlessGdx.recorder(Input.class, (name, args) -> {
            if (name.equals("setInputProcessor") || name.equals("setCatchKey")) {
                inputCalls.add(name + (args.length == 2 ? " " + args[0] + "=" + args[1]
                        : args[0] == null ? " null" : " set"));
            }
        });
    }

    @AfterEach
    void clear() {
        Gdx.input = null;
        HeadlessGdx.clear();
        LogConfig.resetToDefaults();
    }

    private static EmberGame game(String bindingsId) {
        EngineConfig config = new EngineConfig();
        config.inputBindings = bindingsId;
        return new EmberGame(config, c -> new GdxAssets(AssetManifest.parse(MANIFEST, "manifest.json"), RESOLVER));
    }

    @Test
    void loadsBindingsAtBootAndInstallsTheProcessor() {
        EmberGame game = game("input.main");
        game.create();

        assertThat(game.context().input().hasAction("jump")).isTrue();
        assertThat(game.context().input().hasAction("move")).isTrue();
        assertThat(inputCalls).containsExactly("setInputProcessor set", "setCatchKey " + Keys.BACK + "=true");

        game.dispose();
        assertThat(inputCalls).last().isEqualTo("setInputProcessor null");
    }

    @Test
    void withoutBindingsTheBackKeyIsNotCaught() {
        EmberGame game = game(null);
        game.create();

        assertThat(game.context().input().hasAction("jump")).isFalse();
        assertThat(inputCalls).contains("setCatchKey " + Keys.BACK + "=false");
    }

    @Test
    void invalidBindingsFailBootWithTheFileAndPath() {
        InputBindingsException e = catchThrowableOfType(InputBindingsException.class, () -> game("input.bad").create());

        assertThat(e.problems()).containsExactly(
                "input/bad-input.json: contexts.gameplay.actions.jump[0]: unknown input name \"SPCE\"");
    }

    @Test
    void unknownOrNonJsonBindingsIdFailsBoot() {
        assertThat(catchThrowableOfType(EngineConfigException.class, () -> game("input.missing").create()))
                .hasMessageContaining("inputBindings: asset ID \"input.missing\" is not in the asset manifest");
        assertThat(catchThrowableOfType(EngineConfigException.class, () -> game("not.json").create()))
                .hasMessageContaining("must be of type json");
    }

    @Test
    void suspendReleasesHeldInputs() {
        EmberGame game = game("input.main");
        game.create();
        DefaultInput input = (DefaultInput) game.context().input();
        input.source().keyDown(Keys.SPACE);
        input.beginFrame();
        assertThat(input.pressed("jump")).isTrue();

        game.pause();
        game.resume();
        input.beginFrame();

        assertThat(input.pressed("jump")).isFalse();
        assertThat(input.justReleased("jump")).isTrue();
    }
}
